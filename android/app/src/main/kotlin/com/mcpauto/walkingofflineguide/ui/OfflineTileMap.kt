package com.mcpauto.walkingofflineguide.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import android.graphics.Region
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.TileStore
import com.mcpauto.walkingofflineguide.data.UserPosition
import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import com.mcpauto.walkingofflineguide.logic.MapHeading
import com.mcpauto.walkingofflineguide.map.PoiColors
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.network.OnlineTileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun OfflineTileMap(
    camera: MapCamera,
    onCameraChange: (MapCamera) -> Unit,
    onResetCamera: () -> Unit,
    tileStore: TileStore?,
    tilesReady: Boolean,
    availableZooms: Set<Int> = RegionDownloadManager.ONLINE_ZOOMS,
    secondaryTileStore: TileStore? = null,
    onlineTiles: OnlineTileProvider? = null,
    onlineZooms: Set<Int> = RegionDownloadManager.ONLINE_ZOOMS,
    screenSize: IntSize = IntSize.Zero,
    user: UserPosition?,
    headingUp: Boolean = false,
    gpsLocked: Boolean = false,
    targetBearingDeg: Float? = null,
    pois: List<Poi>,
    highlightedPoiId: String? = null,
    routePoints: List<Pair<Double, Double>> = emptyList(),
    routeDistanceM: Int? = null,
    onPoiClick: (Poi) -> Unit = {},
    /** 레이더 반경(km) — 유저 주변 원, null/0이면 숨김 */
    userRadiusKm: Double? = null,
    /** 한 손가락 드래그 → GPS 고정 해제 */
    onGpsLockOff: (() -> Unit)? = null,
    tileCacheGeneration: Int = 0,
    /** 활성 행정지역 1개 — 타일·온라인 프리페치 클립 */
    regionClipBbox: Bbox? = null,
    modifier: Modifier = Modifier,
) {
    val cameraRef = rememberUpdatedState(camera)
    val onChangeRef = rememberUpdatedState(onCameraChange)
    val poisRef = rememberUpdatedState(pois)
    val onPoiClickRef = rememberUpdatedState(onPoiClick)
    val zoomsRef = rememberUpdatedState(availableZooms)
    val onlineRef = rememberUpdatedState(onlineTiles)
    val onlineZoomsRef = rememberUpdatedState(onlineZooms)
    val secondaryRef = rememberUpdatedState(secondaryTileStore)
    val headingUpRef = rememberUpdatedState(headingUp)
    val gpsLockedRef = rememberUpdatedState(gpsLocked)
    val targetBearingRef = rememberUpdatedState(targetBearingDeg)
    val userRadiusRef = rememberUpdatedState(userRadiusKm)
    val onGpsLockOffRef = rememberUpdatedState(onGpsLockOff)
    val regionClipRef = rememberUpdatedState(regionClipBbox)
    var smoothBearingDeg by remember { mutableFloatStateOf(0f) }
    var onlineRev by remember { mutableIntStateOf(0) }

    LaunchedEffect(headingUp, gpsLocked) {
        if (!headingUp && !gpsLocked) {
            smoothBearingDeg = 0f
            return@LaunchedEffect
        }
        var smoothed = smoothBearingDeg
        targetBearingRef.value?.let {
            smoothed = it
            smoothBearingDeg = it
        }
        while (isActive) {
            delay(32L)
            val target = targetBearingRef.value ?: continue
            smoothed = MapCameraMath.lerpAngleDegrees(smoothed, target, 0.62f)
            smoothBearingDeg = smoothed
        }
    }

    val mapBearingRef = rememberUpdatedState(if (headingUp) smoothBearingDeg else 0f)
    var viewportWpx by remember { mutableIntStateOf(0) }
    var viewportHpx by remember { mutableIntStateOf(0) }

    fun activeZoomPool(): Set<Int> {
        val offline = zoomsRef.value
        val online = onlineRef.value
        return if (online != null) offline + onlineZoomsRef.value else offline
    }

    LaunchedEffect(
        camera.centerLat,
        camera.centerLon,
        camera.visibleSpanM,
        camera.baseZoom,
        camera.panX,
        camera.panY,
        screenSize,
        onlineTiles,
        user?.lat,
        user?.lon,
        regionClipBbox,
    ) {
        val o = onlineTiles ?: return@LaunchedEffect
        if (screenSize.width <= 0 || screenSize.height <= 0) return@LaunchedEffect
        o.prefetchForCamera(
            camera,
            screenSize.width,
            screenSize.height,
            anchorLat = user?.lat,
            anchorLon = user?.lon,
            clipBbox = regionClipBbox,
        )
        onlineRev = o.revision()
    }

    val mapActive = tilesReady && (tileStore != null || onlineTiles != null)

    val layerRotationDeg = when {
        headingUp -> MapHeading.mapRotationDeg(smoothBearingDeg)
        camera.bearingDeg != 0f -> camera.bearingDeg
        else -> 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
            .background(Color(0xFFDDE3EA), RoundedCornerShape(8.dp)),
    ) {
        if (!mapActive) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            @Suppress("UNUSED_VARIABLE")
            val _onlineTick = onlineRev
            @Suppress("UNUSED_VARIABLE")
            val _bearingTick = smoothBearingDeg
            @Suppress("UNUSED_VARIABLE")
            val _tileGen = tileCacheGeneration
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val c = cameraRef.value
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val hu = headingUpRef.value
                            val rot = if (hu) MapHeading.mapRotationDeg(mapBearingRef.value) else 0f
                            val pool = activeZoomPool()
                            val z = MapCameraMath.pickRenderZoom(
                                c.centerLat, w, c.visibleSpanM, pool,
                            )
                            val renderCam = buildRenderCamera(c, user, hu, gpsLockedRef.value, z)
                            val locked = gpsLockedRef.value && user != null
                            var best: Poi? = null
                            var bestD = 48f
                            poisRef.value.forEach { p ->
                                val (px, py) = viewportMapPoint(
                                    p.lat, p.lon, renderCam, w, h, locked, user, hu, rot,
                                )
                                val d = hypot(px - offset.x, py - offset.y)
                                if (d < bestD) {
                                    bestD = d
                                    best = p
                                }
                            }
                            best?.let { onPoiClickRef.value(it) }
                        }
                    }
                    .pointerInput(Unit) {
                        while (true) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val zoomDelta = kotlin.math.abs(
                                    kotlin.math.ln(zoom.toDouble().coerceIn(1e-6, 1e6)),
                                )
                                var next = cameraRef.value
                                val heading = headingUpRef.value
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val pool = activeZoomPool()

                                if (zoomDelta > MAP_ZOOM_LOG_THRESHOLD) {
                                    val spanMax = MapCameraMath.maxSpanM(heading)
                                    val spanMin = MapCameraMath.minSpanM(heading)
                                    val logSpan = kotlin.math.ln(
                                        next.visibleSpanM.coerceIn(spanMin, spanMax),
                                    )
                                    val span = kotlin.math.exp(
                                        logSpan - kotlin.math.ln(zoom.toDouble()),
                                    )
                                    next = MapCameraMath.cameraWithSpan(
                                        next,
                                        span,
                                        w,
                                        pool,
                                        heading = heading,
                                    )
                                }

                                if (pan.x != 0f || pan.y != 0f) {
                                    val bearing = if (heading) {
                                        MapHeading.mapRotationDeg(mapBearingRef.value)
                                    } else {
                                        0f
                                    }
                                    val (pdx, pdy) = screenDragToMapPan(pan.x, pan.y, bearing)
                                    next = next.withPan(pdx, pdy)
                                }

                                onChangeRef.value(next)
                            }
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                        .onSizeChanged {
                            viewportWpx = it.width
                            viewportHpx = it.height
                        },
                ) {
                    val vfW = (if (viewportWpx > 0) viewportWpx else screenSize.width)
                        .toFloat()
                        .coerceAtLeast(1f)
                    val vfH = (if (viewportHpx > 0) viewportHpx else screenSize.height)
                        .toFloat()
                        .coerceAtLeast(1f)
                    val headingMargin = if (headingUp) {
                        headingCoverMargin(vfW, vfH, layerRotationDeg)
                    } else {
                        1f
                    }
                    // 회전 클립 방지 — GPS 중심 정사각형 오버스캔 (가로·세로 별도 cap 시 사각형 잘림)
                    val canvasSide = (maxOf(vfW, vfH) * headingMargin * 1.06f)
                        .toInt()
                        .coerceAtMost(HEADING_CANVAS_MAX)
                        .coerceAtLeast(1)
                    val canvasW = canvasSide
                    val canvasH = canvasSide

                    Box(
                        Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(canvasW, canvasH),
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeRelative(
                                        x = (constraints.maxWidth - canvasW) / 2,
                                        y = (constraints.maxHeight - canvasH) / 2,
                                    )
                                }
                            }
                            .graphicsLayer {
                                clip = false
                                if (headingUp) {
                                    rotationZ = layerRotationDeg
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                } else {
                                    rotationZ = 0f
                                }
                            },
                    ) {
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { clip = false },
                        ) {
                            val cam = cameraRef.value
                            val drawW = size.width.toFloat()
                            val drawH = size.height.toFloat()
                            val wf = drawW.coerceAtLeast(1f)
                            val hf = drawH.coerceAtLeast(1f)
                            val pivotX = drawW / 2f
                            val pivotY = drawH / 2f
                            val pool = activeZoomPool()
                            val anchorLat = when {
                                user != null && (headingUp || gpsLocked) -> user.lat
                                else -> cam.centerLat
                            }
                            val z = MapCameraMath.pickRenderZoom(
                                anchorLat,
                                vfW,
                                cam.visibleSpanM,
                                pool,
                            )
                            val renderCam = buildRenderCamera(cam, user, headingUp, gpsLocked, z)
                            val gpsAnchor = gpsLocked && user != null
                            val (compX, compY) = if (gpsAnchor) {
                                MapCameraMath.gpsLockScreenCompensationOverscan(
                                    renderCam, vfW, vfH, wf, hf, user!!.lat, user.lon,
                                )
                            } else {
                                0f to 0f
                            }
                            val rot = layerRotationDeg
                            val scale = MapCameraMath.scaleFactor(
                                renderCam.centerLat, z, vfW, renderCam.visibleSpanM,
                            )
                            val (cx, cy) = MapCameraMath.latLonToWorldPx(
                                renderCam.centerLat, renderCam.centerLon, z,
                            )
                            val (centerTx, centerTy) = if (user != null && (headingUp || gpsLocked)) {
                                MapCameraMath.tileIndexAt(user.lat, user.lon, z)
                            } else {
                                MapCameraMath.tileIndexAt(renderCam.centerLat, renderCam.centerLon, z)
                            }
                            val (xSpan, ySpan) = run {
                                val clip = regionClipRef.value?.takeIf { MapCameraMath.bboxIsValid(it) }
                                if (headingUp) {
                                    val gridSide = MapCameraMath.pickHeadingSquareGridSide(
                                        vfW, vfH, renderCam.visibleSpanM,
                                        renderCam.centerLat, z, headingMargin,
                                    )
                                    val square = MapCameraMath.squareTileRangeAround(
                                        centerTx, centerTy, gridSide, z,
                                    )
                                    if (clip == null) {
                                        square
                                    } else {
                                        MapCameraMath.clipTileRangesToBbox(
                                            square.first, square.second, z, clip,
                                        ) ?: (0..-1 to 0..-1)
                                    }
                                } else {
                                    val (xRange, yRange) = MapCameraMath.visibleTileRange(
                                        renderCam, vfW, vfH,
                                    )
                                    if (clip == null) {
                                        MapCameraMath.clampTileIndexRange(xRange, MAX_TILES_NORTH) to
                                            MapCameraMath.clampTileIndexRange(yRange, MAX_TILES_NORTH)
                                    } else {
                                        MapCameraMath.clipTileRangesToBbox(xRange, yRange, z, clip)
                                            ?: (0..-1 to 0..-1)
                                    }
                                }
                            }
                            val tilePx = (MapCameraMath.TILE_LOGICAL_PX * scale).toInt().coerceAtLeast(1)
                            val originX = pivotX + renderCam.panX
                            val originY = pivotY + renderCam.panY
                            val nc = drawContext.canvas.nativeCanvas

                            MapTileDraw.drawTiles(
                                nc,
                                tileStore,
                                secondaryRef.value,
                                onlineRef.value,
                                renderCam,
                                z,
                                xSpan,
                                ySpan,
                                scale.toFloat(),
                                cx,
                                cy,
                                originX,
                                originY,
                                tilePx,
                                tileBitmapPaint,
                                screenCompX = compX,
                                screenCompY = compY,
                                clipBbox = regionClipRef.value?.takeIf { MapCameraMath.bboxIsValid(it) },
                            )
                        }
                    }

                    // POI·경로 — 뷰포트 오버레이(회전 레이어 밖). 타일만 회전하고 핀은 화면 좌표에 고정.
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { clip = false },
                    ) {
                        val vw = size.width.toFloat().coerceAtLeast(1f)
                        val vh = size.height.toFloat().coerceAtLeast(1f)
                        val cam = cameraRef.value
                        val rot = layerRotationDeg
                        val pool = activeZoomPool()
                        val anchorLat = when {
                            user != null && (headingUp || gpsLocked) -> user.lat
                            else -> cam.centerLat
                        }
                        val z = MapCameraMath.pickRenderZoom(anchorLat, vfW, cam.visibleSpanM, pool)
                        val renderCam = buildRenderCamera(cam, user, headingUp, gpsLocked, z)
                        val gpsAnchor = gpsLocked && user != null
                        val nc = drawContext.canvas.nativeCanvas

                        if (routePoints.size >= 2) {
                            val routePath = android.graphics.Path()
                            routePoints.forEachIndexed { idx, (lat, lon) ->
                                val (px, py) = viewportMapPoint(
                                    lat, lon, renderCam, vw, vh, gpsAnchor, user, headingUp, rot,
                                )
                                if (idx == 0) routePath.moveTo(px, py) else routePath.lineTo(px, py)
                            }
                            nc.drawPath(routePath, routeGlowPaint)
                            nc.drawPath(routePath, routeLinePaint)
                        }

                        if (!gpsLocked) {
                            userRadiusRef.value?.takeIf { it > 0.0 }?.let { radiusKm ->
                                val center = user?.let { u ->
                                    val (px, py) = viewportMapPoint(
                                        u.lat, u.lon, renderCam, vw, vh, gpsAnchor, user, headingUp, rot,
                                    )
                                    Offset(px, py)
                                } ?: Offset(vw / 2f, vh / 2f)
                                val rPx = metersToScreenRadius(renderCam.visibleSpanM, vw, radiusKm * 1000.0)
                                drawCircle(
                                    Color(0x552563EB),
                                    radius = rPx,
                                    center = center,
                                    style = Stroke(width = 2.5f),
                                )
                                drawCircle(
                                    Color(0x3322C55E),
                                    radius = rPx,
                                    center = center,
                                    style = Stroke(width = 1f),
                                )
                            }
                        }

                        pois.forEach { p ->
                            val (px, py) = viewportMapPoint(
                                p.lat, p.lon, renderCam, vw, vh, gpsAnchor, user, headingUp, rot,
                            )
                            if (px < -48f || py < -48f || px > vw + 48f || py > vh + 48f) return@forEach
                            val color = PoiColors.accent(p)
                            val selected = p.id == highlightedPoiId
                            val radius = if (selected) 14f else 5f
                            if (selected) {
                                drawCircle(Color(0x99F59E0B), radius = 26f, center = Offset(px, py))
                                drawCircle(Color(0xFFF59E0B), radius = 20f, center = Offset(px, py))
                            }
                            drawCircle(color, radius = radius, center = Offset(px, py))
                            drawCircle(
                                Color.White,
                                radius = if (selected) 5f else 2f,
                                center = Offset(px, py),
                            )
                            if (!selected && pois.size <= MAX_POI_LABELS_ON_MAP) {
                                val rating = PoiLogic.formatRating(PoiLogic.displayRating(p))
                                nc.drawText("★$rating", px - 10f, py + 14f, poiLabelPaint)
                            }
                        }

                        if (!gpsLocked) {
                            user?.let { u ->
                                val (px, py) = viewportMapPoint(
                                    u.lat, u.lon, renderCam, vw, vh, false, user, headingUp, rot,
                                )
                                drawCircle(Color(0x5922C55E), radius = 14f, center = Offset(px, py))
                                drawCircle(Color(0xFF22C55E), radius = 7f, center = Offset(px, py))
                                u.bearingDeg?.let { b ->
                                    val rad = Math.toRadians(b.toDouble())
                                    val tipX = px + sin(rad).toFloat() * 22f
                                    val tipY = py - cos(rad).toFloat() * 22f
                                    drawLine(
                                        Color(0xFF166534),
                                        Offset(px, py),
                                        Offset(tipX, tipY),
                                        strokeWidth = 5f,
                                    )
                                }
                            }
                        }
                    }

                    if (gpsLocked && user != null) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { clip = false },
                    ) {
                        val pivot = Offset(size.width / 2f, size.height / 2f)
                        userRadiusRef.value?.takeIf { it > 0.0 }?.let { radiusKm ->
                            val cam = cameraRef.value
                            val wf = size.width.toFloat().coerceAtLeast(1f)
                            val rPx = metersToScreenRadius(cam.visibleSpanM, wf, radiusKm * 1000.0)
                            drawCircle(
                                Color(0x552563EB),
                                radius = rPx,
                                center = pivot,
                                style = Stroke(width = 2.5f),
                            )
                            drawCircle(
                                Color(0x3322C55E),
                                radius = rPx,
                                center = pivot,
                                style = Stroke(width = 1f),
                            )
                        }
                        drawCircle(Color(0x5922C55E), radius = 14f, center = pivot)
                        drawCircle(Color(0xFF22C55E), radius = 7f, center = pivot)
                        val fwdDeg = if (headingUp) 0f else (targetBearingRef.value ?: smoothBearingDeg)
                        drawForwardChevron(
                            pivot = pivot,
                            bearingDeg = fwdDeg,
                            color = Color(0xFF22C55E),
                        )
                    }
                }
                }
            }

            val mapW = screenSize.width.toFloat().coerceAtLeast(1f)
            val pool = activeZoomPool()
            val renderZ = MapCameraMath.pickRenderZoom(camera.centerLat, mapW, camera.visibleSpanM, pool)
            val mpp = camera.visibleSpanM / mapW
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
                            "z$renderZ · ${MapCameraMath.spanLabel(camera.visibleSpanM)} · ${smoothBearingDeg.toInt()}°",
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_TILES_NORTH = 14
/** 헤딩 회전용 정사각형 오버스캔 캔버스 한 변 상한(px) */
private const val HEADING_CANVAS_MAX = 2720
private const val MAX_POI_LABELS_ON_MAP = 32
/** pinch 노이즈 무시 — log(zoom) 이 값 미만이면 확대/축소로 보지 않음 */
private const val MAP_ZOOM_LOG_THRESHOLD = 0.028

