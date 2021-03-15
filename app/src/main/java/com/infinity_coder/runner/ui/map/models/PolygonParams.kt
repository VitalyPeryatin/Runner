package com.infinity_coder.runner.ui.map.models

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolygonOptions

data class PolygonParams(
    val topLeftLatLng: LatLng,
    val width: Double,
    val height: Double,
    var polygonOptions: PolygonOptions = PolygonOptions(),
    var isSelected: Boolean = false,
    var isCapturing: Boolean = false
)