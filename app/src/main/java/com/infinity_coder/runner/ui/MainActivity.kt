package com.infinity_coder.runner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.infinity_coder.runner.R
import com.infinity_coder.runner.ui.common.dialogs.OnPermissionRationaleListener
import com.infinity_coder.runner.ui.common.dialogs.PermissionRationaleDialog
import com.infinity_coder.runner.utilities.extensions.checkPermission
import com.infinity_coder.runner.utilities.extensions.toast
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.*


internal class MainActivity : AppCompatActivity(), OnPermissionRationaleListener {

    companion object {
        private const val POLYGON_HEIGHT = 0.001
        private const val POLYGON_WIDTH = 0.001
    }
    private lateinit var map: GoogleMap
    private lateinit var locationProvider: FusedLocationProviderClient

    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var calculateRectanglesJob: Job? = null
    val handler = CoroutineExceptionHandler { _, exception ->
        println("CoroutineExceptionHandler got $exception")
    }
    private var polygons: HashMap<PolygonParams, Polygon> = hashMapOf()

    private val unSelectedPolygonFillColor = Color.TRANSPARENT
    private val unSelectedPolygonStrokeColor = Color.parseColor("#B1BEC5")

    private val selectedPolygonFillColor = Color.parseColor("#0FFE612C")
    private val selectedPolygonStrokeColor = Color.parseColor("#FE612C")

    var lastLocation: Location? = null

    private val images: MutableList<BitmapDescriptor> = ArrayList()
    private var groundOverlay: GroundOverlay? = null
    private var myLocationMarker: Marker? = null

