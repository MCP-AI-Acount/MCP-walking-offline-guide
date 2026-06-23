package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.UserPosition
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.math.PI

/** 화면 가로 폭에 해당하는 실제 거리(미터)로 줌 제어 */
data class MapCamera(
    val centerLat: Double,
    val centerLon: Double,
    val baseZoom: Int,
    val visibleSpanM: Double,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** 위에서 본 지도 회전(도) — 0=북쪽 위 */
    val bearingDeg: Float = 0f,
) {
    fun withPan(dx: Float, dy: Float) = copy(panX = panX + dx, panY = panY + dy)

    fun withSpan(spanM: Double) = copy(visibleSpanM = spanM)

    fun withBearing(deg: Float) = copy(bearingDeg = ((deg % 360f) + 360f) % 360f)

    fun recenter(lat: Double, lon: Double) = copy(centerLat = lat, centerLon = lon, panX = 0f, panY = 0f)

    /** 헤딩업 — GPS 중심 갱신, pan 유지 (드래그 탐색) */
    fun updateAnchor(lat: Double, lon: Double) =
        copy(centerLat = lat, centerLon = lon, bearingDeg = 0f)

    /** 헤딩업 진입·리센터 — pan 초기화 */
    fun lockAnchor(lat: Double, lon: Double) =
        copy(centerLat = lat, centerLon = lon, panX = 0f, panY = 0f, bearingDeg = 0f)
}

object MapCameraMath {
    const val SPAN_MIN_M = 500.0
    /** GPS 해제·지역 탐색 */
    const val SPAN_MAX_M = 5000.0
    const val SPAN_DEFAULT_M = 1500.0
    /** 헤딩업(GPS 고정) — 최대 확대 200m · 최대 축소 2km */
    const val SPAN_HEADING_MIN_M = 200.0
    const val SPAN_HEADING_DEFAULT_M = 1000.0
    const val SPAN_HEADING_MAX_M = 2000.0
    /** OSM 타일 논리 크기(px). @2x 래스터도 동일. */
    const val TILE_LOGICAL_PX = 256.0

    private fun worldSize(zoom: Int): Double = TILE_LOGICAL_PX * 2.0.pow(zoom)

    fun latLonToWorldPx(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val scale = worldSize(zoom)
        val x = (lon + 180.0) / 360.0 * scale
        val latRad = Math.toRadians(lat)
        val y = (1.0 - asinh(tan(latRad)) / PI) / 2.0 * scale
        return x to y
    }

    fun scaleFactor(lat: Double, baseZoom: Int, screenW: Float, visibleSpanM: Double): Float {
        val naturalSpan = metersPerPixel(lat, baseZoom) * screenW
        return (naturalSpan / visibleSpanM.coerceAtLeast(1.0)).toFloat()
    }

