package com.mcpauto.walkingofflineguide.map

import androidx.compose.ui.graphics.Color
import com.mcpauto.walkingofflineguide.data.Poi

object PoiColors {
    val restaurant = Color(0xFFEA580C)
    val hotel = Color(0xFF8B5CF6)
    /** 경로·링크 파랑(#2563EB)과 구분 — 청록 */
    val sight = Color(0xFF0D9488)
    val festival = Color(0xFFF59E0B)

    fun accent(poi: Poi): Color = when (poi.kind) {
        "restaurant" -> restaurant
        "hotel" -> hotel
        "festival" -> festival
        else -> sight
    }
}
