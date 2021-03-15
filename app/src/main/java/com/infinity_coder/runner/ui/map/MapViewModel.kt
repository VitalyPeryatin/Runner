package com.infinity_coder.runner.ui.map

import android.graphics.Color
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.*
import com.infinity_coder.runner.ui.map.models.PolygonParams
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MapViewModel: ViewModel() {

    companion object {
        private const val POLYGON_HEIGHT = 0.001
        private const val POLYGON_WIDTH = 0.001

        private const val DESELECTED_POLYGON_FILL_COLOR = Color.TRANSPARENT
        private val DESELECTED_POLYGON_STROKE_COLOR = Color.parseColor("#B1BEC5")
        private val SELECTED_POLYGON_FILL_COLOR = Color.parseColor("#0FFE612C")
        private val SELECTED_POLYGON_STROKE_COLOR = Color.parseColor("#FE612C")
        private val CAPTURING_POLYGON_FILL_COLOR = Color.parseColor("#0F000000")
        private val CAPTURING_POLYGON_STROKE_COLOR = Color.parseColor("#000000")
        private val SOLID_STROKE_PATTERN = null
        private val DASHED_STROKE_PATTERN = listOf(Dash(25f), Gap(50f))
    }

    private val _polygonParamsFlow = MutableSharedFlow<PolygonParams>()
    val polygonParamsFlow: SharedFlow<PolygonParams> = _polygonParamsFlow

    private val _myLocationPathFlow = MutableSharedFlow<LatLng>()
    val myLocationPathFlow: SharedFlow<LatLng> = _myLocationPathFlow

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        Log.e("CoroutineException", "$exception")
    }
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)
    private var drawDistrictsJob: Job? = null
    private val polygonParams: HashSet<LatLng> = hashSetOf()
    private val polygonParamsMap: HashMap<Pair<Double, Double>, PolygonParams> = hashMapOf()
    private var capturingPolygonParams: PolygonParams? = null

    private var lastLocation: Location? = null
    private var myLocationMarker: Marker? = null

    fun launchDrawDistrictsJob(latLngBounds: LatLngBounds) {
        drawDistrictsJob?.cancel()
        drawDistrictsJob = backgroundScope.launch {
            drawDistricts(latLngBounds)
        }
    }

    fun onPolygonClicked(polygon: Polygon) {
        if (polygon.fillColor == CAPTURING_POLYGON_FILL_COLOR) return

        if (polygon.fillColor == DESELECTED_POLYGON_FILL_COLOR) {
            polygon.fillColor = SELECTED_POLYGON_FILL_COLOR
            polygon.strokeColor = SELECTED_POLYGON_STROKE_COLOR
            polygon.strokePattern = SOLID_STROKE_PATTERN
            polygon.strokeWidth = 5f
            polygon.zIndex = 1f
        } else {
            polygon.fillColor = DESELECTED_POLYGON_FILL_COLOR
            polygon.strokeColor = DESELECTED_POLYGON_STROKE_COLOR
            polygon.strokePattern = DASHED_STROKE_PATTERN
            polygon.strokeWidth = 3f
            polygon.zIndex = 0f
        }
    }

    private suspend fun drawDistricts(visibleLatLngBounds: LatLngBounds) {
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

        val polygonsForDrawing = mutableListOf<PolygonParams>()
        for (longitude in minLongitude..maxLongitude step POLYGON_WIDTH) {
            for (latitude in minLatitude..maxLatitude step POLYGON_HEIGHT) {

                if (currentCoroutineContext().job.isCancelled) return

                val polygonParam = PolygonParams(
                    topLeftLatLng = LatLng(
                        latitude.round(decimals = widthNumberAfterComma),
                        longitude.round(decimals = heightNumberAfterComma)
                    ), width = POLYGON_WIDTH, height = POLYGON_HEIGHT
                )
                if (!polygonParams.contains(polygonParam.topLeftLatLng)) {
                    val polygonOptions = PolygonOptions().apply {
                        addAll(createPolygon(polygonParam))
                        fillColor(DESELECTED_POLYGON_FILL_COLOR)
                        strokeColor(DESELECTED_POLYGON_STROKE_COLOR)
                        strokePattern(DASHED_STROKE_PATTERN)
                        strokeWidth(3f)
                        clickable(true)
                    }
                    polygonParam.polygonOptions = polygonOptions
                    polygonsForDrawing.add(polygonParam)
                }
            }
        }

        for (index in 0 until polygonsForDrawing.size step 2) {
            withContext(Dispatchers.Main) {
                val polygonParam = polygonsForDrawing[index]
                _polygonParamsFlow.emit(polygonParam)
                polygonParams.add(polygonParam.topLeftLatLng)
                val pair = Pair(polygonParam.topLeftLatLng.latitude, polygonParam.topLeftLatLng.longitude)
                polygonParamsMap[pair] = polygonParam
            }
        }
        for (index in 1 until polygonsForDrawing.size step 2) {
            withContext(Dispatchers.Main) {
                val polygonParam = polygonsForDrawing[index]
                _polygonParamsFlow.emit(polygonParam)
                polygonParams.add(polygonParam.topLeftLatLng)
                val pair = Pair(polygonParam.topLeftLatLng.latitude, polygonParam.topLeftLatLng.longitude)
                polygonParamsMap[pair] = polygonParam
            }
        }
    }

    private fun Double.round(decimals: Int = 2): Double {
        return "%.${decimals}f".format(Locale.US, this).toDouble()
    }

    private fun Double.floor(decimals: Int): Double {
        val multiplier = 10f.pow(decimals)
        return (this * multiplier).toInt().toDouble() / multiplier
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
            LatLng(y, x), LatLng(y, x + width), LatLng(y - height, x + width), LatLng(y - height, x)
        )
    }

    suspend fun updateLocationMarker(newLocation: Location, locationMarker: Marker) {
        val myLocationLatLng = LatLng(newLocation.latitude, newLocation.longitude)
        myLocationMarker = locationMarker

        val rotation = newLocation.bearingTo(lastLocation ?: newLocation) + 180
        locationMarker.rotation = rotation
        locationMarker.position = myLocationLatLng

        _myLocationPathFlow.emit(myLocationLatLng)

        val widthNumberAfterComma = getNumberOfSymbolsAfterComma(POLYGON_WIDTH)
        val heightNumberAfterComma = getNumberOfSymbolsAfterComma(POLYGON_HEIGHT)

        val topLeftDistinctLatitude = newLocation.latitude.floor(decimals = heightNumberAfterComma) + POLYGON_HEIGHT
        val topLeftDistinctLongitude = newLocation.longitude.floor(decimals = widthNumberAfterComma)

        val polygonParam = polygonParamsMap[Pair(topLeftDistinctLatitude, topLeftDistinctLongitude)]
        if (polygonParam != null && capturingPolygonParams != polygonParam) {
            capturingPolygonParams?.let {
                it.polygonOptions.fillColor(DESELECTED_POLYGON_FILL_COLOR)
                it.polygonOptions.strokeColor(DESELECTED_POLYGON_STROKE_COLOR)
                it.polygonOptions.strokePattern(DASHED_STROKE_PATTERN)
                it.polygonOptions.strokeWidth(3f)
                it.polygonOptions.zIndex(0f)
                _polygonParamsFlow.emit(it)
            }

            capturingPolygonParams = polygonParam
            polygonParam.polygonOptions.fillColor(CAPTURING_POLYGON_FILL_COLOR)
            polygonParam.polygonOptions.strokeColor(CAPTURING_POLYGON_STROKE_COLOR)
            polygonParam.polygonOptions.strokePattern(SOLID_STROKE_PATTERN)
            polygonParam.polygonOptions.strokeWidth(5f)
            polygonParam.polygonOptions.zIndex(1f)
            _polygonParamsFlow.emit(polygonParam)
        }

        lastLocation = newLocation
    }

    fun myLocationMockMover() {
        viewModelScope.launch(Dispatchers.IO) {
            for (iteration in 1..10000) {
                if (lastLocation != null && myLocationMarker != null) {
                    val nextLocation = Location("")
                    nextLocation.latitude = lastLocation!!.latitude + 0.00001
                    nextLocation.longitude = lastLocation!!.longitude + 0.00001

                    withContext(Dispatchers.Main) {
                        updateLocationMarker(lastLocation!!, myLocationMarker!!)
                    }
                    lastLocation = nextLocation
                    delay(10)
                }
            }
        }
    }
}