    fun screenFromLatLon(
        lat: Double,
        lon: Double,
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
    ): Pair<Float, Float> {
        val z = camera.baseZoom
        val scale = scaleFactor(camera.centerLat, z, screenW, camera.visibleSpanM)
        val (wx, wy) = latLonToWorldPx(lat, lon, z)
        val (cx, cy) = latLonToWorldPx(camera.centerLat, camera.centerLon, z)
        var dx = ((wx - cx) * scale + camera.panX).toFloat()
        var dy = ((wy - cy) * scale + camera.panY).toFloat()
        if (camera.bearingDeg != 0f) {
            val rad = Math.toRadians(camera.bearingDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            dx = rx
            dy = ry
        }
        return (screenW / 2 + dx) to (screenH / 2 + dy)
    }

    /** GPS 잠금 — pan 후에도 gps 좌표가 화면 정중앙에 오도록 보정량 */
    fun gpsLockScreenCompensation(
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
        gpsLat: Double,
        gpsLon: Double,
    ): Pair<Float, Float> {
        val pivotX = screenW / 2f
        val pivotY = screenH / 2f
        val (ux, uy) = screenFromLatLon(gpsLat, gpsLon, camera, screenW, screenH)
        return (pivotX - ux) to (pivotY - uy)
    }

    fun screenFromLatLonGpsLocked(
        lat: Double,
        lon: Double,
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
        gpsLat: Double,
        gpsLon: Double,
    ): Pair<Float, Float> {
        val (px, py) = screenFromLatLon(lat, lon, camera, screenW, screenH)
        val (cx, cy) = gpsLockScreenCompensation(camera, screenW, screenH, gpsLat, gpsLon)
        return (px + cx) to (py + cy)
    }

    /**
     * 오버스캔 캔버스 — mpp·줌은 viewport 기준, 픽셀 좌표는 canvas 중심 기준.
     * (회전용 큰 캔버스에 그릴 때 지도 배율이 줄지 않도록 분리)
     */
    fun screenFromLatLonOverscan(
        lat: Double,
        lon: Double,
        camera: MapCamera,
        viewportW: Float,
        viewportH: Float,
        canvasW: Float,
        canvasH: Float,
    ): Pair<Float, Float> {
        val z = camera.baseZoom
        val scale = scaleFactor(camera.centerLat, z, viewportW, camera.visibleSpanM)
        val (wx, wy) = latLonToWorldPx(lat, lon, z)
        val (cx, cy) = latLonToWorldPx(camera.centerLat, camera.centerLon, z)
        var dx = ((wx - cx) * scale + camera.panX).toFloat()
        var dy = ((wy - cy) * scale + camera.panY).toFloat()
        if (camera.bearingDeg != 0f) {
            val rad = Math.toRadians(camera.bearingDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            dx = rx
            dy = ry
        }
        return (canvasW / 2f + dx) to (canvasH / 2f + dy)
    }

    fun gpsLockScreenCompensationOverscan(
        camera: MapCamera,
        viewportW: Float,
        viewportH: Float,
        canvasW: Float,
        canvasH: Float,
        gpsLat: Double,
        gpsLon: Double,
    ): Pair<Float, Float> {
        val (ux, uy) = screenFromLatLonOverscan(
            gpsLat, gpsLon, camera, viewportW, viewportH, canvasW, canvasH,
        )
        return (canvasW / 2f - ux) to (canvasH / 2f - uy)
    }

    fun screenFromLatLonGpsLockedOverscan(
        lat: Double,
        lon: Double,
        camera: MapCamera,
        viewportW: Float,
        viewportH: Float,
        canvasW: Float,
        canvasH: Float,
        gpsLat: Double,
        gpsLon: Double,
    ): Pair<Float, Float> {
        val (px, py) = screenFromLatLonOverscan(
            lat, lon, camera, viewportW, viewportH, canvasW, canvasH,
        )
        val (cx, cy) = gpsLockScreenCompensationOverscan(
            camera, viewportW, viewportH, canvasW, canvasH, gpsLat, gpsLon,
        )
        return (px + cx) to (py + cy)
    }

    fun screenToUnrotatedOffset(
        screenX: Float,
        screenY: Float,
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
    ): Pair<Float, Float> {
        var dx = screenX - screenW / 2f
        var dy = screenY - screenH / 2f
        if (camera.bearingDeg != 0f) {
            val rad = Math.toRadians(-camera.bearingDeg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val rx = dx * c - dy * s
            val ry = dx * s + dy * c
            dx = rx
            dy = ry
        }
        return dx - camera.panX to dy - camera.panY
    }

    fun rotatePan(dx: Float, dy: Float, bearingDeg: Float): Pair<Float, Float> =
        rotateScreenOffset(dx, dy, -bearingDeg)

    /** 화면 좌표(y↓) 기준 pivot 주위 시계방향 bearingDeg — 헤딩업 지도 회전 */
    fun rotateScreenOffset(dx: Float, dy: Float, bearingDeg: Float): Pair<Float, Float> {
        if (bearingDeg == 0f) return dx to dy
        val rad = Math.toRadians(bearingDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return (dx * c - dy * s) to (dx * s + dy * c)
    }

    /** 북쪽 위 map 좌표 → pivot 주위 bearing 만큼 회전한 화면 좌표 */
    fun mapPointToScreen(
        mapX: Float,
        mapY: Float,
        pivotX: Float,
        pivotY: Float,
        bearingDeg: Float,
    ): Pair<Float, Float> {
        val (rx, ry) = rotateScreenOffset(mapX - pivotX, mapY - pivotY, bearingDeg)
        return (pivotX + rx) to (pivotY + ry)
    }

    fun worldPxToLatLon(wx: Double, wy: Double, zoom: Int): Pair<Double, Double> {
        val scaleWorld = worldSize(zoom)
        val lon = wx / scaleWorld * 360.0 - 180.0
        val latRad = Math.atan(Math.sinh(PI * (1.0 - 2.0 * wy / scaleWorld)))
        return Math.toDegrees(latRad) to lon
    }

    fun screenToLatLon(
        screenX: Float,
        screenY: Float,
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
    ): Pair<Double, Double> {
        val (offX, offY) = screenToUnrotatedOffset(screenX, screenY, camera, screenW, screenH)
        val z = camera.baseZoom
        val scale = scaleFactor(camera.centerLat, z, screenW, camera.visibleSpanM)
        if (scale == 0f) return camera.centerLat to camera.centerLon
        val (cx, cy) = latLonToWorldPx(camera.centerLat, camera.centerLon, z)
        val wx = offX / scale + cx
        val wy = offY / scale + cy
        return worldPxToLatLon(wx, wy, z)
    }

    fun visibleGeoBounds(camera: MapCamera, screenW: Float, screenH: Float): Bbox {
        val corners = listOf(
            screenToLatLon(0f, 0f, camera, screenW, screenH),
            screenToLatLon(screenW, 0f, camera, screenW, screenH),
            screenToLatLon(0f, screenH, camera, screenW, screenH),
            screenToLatLon(screenW, screenH, camera, screenW, screenH),
        )
        return Bbox(
            south = corners.minOf { it.first },
            north = corners.maxOf { it.first },
            west = corners.minOf { it.second },
            east = corners.maxOf { it.second },
        )
    }

    fun visibleTileRange(camera: MapCamera, screenW: Float, screenH: Float): Pair<IntRange, IntRange> {
        val z = camera.baseZoom
        val n = 2.0.pow(z).toInt()
        val bounds = visibleGeoBounds(camera, screenW, screenH)
        return tileRangeFromGeoBounds(bounds, z, n)
    }

    /**
     * 헤딩업 — 화면 코너를 북쪽위 좌표계로 되돌려 회전·줌에 맞는 타일 범위 산출.
     * @param mapRotationDeg Canvas rotate 각 (MapHeading.mapRotationDeg)
     */
    fun visibleTileRangeHeadingUp(
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
        mapRotationDeg: Float,
    ): Pair<IntRange, IntRange> {
        if (kotlin.math.abs(mapRotationDeg) < 0.5f) {
            return visibleTileRange(camera, screenW, screenH)
        }
        val z = camera.baseZoom
        val n = 2.0.pow(z).toInt()
        val px = screenW / 2f
        val py = screenH / 2f
        val samples = buildList {
            add(0f to 0f)
            add(screenW to 0f)
            add(0f to screenH)
            add(screenW to screenH)
            add(px to 0f)
            add(screenW to py)
            add(px to screenH)
            add(0f to py)
            val dx = screenW * 0.48f
            val dy = screenH * 0.48f
            add(px - dx to py - dy)
            add(px + dx to py - dy)
            add(px - dx to py + dy)
            add(px + dx to py + dy)
        }
        var south = Double.MAX_VALUE
        var north = -Double.MAX_VALUE
        var west = Double.MAX_VALUE
        var east = -Double.MAX_VALUE
        samples.forEach { (sx, sy) ->
            val (ux, uy) = rotateScreenOffset(sx - px, sy - py, -mapRotationDeg)
            val (lat, lon) = screenToLatLon(px + ux, py + uy, camera, screenW, screenH)
            south = minOf(south, lat)
            north = maxOf(north, lat)
            west = minOf(west, lon)
            east = maxOf(east, lon)
        }
        return tileRangeFromGeoBounds(Bbox(south, north, west, east), z, n)
    }

    /** 터치 좌표 → 북쪽위(회전 전) 화면 좌표 — 헤딩업 POI 히트테스트 */
    fun screenToNorthUp(
        screenX: Float,
        screenY: Float,
        pivotX: Float,
        pivotY: Float,
        mapRotationDeg: Float,
    ): Pair<Float, Float> {
        if (kotlin.math.abs(mapRotationDeg) < 0.5f) return screenX to screenY
        val (ux, uy) = rotateScreenOffset(screenX - pivotX, screenY - pivotY, -mapRotationDeg)
        return (pivotX + ux) to (pivotY + uy)
    }

    /** draw 루프 타일 수 상한 — 회전·줌 오차로 범위 폭주 시 ANR 방지 */
    fun clampTileIndexRange(range: IntRange, maxTiles: Int): IntRange {
        if (range.isEmpty()) return range
        val count = range.last - range.first + 1
        if (count <= maxTiles) return range
        val mid = (range.first + range.last) / 2
        val half = maxTiles / 2
        val first = (mid - half).coerceAtLeast(range.first)
        val last = (first + maxTiles - 1).coerceAtMost(range.last)
        return first..last
    }

    /** 헤딩업 — 화면 중심(카메라) 타일 기준으로 clamp (회전 시 한쪽 타일만 남는 현상 방지) */
    fun clampTileIndexRangeAroundCenter(
        range: IntRange,
        maxTiles: Int,
        centerTile: Int,
    ): IntRange {
        if (range.isEmpty()) return range
        val count = range.last - range.first + 1
        if (count <= maxTiles) return range
        val half = maxTiles / 2
        val maxFirst = (range.last - maxTiles + 1).coerceAtLeast(range.first)
        val first = (centerTile - half).coerceIn(range.first, maxFirst)
        return first..(first + maxTiles - 1).coerceAtMost(range.last)
    }

    fun bboxIsValid(bbox: Bbox): Boolean =
        bbox.north > bbox.south && bbox.east > bbox.west

    fun tileIntersectsBbox(x: Int, y: Int, z: Int, bbox: Bbox): Boolean {
        val n = 2.0.pow(z).toInt().coerceAtLeast(1)
        if (x !in 0 until n || y !in 0 until n) return false
        val tb = MapMath.tileBounds(x, y, z)
        return tb.north >= bbox.south && tb.south <= bbox.north &&
            tb.east >= bbox.west && tb.west <= bbox.east
    }

    fun tileIndexRangeForBbox(bbox: Bbox, z: Int): Pair<IntRange, IntRange> {
        val maxIdx = 2.0.pow(z).toInt().coerceAtLeast(1) - 1
        val xr = MapMath.tileXRange(bbox, z)
        val yr = MapMath.tileYRange(bbox, z)
        return (xr.first.coerceIn(0, maxIdx)..xr.last.coerceIn(0, maxIdx)) to
            (yr.first.coerceIn(0, maxIdx)..yr.last.coerceIn(0, maxIdx))
    }

    fun intersectTileRanges(
        a: Pair<IntRange, IntRange>,
        b: Pair<IntRange, IntRange>,
    ): Pair<IntRange, IntRange>? {
        val x0 = maxOf(a.first.first, b.first.first)
        val x1 = minOf(a.first.last, b.first.last)
        val y0 = maxOf(a.second.first, b.second.first)
        val y1 = minOf(a.second.last, b.second.last)
        if (x0 > x1 || y0 > y1) return null
        return (x0..x1) to (y0..y1)
    }

    /** 뷰포트·그리드 타일 범위를 행정지역 bbox 안으로 제한 */
    fun clipTileRangesToBbox(
        xRange: IntRange,
        yRange: IntRange,
        z: Int,
        bbox: Bbox,
    ): Pair<IntRange, IntRange>? {
        if (!bboxIsValid(bbox)) return xRange to yRange
        return intersectTileRanges(xRange to yRange, tileIndexRangeForBbox(bbox, z))
    }

    fun tileIndexAt(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom).toInt()
        val (wx, wy) = latLonToWorldPx(lat, lon, zoom)
        val tx = floor(wx / TILE_LOGICAL_PX).toInt().coerceIn(0, n - 1)
        val ty = floor(wy / TILE_LOGICAL_PX).toInt().coerceIn(0, n - 1)
        return tx to ty
    }

    /** 중심 타일 기준 N×N 정사각 그리드 (5/7/9/11) */
    fun squareTileRangeAround(
        centerTx: Int,
        centerTy: Int,
        gridSide: Int,
        zoom: Int,
    ): Pair<IntRange, IntRange> {
        val side = gridSide.coerceAtLeast(3)
        val maxIdx = 2.0.pow(zoom).toInt().coerceAtLeast(1) - 1
        return centeredTileIndexRange(centerTx, side, maxIdx) to
            centeredTileIndexRange(centerTy, side, maxIdx)
    }

    private fun centeredTileIndexRange(center: Int, gridSide: Int, maxIdx: Int): IntRange {
        val half = gridSide / 2
        var first = center - half
        var last = first + gridSide - 1
        if (first < 0) {
            last += -first
            first = 0
        }
        if (last > maxIdx) {
            first -= (last - maxIdx)
            last = maxIdx
        }
        first = first.coerceAtLeast(0)
        if (last - first + 1 < gridSide) {
            last = (first + gridSide - 1).coerceAtMost(maxIdx)
        }
        return first..last
    }

    fun pickHeadingSquareGridSide(
        viewportW: Float,
        viewportH: Float,
        visibleSpanM: Double,
        centerLat: Double,
        zoom: Int,
        headingMargin: Float,
    ): Int {
        val vfW = viewportW.coerceAtLeast(1f)
        val vfH = viewportH.coerceAtLeast(1f)
        val margin = headingMargin.coerceAtLeast(1f)
        val scale = scaleFactor(centerLat, zoom, vfW, visibleSpanM)
        val tilePx = (TILE_LOGICAL_PX * scale).toFloat().coerceAtLeast(1f)
        // 헤딩 최대 2km — 멀리 볼수록 타일 px↓ → 그리드 확대 (회전·오버스캔 여유)
        val spanRatioMax = (SPAN_HEADING_MAX_M / SPAN_HEADING_MIN_M).toFloat()
        val farSpanBoost = (visibleSpanM / SPAN_HEADING_MIN_M).toFloat().coerceIn(1f, spanRatioMax)
        val zoomOutFactor = 1f + (farSpanBoost - 1f) * 0.04f
        val coverPx = maxOf(vfW, vfH) * margin * 1.14f * zoomOutFactor
        val needed = kotlin.math.ceil(coverPx / tilePx).toInt() + 2
        return when {
            needed <= 5 -> 5
            needed <= 7 -> 7
            needed <= 9 -> 9
            needed <= 11 -> 11
            needed <= 13 -> 13
            needed <= 15 -> 15
            else -> 17
        }
    }

    private fun tileRangeFromGeoBounds(bounds: Bbox, z: Int, n: Int): Pair<IntRange, IntRange> {
        val corners = listOf(
            bounds.south to bounds.west,
            bounds.south to bounds.east,
            bounds.north to bounds.west,
            bounds.north to bounds.east,
        )
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        corners.forEach { (lat, lon) ->
            val (wx, wy) = latLonToWorldPx(lat, lon, z)
            val tx = floor(wx / TILE_LOGICAL_PX).toInt()
            val ty = floor(wy / TILE_LOGICAL_PX).toInt()
            x0 = minOf(x0, tx)
            y0 = minOf(y0, ty)
            x1 = maxOf(x1, tx)
            y1 = maxOf(y1, ty)
        }
        return (x0.coerceIn(0, n - 1)..x1.coerceIn(0, n - 1)) to
            (y0.coerceIn(0, n - 1)..y1.coerceIn(0, n - 1))
    }

    fun metersPerPixel(lat: Double, zoom: Int): Double {
        val equator = 2 * PI * 6_378_137.0
        return equator * cos(Math.toRadians(lat)) / worldSize(zoom)
    }

    fun scaleBarMeters(mpp: Double, maxPx: Float = 120f): Pair<Int, Float> {
        val nice = listOf(2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000)
        for (m in nice) {
            val px = (m / mpp).toFloat()
            if (px in 40f..maxPx) return m to px
        }
        val fallback = nice.firstOrNull { (it / mpp) <= maxPx } ?: 1000
        return fallback to (fallback / mpp).toFloat().coerceIn(40f, maxPx)
    }

    fun clampSpan(spanM: Double): Double = spanM.coerceIn(SPAN_MIN_M, SPAN_MAX_M)

    fun clampSpanHeading(spanM: Double): Double =
        spanM.coerceIn(SPAN_HEADING_MIN_M, SPAN_HEADING_MAX_M)

    fun minSpanM(heading: Boolean): Double =
        if (heading) SPAN_HEADING_MIN_M else SPAN_MIN_M

    fun maxSpanM(heading: Boolean): Double =
        if (heading) SPAN_HEADING_MAX_M else SPAN_MAX_M

    /** 해당 줌에서 업스케일 없이 보여줄 수 있는 최소 화면 span(미터) */
    fun minSpanForZoom(lat: Double, zoom: Int, screenW: Float): Double =
        metersPerPixel(lat, zoom) * screenW

    /** maxZoom에서 과도한 업스케일 방지 — pinch-in 하한 (미사용: overzoom 허용) */
    @Deprecated("Use clampSpan only — overzoom + parent-tile fallback")
    fun clampSpanForMaxZoom(lat: Double, spanM: Double, maxZoom: Int, screenW: Float): Double =
        clampSpan(spanM)

    fun pickBaseZoom(available: Set<Int>, prefer: List<Int>): Int =
        prefer.firstOrNull { it in available } ?: available.maxOrNull() ?: 14

    /**
     * 화면 span에 맞는 타일 줌 — m/px 로그 거리 최소화.
     * 5km(멀리)에서 z10 업스케일 블러 · 500m(가까이) 선명도를 span 구간 전체에서 균형.
     */
    fun pickRenderZoom(lat: Double, screenW: Float, visibleSpanM: Double, available: Set<Int>): Int {
        if (available.isEmpty()) return 13
        val w = screenW.coerceAtLeast(1f)
        val targetMpp = visibleSpanM.coerceAtLeast(SPAN_MIN_M) / w
        return available.minByOrNull { z ->
            val nativeMpp = metersPerPixel(lat, z).coerceAtLeast(1e-9)
            kotlin.math.abs(kotlin.math.ln(nativeMpp / targetMpp))
        } ?: available.maxOrNull() ?: 13
    }

    /** pinch span + 해당 줌을 한 번에 맞춤 */
    fun cameraWithSpan(
        camera: MapCamera,
        spanM: Double,
        screenW: Float,
        available: Set<Int>,
        heading: Boolean = false,
    ): MapCamera {
        val span = if (heading) clampSpanHeading(spanM) else clampSpan(spanM)
        val z = pickRenderZoom(camera.centerLat, screenW, span, available)
        return camera.copy(visibleSpanM = span, baseZoom = z)
    }

    fun defaultCamera(
        pos: UserPosition?,
        regionBbox: Bbox,
        availableZooms: Set<Int>,
        centerOnPos: Boolean = false,
        screenW: Float = 720f,
        heading: Boolean = false,
    ): MapCamera {
        val usePos = centerOnPos && pos != null
        val lat = if (usePos) pos!!.lat else (regionBbox.north + regionBbox.south) / 2
        val lon = if (usePos) pos!!.lon else (regionBbox.east + regionBbox.west) / 2
        val span = if (heading) SPAN_HEADING_DEFAULT_M else SPAN_DEFAULT_M
        val z = pickRenderZoom(lat, screenW, span, availableZooms)
        return MapCamera(lat, lon, z, span)
    }

    fun cameraAt(
        lat: Double,
        lon: Double,
        availableZooms: Set<Int>,
        spanM: Double = SPAN_DEFAULT_M,
        screenW: Float = 720f,
    ): MapCamera {
        val span = clampSpan(spanM)
        val z = pickRenderZoom(lat, screenW, span, availableZooms)
        return MapCamera(lat, lon, z, span)
    }

    fun spanLabel(spanM: Double): String =
        if (spanM >= 1000) "%.1fkm".format(spanM / 1000) else "${spanM.toInt()}m"

    /** 각도 보간 — 359°↔0° 짧은 경로 (지도 회전 스무딩) */
    fun lerpAngleDegrees(from: Float, to: Float, fraction: Float): Float {
        var diff = to - from
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        var r = from + diff * fraction
        while (r < 0f) r += 360f
        while (r >= 360f) r -= 360f
        return r
    }

    /** dt(초) 기준 지수 스무딩 — Google Maps류 map rotation */
    fun smoothAngleDt(current: Float, target: Float, dtSec: Float, timeConstantSec: Float = 0.12f): Float {
        val alpha = (1.0 - kotlin.math.exp(-dtSec / timeConstantSec.toDouble())).toFloat()
            .coerceIn(0.04f, 0.4f)
        return lerpAngleDegrees(current, target, alpha)
    }

    /** 화면 pan 오프셋을 중심 좌표에 반영해 pan을 0으로 초기화 */
    fun bakePan(camera: MapCamera, screenW: Float, screenH: Float): MapCamera {
        if (camera.panX == 0f && camera.panY == 0f) return camera
        val (lat, lon) = screenToLatLon(screenW / 2f, screenH / 2f, camera, screenW, screenH)
        return camera.copy(centerLat = lat, centerLon = lon, panX = 0f, panY = 0f)
    }
}