/** 회전된 헤딩업에서 손가락 드래그 방향 = 지도 이동 방향 */
private fun screenDragToMapPan(dx: Float, dy: Float, mapRotationDeg: Float): Pair<Float, Float> =
    MapCameraMath.rotateScreenOffset(dx, dy, mapRotationDeg)

private val poiLabelPaint = android.graphics.Paint().apply {
    isAntiAlias = true
    textSize = 20f
    color = android.graphics.Color.argb(230, 30, 41, 59)
}

private val tileBitmapPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

private val routeLinePaint = android.graphics.Paint().apply {
    isAntiAlias = true
    color = android.graphics.Color.parseColor("#2563EB")
    strokeWidth = 6f
    style = android.graphics.Paint.Style.STROKE
    strokeCap = android.graphics.Paint.Cap.ROUND
}

private val routeGlowPaint = android.graphics.Paint(routeLinePaint).apply {
    color = android.graphics.Color.parseColor("#CC93C5FD")
    strokeWidth = 10f
}

private fun headingCoverMargin(w: Float, h: Float, rotDeg: Float): Float {
    if (abs(rotDeg) < 0.5f) return 1f
    val rad = Math.toRadians(abs(rotDeg).toDouble())
    val s = sin(rad).toFloat()
    val c = cos(rad).toFloat()
    val rotW = w * c + h * s
    val rotH = w * s + h * c
    return maxOf(rotW / w.coerceAtLeast(1f), rotH / h.coerceAtLeast(1f)) * 1.14f
}

