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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.WorldMapData
import com.mcpauto.walkingofflineguide.logic.MapMath

/** 정치 지도 스타일 — 지형 없음 · 국가명 · 핀 */
@Composable
fun WorldMapCanvas(
    regions: List<RegionRecord>,
    highlightId: String?,
    blinkHighlight: Boolean,
    onRegionTap: (RegionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val catalog = remember { GeoCatalog(context) }
    val countries = remember { catalog.allCountries() }
    val density = LocalDensity.current
    val labelPx = with(density) { 9.sp.toPx() }

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
            .background(Color(0xFFB8D4E8)),
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

            drawRect(Color(0xFFB8D4E8), size = size)

            WorldMapData.landMasses.forEach { ring ->
                val path = Path()
                ring.forEachIndexed { i, (lat, lon) ->
                    val (x, y) = MapMath.projectWorld(lat, lon, w, h)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, Color(0xFFE8F0E8))
                drawPath(path, Color(0xFF94A3B8), style = Stroke(width = 1.2f))
            }

            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = labelPx
                color = android.graphics.Color.argb(200, 51, 65, 85)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            countries.forEach { c ->
                val (px, py) = MapMath.projectWorld(c.lat, c.lon, w, h)
                if (px < 0f || py < 0f || px > w || py > h) return@forEach
                val label = c.nameKo.ifBlank { c.nameEn }.take(6)
                drawContext.canvas.nativeCanvas.drawText(label, px, py, paint)
            }

            completed.forEach { r ->
                val (px, py) = MapMath.projectWorld(r.lat, r.lon, w, h)
                val base = if (r.visited) Color(0xFF16A34A) else Color(0xFF64748B)
                val alpha = if (r.id == highlightId && blinkHighlight) blinkAlpha else 1f
                val radius = if (r.id == highlightId) 13f else 8f
                drawCircle(base.copy(alpha = alpha * 0.4f), radius = radius * 2.5f, center = Offset(px, py))
                drawCircle(base.copy(alpha = alpha), radius = radius, center = Offset(px, py))
                drawCircle(Color.White, radius = 2.5f, center = Offset(px, py))
            }
        }
    }
}
