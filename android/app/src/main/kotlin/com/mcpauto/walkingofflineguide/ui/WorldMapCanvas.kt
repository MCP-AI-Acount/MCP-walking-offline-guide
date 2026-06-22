package com.mcpauto.walkingofflineguide.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.WorldMapData
import com.mcpauto.walkingofflineguide.logic.MapMath

@Composable
fun WorldMapCanvas(
    regions: List<RegionRecord>,
    highlightId: String?,
    blinkHighlight: Boolean,
    onRegionTap: (RegionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blinkAlpha = if (blinkHighlight && highlightId != null) {
        rememberInfiniteTransition(label = "blink").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "a",
        ).value
    } else {
        1f
    }
    val completed = remember(regions) { regions.filter { it.downloadComplete } }

    Box(
        modifier = modifier
            .background(Color(0xFF0C1929), RoundedCornerShape(12.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(completed) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@detectTapGestures
                        completed.minByOrNull { r ->
                            val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                            val dx = px - offset.x
                            val dy = py - offset.y
                            dx * dx + dy * dy
                        }?.let { best ->
                            val (px, py) = MapMath.projectWorld(best.lat, best.lon, w, h)
                            val dx = px - offset.x
                            val dy = py - offset.y
                            if (dx * dx + dy * dy < 1600f) onRegionTap(best)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1E3A5F), Color(0xFF0F172A)),
                ),
                size = size,
            )

            // 위도·경도 격자
            for (lon in -150 until 180 step 30) {
                val (x1, _) = MapMath.projectWorld(0.0, lon.toDouble(), w, h)
                drawLine(Color(0x33FFFFFF), Offset(x1, 0f), Offset(x1, h), strokeWidth = 1f)
            }
            for (lat in -60 until 80 step 30) {
                val (_, y1) = MapMath.projectWorld(lat.toDouble(), 0.0, w, h)
                drawLine(Color(0x22FFFFFF), Offset(0f, y1), Offset(w, y1), strokeWidth = 1f)
            }

            WorldMapData.landMasses.forEach { ring ->
                val path = Path()
                ring.forEachIndexed { i, (lat, lon) ->
                    val (x, y) = MapMath.projectWorld(lat, lon, w, h)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, Color(0xFF3D5A40))
                drawPath(path, Color(0xFF5C7A5A), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
            }

            completed.forEach { r ->
                val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                if (px.isNaN() || py.isNaN()) return@forEach
                val base = if (r.visited) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                val alpha = if (r.id == highlightId && blinkHighlight) blinkAlpha else 1f
                val radius = if (r.id == highlightId) 16f else 10f
                drawCircle(base.copy(alpha = alpha * 0.4f), radius = radius * 2.5f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha), radius = radius, center = Offset(px, py))
                drawCircle(Color.White, radius = 3.5f, center = Offset(px, py))
            }
        }
    }
}
