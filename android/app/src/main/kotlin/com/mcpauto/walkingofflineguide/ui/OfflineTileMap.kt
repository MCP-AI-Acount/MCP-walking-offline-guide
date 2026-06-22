package com.mcpauto.walkingofflineguide.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.TileStore
import com.mcpauto.walkingofflineguide.data.UserPosition
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import com.mcpauto.walkingofflineguide.map.PoiColors
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import kotlin.math.hypot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun OfflineTileMap(
    camera: MapCamera,
    onCameraChange: (MapCamera) -> Unit,
    onResetCamera: () -> Unit,
    tileStore: TileStore?,
    tilesReady: Boolean,
    user: UserPosition?,
    pois: List<Poi>,
    highlightedPoiId: String? = null,
    routePoints: List<Pair<Double, Double>> = emptyList(),
    routeDistanceM: Int? = null,
    onPoiClick: (Poi) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cameraRef = rememberUpdatedState(camera)
    val onChangeRef = rememberUpdatedState(onCameraChange)
    val poisRef = rememberUpdatedState(pois)
    val onPoiClickRef = rememberUpdatedState(onPoiClick)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFDDE3EA)),
    ) {
        if (!tilesReady || tileStore == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        coroutineScope {
                            launch {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val c = cameraRef.value
                                        val (dx, dy) = MapCameraMath.rotatePan(dragAmount.x, dragAmount.y, c.bearingDeg)
                                        onChangeRef.value(c.withPan(dx, dy))
                                    },
                                    onDragEnd = {
                                        val c = cameraRef.value
                                        if (c.panX != 0f || c.panY != 0f) {
                                            onChangeRef.value(
                                                MapCameraMath.bakePan(c, size.width.toFloat(), size.height.toFloat()),
                                            )
                                        }
                                    },
                                )
                            }
                            launch {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (zoom == 1f && pan == Offset.Zero) return@detectTransformGestures
                                    val c = cameraRef.value
                                    var next = c
                                    if (zoom != 1f) {
                                        val span = MapCameraMath.clampSpan(c.visibleSpanM / zoom.toDouble())
                                        next = next.withSpan(span)
                                    }
                                    if (pan != Offset.Zero) {
                                        val (dx, dy) = MapCameraMath.rotatePan(pan.x, pan.y, c.bearingDeg)
                                        next = next.withPan(dx, dy)
                                    }
                                    onChangeRef.value(next)
                                }
                            }
                            launch {
                                detectTapGestures { offset ->
                                    val c = cameraRef.value
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    var best: Poi? = null
                                    var bestD = 48f
                                    poisRef.value.forEach { p ->
                                        val (px, py) = MapCameraMath.screenFromLatLon(p.lat, p.lon, c, w, h)
                                        val d = hypot(px - offset.x, py - offset.y)
                                        if (d < bestD) {
                                            bestD = d
                                            best = p
                                        }
                                    }
                                    best?.let { onPoiClickRef.value(it) }
                                }
                            }
                        }
                    },
            ) {
                val cam = cameraRef.value
                val flatCam = cam.copy(bearingDeg = 0f)
                val z = cam.baseZoom
                val w = size.width
                val h = size.height
                val scale = MapCameraMath.scaleFactor(flatCam.centerLat, z, w, flatCam.visibleSpanM)
                val (cx, cy) = MapCameraMath.latLonToWorldPx(flatCam.centerLat, flatCam.centerLon, z)
                val (xRange, yRange) = MapCameraMath.visibleTileRange(flatCam, w, h)
                val pad = if (cam.bearingDeg != 0f) 1 else 0
                val xSpan = (xRange.first - pad)..(xRange.last + pad)
                val ySpan = (yRange.first - pad)..(yRange.last + pad)
                val tilePx = (MapCameraMath.TILE_LOGICAL_PX * scale).toInt().coerceAtLeast(1)

                withTransform({
                    translate(w / 2f, h / 2f)
                    rotate(cam.bearingDeg)
                    translate(-w / 2f, -h / 2f)
                }) {
                    for (x in xSpan) {
                        for (y in ySpan) {
                            val bmp = tileStore?.get(z, x, y) ?: continue
                            val img = bmp.toComposeImage()
                            val left = ((x * MapCameraMath.TILE_LOGICAL_PX - cx) * scale + w / 2 + flatCam.panX).toFloat()
                            val top = ((y * MapCameraMath.TILE_LOGICAL_PX - cy) * scale + h / 2 + flatCam.panY).toFloat()
                            drawImage(
                                image = img,
                                dstOffset = IntOffset(left.toInt(), top.toInt()),
                                dstSize = IntSize(tilePx, tilePx),
                            )
                        }
                    }

                    if (routePoints.size >= 2) {
                        val path = Path()
                        routePoints.forEachIndexed { idx, (lat, lon) ->
                            val (px, py) = MapCameraMath.screenFromLatLon(lat, lon, flatCam, w, h)
                            if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(path, Color(0xFF2563EB), style = Stroke(width = 6f))
                        drawPath(path, Color(0xCC93C5FD), style = Stroke(width = 10f))
                    }

                    pois.forEach { p ->
                        val (px, py) = MapCameraMath.screenFromLatLon(p.lat, p.lon, flatCam, w, h)
                        if (px < -40 || py < -40 || px > w + 40 || py > h + 40) return@forEach
                        val color = PoiColors.accent(p)
                        val selected = p.id == highlightedPoiId
                        val radius = if (selected) 14f else 5f
                        if (selected) {
                            drawCircle(Color(0x99F59E0B), radius = 26f, center = Offset(px, py))
                            drawCircle(Color(0xFFF59E0B), radius = 20f, center = Offset(px, py))
                        }
                        drawCircle(color, radius = radius, center = Offset(px, py))
                        drawCircle(Color.White, radius = if (selected) 5f else 2f, center = Offset(px, py))
                        if (!selected) {
                            val rating = PoiLogic.formatRating(PoiLogic.displayRating(p))
                            drawContext.canvas.nativeCanvas.drawText(
                                "★$rating",
                                px - 10f,
                                py + 14f,
                                android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textSize = 20f
                                    setColor(android.graphics.Color.argb(230, 30, 41, 59))
                                },
                            )
                        }
                    }

                    user?.let { u ->
                        val (px, py) = MapCameraMath.screenFromLatLon(u.lat, u.lon, flatCam, w, h)
                        drawCircle(Color(0x5922C55E), radius = 14f, center = Offset(px, py))
                        drawCircle(Color(0xFF22C55E), radius = 7f, center = Offset(px, py))
                        u.bearingDeg?.let { bearing ->
                            val rad = Math.toRadians(bearing.toDouble())
                            val tipX = px + kotlin.math.sin(rad).toFloat() * 18f
                            val tipY = py - kotlin.math.cos(rad).toFloat() * 18f
                            drawLine(Color(0xFF166534), Offset(px, py), Offset(tipX, tipY), strokeWidth = 4f)
                        }
                    }
                }
            }

            val cam = cameraRef.value
            val mpp = MapCameraMath.metersPerPixel(cam.centerLat, cam.baseZoom) /
                MapCameraMath.scaleFactor(cam.centerLat, cam.baseZoom, 1000f, cam.visibleSpanM)
            val (barM, barPx) = MapCameraMath.scaleBarMeters(mpp)
            val barLabel = if (barM >= 1000) "${barM / 1000}km" else "${barM}m"
            val density = LocalDensity.current
            val barWidthDp: Dp = with(density) { barPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            barLabel,
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Box(
                            modifier = Modifier
                                .width(barWidthDp.coerceAtLeast(40.dp))
                                .height(4.dp)
                                .background(Color(0xFF1E293B), RoundedCornerShape(2.dp)),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        routeDistanceM?.let { d ->
                            Text(
                                "경로 ${formatRouteDistance(d)}",
                                color = Color(0xFF2563EB),
                                fontSize = 11.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 2.dp),
                            )
                        }
                        Text(
                            "z${cam.baseZoom} · ${MapCameraMath.spanLabel(cam.visibleSpanM)} · ${cam.bearingDeg.toInt()}°",
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun formatRouteDistance(m: Int): String =
    if (m >= 1000) "%.1fkm".format(m / 1000.0) else "${m}m"

private val imageCache = android.util.LruCache<Int, androidx.compose.ui.graphics.ImageBitmap>(512)

private fun Bitmap.toComposeImage(): androidx.compose.ui.graphics.ImageBitmap {
    val key = System.identityHashCode(this)
    imageCache.get(key)?.let { return it }
    val copy = if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this
    return copy.asImageBitmap().also { imageCache.put(key, it) }
}