/** GPS 핀 앞쪽 — 초록 chevron(∧), 원 밖으로 배치 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawForwardChevron(
    pivot: Offset,
    bearingDeg: Float,
    color: Color,
    length: Float = 17f,
    outset: Float = 11f,
) {
    val rad = Math.toRadians(bearingDeg.toDouble())
    val base = Offset(
        pivot.x + sin(rad).toFloat() * outset,
        pivot.y - cos(rad).toFloat() * outset,
    )
    val tip = Offset(
        base.x + sin(rad).toFloat() * length,
        base.y - cos(rad).toFloat() * length,
    )
    val wing = length * 0.58f
    val spread = 0.88f
    val left = Offset(
        base.x + sin(rad - spread).toFloat() * wing,
        base.y - cos(rad - spread).toFloat() * wing,
    )
    val right = Offset(
        base.x + sin(rad + spread).toFloat() * wing,
        base.y - cos(rad + spread).toFloat() * wing,
    )
    val path = Path().apply {
        moveTo(left.x, left.y)
        lineTo(tip.x, tip.y)
        lineTo(right.x, right.y)
    }
    drawPath(
        path,
        color,
        style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** 뷰포트 좌표 — 헤딩업 시 지도 회전각만큼 화면 좌표로 변환 (오버레이 POI·경로) */
