package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CrosshairIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val arm = this.size.minDimension / 2f - 2f
        val gap = 5f
        val stroke = if (tint.alpha > 0.9f) 2.2f else 1.8f
        drawLine(tint, Offset(cx - arm, cy), Offset(cx - gap, cy), strokeWidth = stroke)
        drawLine(tint, Offset(cx + gap, cy), Offset(cx + arm, cy), strokeWidth = stroke)
        drawLine(tint, Offset(cx, cy - arm), Offset(cx, cy - gap), strokeWidth = stroke)
        drawLine(tint, Offset(cx, cy + gap), Offset(cx, cy + arm), strokeWidth = stroke)
    }
}

@Composable
fun RadarRadiusBadge(
    radiusM: Int,
    modifier: Modifier = Modifier,
) {
    val ringPx = when (radiusM) {
        200 -> 14.dp
        500 -> 18.dp
        1000 -> 22.dp
        else -> 10.dp
    }
    Canvas(modifier.size(ringPx * 2 + 4.dp)) {
        val r = ringPx.toPx()
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(Color(0x552563EB), radius = r, center = c, style = Stroke(width = 2.5f))
        drawCircle(Color(0x3322C55E), radius = r, center = c, style = Stroke(width = 1f))
    }
}
