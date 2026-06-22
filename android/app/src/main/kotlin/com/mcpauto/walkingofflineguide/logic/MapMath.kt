package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.UserPosition
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.math.PI

data class MapViewport(
    val regionId: String,
    val bbox: Bbox,
    val zoom: Int,
    val label: String,
)

object MapMath {
    fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = 2.0.pow(zoom)
        val x = ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n.toInt() - 1)
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n)
            .toInt().coerceIn(0, n.toInt() - 1)
        return x to y
    }

    fun tileBounds(x: Int, y: Int, zoom: Int): Bbox {
        val n = 2.0.pow(zoom)
        val west = x / n * 360.0 - 180.0
        val east = (x + 1) / n * 360.0 - 180.0
        val north = tileYToLat(y, zoom)
        val south = tileYToLat(y + 1, zoom)
        return Bbox(south = south, west = west, north = north, east = east)
    }

    private fun tileYToLat(y: Int, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
        return Math.toDegrees(latRad)
    }

    fun tileXRange(bbox: Bbox, zoom: Int): IntRange {
        val (x0, _) = latLonToTile(bbox.north, bbox.west, zoom)
        val (x1, _) = latLonToTile(bbox.south, bbox.east, zoom)
        return minOf(x0, x1)..maxOf(x0, x1)
    }

    fun tileYRange(bbox: Bbox, zoom: Int): IntRange {
        val (_, y0) = latLonToTile(bbox.north, bbox.west, zoom)
        val (_, y1) = latLonToTile(bbox.south, bbox.east, zoom)
        return minOf(y0, y1)..maxOf(y0, y1)
    }

    fun project(lat: Double, lon: Double, bbox: Bbox, w: Float, h: Float): Pair<Float, Float> {
        val lonSpan = (bbox.east - bbox.west).coerceAtLeast(1e-9)
        val latSpan = (bbox.north - bbox.south).coerceAtLeast(1e-9)
        val x = ((lon - bbox.west) / lonSpan * w).toFloat()
        val y = ((bbox.north - lat) / latSpan * h).toFloat()
        return x to y
    }

    fun projectWorld(lat: Double, lon: Double, w: Float, h: Float): Pair<Float, Float> {
        val x = ((lon + 180.0) / 360.0 * w).toFloat()
        val latRad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val merc = (1.0 - asinh(tan(latRad)) / PI) / 2.0
        val y = (merc * h).toFloat()
        return x to y
    }

    fun regionBbox(bundle: PoiBundle): Bbox = bundle.bbox

    fun poisForRegion(bundle: PoiBundle): List<Poi> = bundle.pois

    fun viewport(regionId: String, label: String, pos: UserPosition?, regionBbox: Bbox): MapViewport {
        val raw = pos?.let { PoiLogic.bboxAround(it.lat, it.lon, FOREIGN_RADIUS_KM) } ?: regionBbox
        return MapViewport(
            regionId = regionId,
            bbox = clipBbox(raw, regionBbox),
            zoom = 12,
            label = label,
        )
    }

    private fun clipBbox(inner: Bbox, outer: Bbox): Bbox = Bbox(
        south = inner.south.coerceAtLeast(outer.south),
        west = inner.west.coerceAtLeast(outer.west),
        north = inner.north.coerceAtMost(outer.north),
        east = inner.east.coerceAtMost(outer.east),
    )

    const val FOREIGN_RADIUS_KM = 12.0
}