    private val polygonCenterLocation = LatLng(55.6388593, 37.6704059)

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            determineLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            onMapReady(googleMap)
        }
        locationProvider = LocationServices.getFusedLocationProviderClient(this)
        permissionsButton.setOnClickListener {
            determineLocationIfHasPermissions()
        }
    }

    private fun buildLocationRequest(): LocationRequest {
        return LocationRequest.create().apply {
            interval = 100
            fastestInterval = 50
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime = 100
        }
    }

    private fun buildLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val currentLocation = locationResult.lastLocation
                val myLocationLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                if (myLocationMarker == null) {
                    myLocationMarker = createLocationMarker(myLocationLatLng)
                }

                val rotation = currentLocation.bearingTo(lastLocation ?: currentLocation) + 180
                myLocationMarker?.rotation = rotation
                myLocationMarker?.position = myLocationLatLng

                lastLocation = currentLocation
            }
        }
    }

    override fun onPermissionRationaleGotIt(permission: String) {
        requestPermission.launch(permission)
    }

    private fun createLocationMarker(location: LatLng): Marker {
        return map.addMarker(
            MarkerOptions()
                .flat(true)
                .anchor(0.5f, 0.5f)
                .position(location)
                .icon(BitmapDescriptorFactory.fromBitmap(createScaledLocationIcon(100, 100)))
        )
    }

    private fun createScaledLocationIcon(width: Int, height: Int): Bitmap {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.location_marker)
        return Bitmap.createScaledBitmap(bitmap, width, height, false);
    }

    private fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        googleMap.setOnCameraMoveListener {
            val latLngBounds = map.projection.visibleRegion.latLngBounds
            calculateRectanglesJob?.cancel()
            calculateRectanglesJob = backgroundScope.launch(handler) {
                updateMap(latLngBounds)
            }
        }

        determineLocationIfHasPermissions()

        map.setOnGroundOverlayClickListener {
            onGroundOverlayClick(it)
        }
        images.clear()
        images.add(BitmapDescriptorFactory.fromResource(R.drawable.building))

        groundOverlay = map.addGroundOverlay(
            GroundOverlayOptions().image(images[0]).anchor(0f, 0f).zIndex(2f).position(polygonCenterLocation, 80f, 80f)
                .transparency(0.1f).clickable(true)
        )
    }

    private fun onGroundOverlayClick(groundOverlay: GroundOverlay) {
        toast("Overlay clicked")
    }

    private suspend fun updateMap(visibleLatLngBounds: LatLngBounds) {
        val northeast = visibleLatLngBounds.northeast
        val southwest = visibleLatLngBounds.southwest

        val widthNumberAfterComma = getNumberOfSymbolsAfterComma(POLYGON_WIDTH)
        val heightNumberAfterComma = getNumberOfSymbolsAfterComma(POLYGON_HEIGHT)

        val leftVisiblePoint = southwest.longitude.round(decimals = widthNumberAfterComma)
        val topVisiblePoint = northeast.latitude.round(decimals = heightNumberAfterComma)
        val rightVisiblePoint = northeast.longitude.round(decimals = widthNumberAfterComma)
        val bottomVisiblePoint = southwest.latitude.round(decimals = heightNumberAfterComma)

        val minLongitude = min(leftVisiblePoint, rightVisiblePoint) - POLYGON_WIDTH
        val maxLongitude = max(leftVisiblePoint, rightVisiblePoint) + POLYGON_WIDTH

        val minLatitude = min(topVisiblePoint, bottomVisiblePoint) - POLYGON_HEIGHT
        val maxLatitude = max(topVisiblePoint, bottomVisiblePoint) + POLYGON_HEIGHT

        for (longitude in minLongitude..maxLongitude step POLYGON_WIDTH) {
            for (latitude in minLatitude..maxLatitude step POLYGON_HEIGHT) {

                if (currentCoroutineContext().job.isCancelled) return

                val polygonParam = PolygonParams(
                    topLeftPoint = LatLng(
                        longitude.round(decimals = widthNumberAfterComma), latitude.round(decimals = heightNumberAfterComma)
                    ), width = POLYGON_WIDTH, height = POLYGON_HEIGHT
                )
                if (!polygons.contains(polygonParam)) {
                    withContext(Dispatchers.Main) {
                        val polygon = map.addPolygon(PolygonOptions().apply {
                            addAll(createPolygon(polygonParam))
                            fillColor(unSelectedPolygonFillColor)
                            strokeColor(unSelectedPolygonStrokeColor)
                            strokePattern(listOf(Dash(50f), Gap(100f)))
                            strokeWidth(3f)
                            clickable(true)
                        })
                        polygons[polygonParam] = polygon
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            map.setOnPolygonClickListener { polygon ->
                if (polygon.fillColor == unSelectedPolygonFillColor) {
                    polygon.fillColor = selectedPolygonFillColor
                    polygon.strokeColor = selectedPolygonStrokeColor
                    polygon.strokePattern = null
                    polygon.strokeWidth = 5f
                    polygon.zIndex = 1f
                } else {
                    polygon.fillColor = unSelectedPolygonFillColor
                    polygon.strokeColor = unSelectedPolygonStrokeColor
                    polygon.strokePattern = listOf(Dash(50f), Gap(100f))
                    polygon.strokeWidth = 3f
                    polygon.zIndex = 0f
                }
            }
        }
    }

    private fun Double.round(decimals: Int = 2): Double {
        return "%.${decimals}f".format(Locale.US, this).toDouble()
    }

    private fun getNumberOfSymbolsAfterComma(number: Double): Int {
        return number.toString()
            .substringAfterLast(".")
            .length
    }

    private infix fun ClosedRange<Double>.step(step: Double): Iterable<Double> {
        require(start.isFinite())
        require(endInclusive.isFinite())
        require(step > 0.0) { "Step must be positive, was: $step." }
        val sequence = generateSequence(start) { previous ->
            if (previous == Double.POSITIVE_INFINITY) return@generateSequence null
            val next = previous + step
            if (next > endInclusive) null else next
        }
        return sequence.asIterable()
    }

    private fun createPolygon(rectangleParams: PolygonParams): List<LatLng> {
        val (topLeftPoint, width, height) = rectangleParams

        val x = topLeftPoint.longitude
        val y = topLeftPoint.latitude

        return listOf(
            LatLng(x, y), LatLng(x + width, y), LatLng(x + width, y - height), LatLng(x, y - height)
        )
    }

    private fun showRationaleLocationDialog() {
        PermissionRationaleDialog.show(supportFragmentManager, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun determineLocationIfHasPermissions() {
        if (!::map.isInitialized) return

        checkPermission(permission = Manifest.permission.ACCESS_FINE_LOCATION, onGranted = {
            determineLocation()
        }, onShowRational = {
            showRationaleLocationDialog()
        }, onDenied = {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        })
    }

    @SuppressLint("MissingPermission")
    private fun determineLocation() {
        locationProvider.setMockMode(true)
        locationProvider.requestLocationUpdates(buildLocationRequest(), buildLocationCallback(), Looper.getMainLooper())
        val locationResult = locationProvider.lastLocation
        locationResult.addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val lastKnownLocation = task.result
                if (lastKnownLocation != null) {
                    val myLocationCoordinates = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                    //val myLocationCoordinates = LatLng(0.005, 0.005)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(myLocationCoordinates, 17f))
                }
            } else {
                toast("Ошибка при получении координат")
            }
        }
    }
}

data class PolygonParams(val topLeftPoint: LatLng, val width: Double, val height: Double)

data class PointF(val x: Double, val y: Double)