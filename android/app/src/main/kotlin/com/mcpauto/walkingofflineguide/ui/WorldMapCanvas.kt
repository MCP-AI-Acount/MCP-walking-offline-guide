package com.mcpauto.walkingofflineguide.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.R
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
            initialValue = 0.45f,
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C1929)),
    ) {
        Image(
            painter = painterResource(R.drawable.world_map),
            contentDescription = "세계지도",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(Modifier.fillMaxSize().background(Color(0x22000000)))
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(completed) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f || completed.isEmpty()) return@detectTapGestures
                        completed.minByOrNull { r ->
                            val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                            val dx = px - offset.x
                            val dy = py - offset.y
                            dx * dx + dy * dy
                        }?.let { best ->
                            val (px, py) = MapMath.projectWorld(best.lat, best.lon, w, h)
                            val dx = px - offset.x
                            val dy = py - offset.y
                            if (dx * dx + dy * dy < 4900f) onRegionTap(best)
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            completed.forEach { r ->
                val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                val isHighlight = r.id == highlightId
                val alpha = if (isHighlight && blinkHighlight) blinkAlpha else 1f
                val base = when {
                    isHighlight -> Color(0xFF38BDF8)
                    r.visited -> Color(0xFF4ADE80)
                    else -> Color(0xFF60A5FA)
                }
                val radius = if (isHighlight) 18f else 14f
                drawCircle(base.copy(alpha = alpha * 0.25f), radius = radius * 3.2f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha * 0.55f), radius = radius * 1.8f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha), radius = radius, center = Offset(px, py))
                drawCircle(Color.White, radius = 4f, center = Offset(px, py))
            }
        }
    }
}
