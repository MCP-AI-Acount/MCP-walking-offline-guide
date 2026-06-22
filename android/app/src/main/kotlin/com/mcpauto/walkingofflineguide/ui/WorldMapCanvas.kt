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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.data.RegionRecord
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
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "a",
        ).value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .background(Color(0xFF1E3A5F), RoundedCornerShape(12.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(regions) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        regions.filter { it.downloadComplete }.minByOrNull { r ->
                            val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                            val dx = px - offset.x
                            val dy = py - offset.y
                            dx * dx + dy * dy
                        }?.let { best ->
                            val (px, py) = MapMath.projectWorld(best.lat, best.lon, w, h)
                            val dx = px - offset.x
                            val dy = py - offset.y
                            if (dx * dx + dy * dy < 900f) onRegionTap(best)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF0F172A))
            // 간단한 대륙 윤곽 (장식)
            drawRect(Color(0xFF334155), topLeft = Offset(w * 0.15f, h * 0.25f), size = androidx.compose.ui.geometry.Size(w * 0.25f, h * 0.35f))
            drawRect(Color(0xFF334155), topLeft = Offset(w * 0.45f, h * 0.2f), size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.4f))
            drawRect(Color(0xFF334155), topLeft = Offset(w * 0.62f, h * 0.22f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.38f))

            regions.filter { it.downloadComplete }.forEach { r ->
                val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                val base = if (r.visited) Color(0xFF4ADE80) else Color(0xFF64748B)
                val alpha = if (r.id == highlightId && blinkHighlight) blinkAlpha else 1f
                val radius = if (r.id == highlightId) 18f else 12f
                drawCircle(base.copy(alpha = alpha * 0.35f), radius = radius * 2.2f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha), radius = radius, center = Offset(px, py))
                drawCircle(Color.White, radius = 4f, center = Offset(px, py))
            }
        }
    }
}
