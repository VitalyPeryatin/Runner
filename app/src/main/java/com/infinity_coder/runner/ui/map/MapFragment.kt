package com.infinity_coder.runner.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.infinity_coder.runner.R
import com.infinity_coder.runner.ui.common.dialogs.OnPermissionRationaleListener
import com.infinity_coder.runner.ui.common.dialogs.PermissionRationaleDialog
import com.infinity_coder.runner.utilities.extensions.checkPermission
import com.infinity_coder.runner.utilities.extensions.toast
import kotlinx.android.synthetic.main.fragment_map.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class MapFragment: Fragment(R.layout.fragment_map), OnPermissionRationaleListener {

    companion object {

        fun newInstance(): MapFragment {
            return MapFragment()
        }
    }

    private lateinit var map: GoogleMap
    private var myLocationMarker: Marker? = null

    private val userPathColor = Color.parseColor("#FE612C")

    private lateinit var viewModel: MapViewModel
    private var userPathPolyline: Polyline? = null
    private var polygonsMap: HashMap<LatLng, Polygon> = hashMapOf()

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            determineLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MapViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync { googleMap ->
            onMapReady(googleMap)
        }

        findMyLocationButton.setOnClickListener {
            determineLocationIfHasPermissions()
        }

        lifecycleScope.launchWhenCreated {
            viewModel.polygonParamsFlow.collect { polygonParams ->
                if (polygonsMap.containsKey(polygonParams.topLeftLatLng)) {
                    val polygon = polygonsMap[polygonParams.topLeftLatLng]
                    val polygonOptions = polygonParams.polygonOptions
                    polygon?.fillColor = polygonOptions.fillColor
                    polygon?.strokeColor = polygonOptions.strokeColor
                    polygon?.strokePattern = polygonOptions.strokePattern
                    polygon?.strokeWidth = polygonOptions.strokeWidth
                    polygon?.zIndex = polygonOptions.zIndex
                } else {
                    val polygon = map.addPolygon(polygonParams.polygonOptions)
                    polygonsMap[polygonParams.topLeftLatLng] = polygon
                }
            }
        }
        lifecycleScope.launchWhenCreated {
            viewModel.myLocationPathFlow.collect {
                drawUserPath(it)
            }
        }
    }

    private fun onMapReady(googleMap: GoogleMap) {
        map = googleMap.apply {
            setMaxZoomPreference(20f)
            setMinZoomPreference(15.5f)
            isIndoorEnabled = false
            isTrafficEnabled = false
        }

        applyMapStyles()
        launchDrawDistrictsJob()
        determineLocationIfHasPermissions()
        drawGroundOverlays()

        map.setOnCameraMoveListener {
            launchDrawDistrictsJob()
        }
        map.setOnGroundOverlayClickListener {
            toast("Overlay clicked")
        }
        map.setOnPolygonClickListener(viewModel::onPolygonClicked)
    }

    private fun applyMapStyles() {
        try {
            map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
            )
        } catch (e: Resources.NotFoundException) {
        }
    }

    private fun drawGroundOverlays() {
        val mockImage = BitmapDescriptorFactory.fromResource(R.drawable.avatar1)
        val mockGroundOverlayLocation = LatLng(55.6388593, 37.6704059)
        map.addGroundOverlay(
            GroundOverlayOptions().image(mockImage).anchor(0f, 0f).zIndex(2f)
                .position(mockGroundOverlayLocation, 80f, 80f).transparency(0.1f).clickable(true)
        )
    }

    private fun launchDrawDistrictsJob() {
        val latLngBounds = map.getVisibleLatLngBounds()
        viewModel.launchDrawDistrictsJob(latLngBounds)
    }

    private fun GoogleMap.getVisibleLatLngBounds(): LatLngBounds {
        return projection.visibleRegion.latLngBounds
    }

    private fun provideLocationRequest(): LocationRequest {
        return LocationRequest.create().apply {
            interval = 100
            fastestInterval = 50
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime = 100
        }
    }

    private fun provideLocationCallback(): LocationCallback {
        return object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // TODO
                // updateLocationMarker(locationResult.lastLocation)
            }
        }
    }

    private fun updateLocationMarker(newLocation: Location) {

        val locationMarker = myLocationMarker ?: createNewLocationMarker()
        if (myLocationMarker == null) {
            myLocationMarker = locationMarker
        }

        lifecycleScope.launch {
            viewModel.updateLocationMarker(newLocation, locationMarker)
        }
    }

    override fun onPermissionRationaleGotIt(permission: String) {
        requestPermission.launch(permission)
    }

    private fun createNewLocationMarker(): Marker {
        return map.addMarker(
            MarkerOptions().flat(true).anchor(0.5f, 0.5f).position(LatLng(90.0, 0.0))
                .icon(BitmapDescriptorFactory.fromBitmap(createScaledLocationIcon(100, 100)))
        )
    }

    private fun createScaledLocationIcon(width: Int, height: Int): Bitmap {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.location_marker)
        return Bitmap.createScaledBitmap(bitmap, width, height, false)
    }

    private fun showRationaleLocationDialog() {
        PermissionRationaleDialog.show(childFragmentManager, Manifest.permission.ACCESS_FINE_LOCATION)
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
        val locationProvider = LocationServices.getFusedLocationProviderClient(requireContext())
        locationProvider.requestLocationUpdates(provideLocationRequest(), provideLocationCallback(), Looper.getMainLooper())
        val locationResult = locationProvider.lastLocation
        locationResult.addOnCompleteListener(requireActivity()) { task ->
            if (task.isSuccessful) {
                val lastKnownLocation = task.result
                if (lastKnownLocation != null) {
                    lifecycleScope.launch {
                        updateLocationMarker(lastKnownLocation)
                    }

                    userPathPolyline = map.addPolyline(
                        PolylineOptions()
                            .clickable(true)
                            .color(userPathColor)
                            .width(10f)
                            .startCap(RoundCap())
                            .endCap(RoundCap())
                            .zIndex(2f)
                            .add(LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude))
                    )

                    viewModel.myLocationMockMover()
                    val myLocationCoordinates = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)

                    map.moveCamera(CameraUpdateFactory.newLatLng(myLocationCoordinates))
                }
            } else {
                toast("Ошибка при получении координат")
            }
        }
    }

    private fun drawUserPath(latLngPoint: LatLng) {
        userPathPolyline?.let { polyline ->
            val points = polyline.points
            points?.add(latLngPoint)
            polyline.points = points
        }
    }
}