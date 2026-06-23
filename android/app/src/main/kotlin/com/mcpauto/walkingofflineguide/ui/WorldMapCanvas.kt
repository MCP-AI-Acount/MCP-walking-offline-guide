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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.R
import com.mcpauto.walkingofflineguide.data.RegionPlayability
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.logic.MapMath

data class WorldMapPin(
    val region: RegionRecord,
    val state: RegionPlayability.PinState,
)

@Composable
fun WorldMapCanvas(
    pins: List<WorldMapPin>,
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
    val tappable = remember(pins) { pins.map { it.region } }

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
                .pointerInput(tappable) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f || tappable.isEmpty()) return@detectTapGestures
                        tappable.minByOrNull { r ->
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
            pins.forEach { pin ->
                val r = pin.region
                val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                val isHighlight = r.id == highlightId
                val alpha = if (isHighlight && blinkHighlight) blinkAlpha else 1f
                val base = when {
                    isHighlight -> Color(0xFF38BDF8)
                    pin.state == RegionPlayability.PinState.PARTIAL -> Color(0xFFF59E0B)
                    r.visited -> Color(0xFF4ADE80)
                    else -> Color(0xFF60A5FA)
                }
                val radius = if (isHighlight) 18f else 14f
                drawCircle(base.copy(alpha = alpha * 0.25f), radius = radius * 3.2f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha * 0.55f), radius = radius * 1.8f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha), radius = radius, center = Offset(px, py))
                drawCircle(Color.White, radius = 4f, center = Offset(px, py))
                drawCheckBadge(
                    center = Offset(px + radius * 0.85f, py - radius * 0.85f),
                    ring = base.copy(alpha = alpha),
                    partial = pin.state == RegionPlayability.PinState.PARTIAL,
                )
            }
        }
    }
}

private fun DrawScope.drawCheckBadge(center: Offset, ring: Color, partial: Boolean) {
    val r = 8f
    drawCircle(Color(0xFF0F172A), r + 1.2f, center)
    drawCircle(if (partial) Color(0xFFFFF7ED) else Color.White, r, center)
    drawCircle(ring.copy(alpha = 0.35f), r - 1f, center)
    val path = Path().apply {
        moveTo(center.x - 3.2f, center.y + 0.2f)
        lineTo(center.x - 0.8f, center.y + 2.8f)
        lineTo(center.x + 3.8f, center.y - 2.6f)
    }
    drawPath(
        path,
        if (partial) Color(0xFFEA580C) else Color(0xFF15803D),
        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
