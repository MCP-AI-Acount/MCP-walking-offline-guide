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
}

object MapCameraMath {
    const val SPAN_MIN_M = 1000.0
    const val SPAN_MAX_M = 5000.0
    const val SPAN_DEFAULT_M = 3000.0
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

    fun rotatePan(dx: Float, dy: Float, bearingDeg: Float): Pair<Float, Float> {
        if (bearingDeg == 0f) return dx to dy
        val rad = Math.toRadians(-bearingDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return (dx * c - dy * s) to (dx * s + dy * c)
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
        val nice = listOf(5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000)
        for (m in nice) {
            val px = (m / mpp).toFloat()
            if (px in 40f..maxPx) return m to px
        }
        return 1000 to (1000 / mpp).toFloat()
    }

    fun clampSpan(spanM: Double): Double = spanM.coerceIn(SPAN_MIN_M, SPAN_MAX_M)

    fun pickBaseZoom(available: Set<Int>, prefer: List<Int>): Int =
        prefer.firstOrNull { it in available } ?: available.maxOrNull() ?: 13

    fun defaultCamera(
        pos: UserPosition?,
        regionBbox: Bbox,
        availableZooms: Set<Int>,
    ): MapCamera {
        val lat = pos?.lat?.takeIf { PoiLogic.inBbox(it, pos.lon, regionBbox) }
            ?: (regionBbox.north + regionBbox.south) / 2
        val lon = pos?.lon?.takeIf { PoiLogic.inBbox(pos.lat, it, regionBbox) }
            ?: (regionBbox.east + regionBbox.west) / 2
        val z = pickBaseZoom(availableZooms, listOf(13, 12, 11, 10, 9))
        return MapCamera(lat, lon, z, SPAN_DEFAULT_M)
    }

    fun spanLabel(spanM: Double): String =
        if (spanM >= 1000) "%.1fkm".format(spanM / 1000) else "${spanM.toInt()}m"

    /** 화면 pan 오프셋을 중심 좌표에 반영해 pan을 0으로 초기화 */
    fun bakePan(camera: MapCamera, screenW: Float, screenH: Float): MapCamera {
        if (camera.panX == 0f && camera.panY == 0f) return camera
        val (lat, lon) = screenToLatLon(screenW / 2f, screenH / 2f, camera, screenW, screenH)
        return camera.copy(centerLat = lat, centerLon = lon, panX = 0f, panY = 0f)
    }
}
