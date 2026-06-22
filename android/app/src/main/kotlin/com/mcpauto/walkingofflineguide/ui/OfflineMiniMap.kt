package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.UserPosition
import com.mcpauto.walkingofflineguide.logic.PoiLogic

@Composable
fun OfflineMiniMap(
    bbox: Bbox,
    user: UserPosition?,
    pois: List<Poi>,
    modifier: Modifier = Modifier,
    heightDp: Int = 220,
    caption: String = "오프라인 미니 지도 (핀만)",
    showUser: Boolean = true,
    showPois: Boolean = true,
) {
    val pad = 16f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(Color(0xFF1E2A3A), cornerRadius = CornerRadius(12f))
            drawRoundRect(
                color = Color(0xFF243044),
                topLeft = Offset(pad, pad),
                size = Size(size.width - pad * 2, size.height - pad * 2),
                cornerRadius = CornerRadius(8f),
            )
            val innerW = size.width - pad * 2
            val innerH = size.height - pad * 2
            if (showPois) {
                pois.forEach { p ->
                    if (!PoiLogic.inBbox(p.lat, p.lon, bbox)) return@forEach
                    val (x, y) = project(p.lat, p.lon, bbox, innerW, innerH)
                    val color = if (p.kind == "festival") Color(0xFFF59E0B) else Color(0xFF5B9FD4)
                    drawCircle(color, radius = 5f, center = Offset(x + pad, y + pad))
                }
            }
            if (showUser) {
                user?.let { u ->
                    if (!PoiLogic.inBbox(u.lat, u.lon, bbox)) return@let
                    val (x, y) = project(u.lat, u.lon, bbox, innerW, innerH)
                    val cx = x + pad
                    val cy = y + pad
                    drawCircle(Color(0x5922C55E), radius = 10f, center = Offset(cx, cy))
                    drawCircle(Color(0xFF22C55E), radius = 5f, center = Offset(cx, cy))
                }
            }
        }
        Text(
            text = caption,
            color = Color(0xFF8B9CB3),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

private fun project(lat: Double, lon: Double, bbox: Bbox, w: Float, h: Float): Pair<Float, Float> {
    val lonSpan = (bbox.east - bbox.west).coerceAtLeast(1e-9)
    val latSpan = (bbox.north - bbox.south).coerceAtLeast(1e-9)
    val x = ((lon - bbox.west) / lonSpan * w).toFloat()
    val y = ((bbox.north - lat) / latSpan * h).toFloat()
    return x to y
}