private fun viewportMapPoint(
    lat: Double,
    lon: Double,
    camera: MapCamera,
    viewportW: Float,
    viewportH: Float,
    gpsLocked: Boolean,
    user: UserPosition?,
    headingUp: Boolean,
    mapRotationDeg: Float,
): Pair<Float, Float> {
    val (nx, ny) = if (gpsLocked && user != null) {
        MapCameraMath.screenFromLatLonGpsLocked(
            lat, lon, camera, viewportW, viewportH, user.lat, user.lon,
        )
    } else {
        MapCameraMath.screenFromLatLon(lat, lon, camera, viewportW, viewportH)
    }
    if (headingUp && abs(mapRotationDeg) > 0.5f) {
        return MapCameraMath.mapPointToScreen(
            nx, ny, viewportW / 2f, viewportH / 2f, mapRotationDeg,
        )
    }
    return nx to ny
}

private fun expandTileRange(range: IntRange, pad: Int): IntRange {
    if (pad <= 0 || range.isEmpty()) return range
    return (range.first - pad).coerceAtLeast(0)..(range.last + pad)
}

private fun buildRenderCamera(
    cam: MapCamera,
    user: UserPosition?,
    headingUp: Boolean,
    gpsLocked: Boolean,
    z: Int,
): MapCamera {
    val northUp = when {
        user != null && (headingUp || gpsLocked) ->
            cam.copy(centerLat = user.lat, centerLon = user.lon, bearingDeg = 0f)
        headingUp -> cam.copy(bearingDeg = 0f)
        else -> cam
    }
    return if (z == northUp.baseZoom) northUp else northUp.copy(baseZoom = z)
}

private fun formatRouteDistance(m: Int): String =
    if (m >= 1000) "%.1fkm".format(m / 1000.0) else "${m}m"
