package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.logic.MapMath.tileBounds
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** GPS 기준 가까운 순 → 시계방향(북=0°) 타일 fetch 순서 */
object TileFetchOrdering {

    data class TileCell(val z: Int, val x: Int, val y: Int) {
        fun sortKey(anchorLat: Double?, anchorLon: Double?): Triple<Double, Double, Int> {
            if (anchorLat == null || anchorLon == null) {
                return Triple(0.0, 0.0, -z)
            }
            val bb = tileBounds(x, y, z)
            val cx = (bb.north + bb.south) / 2
            val cy = (bb.east + bb.west) / 2
            val dist = PoiLogic.haversineM(anchorLat, anchorLon, cx, cy)
            val bearing = bearingClockwiseDeg(anchorLat, anchorLon, cx, cy)
            return Triple(dist, bearing, -z)
        }
    }

    fun bearingClockwiseDeg(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLon = Math.toRadians(toLon - fromLon)
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var deg = Math.toDegrees(atan2(y, x))
        if (deg < 0) deg += 360.0
        return deg
    }

    fun sortedNearClockwise(
        cells: Iterable<TileCell>,
        anchorLat: Double?,
        anchorLon: Double?,
    ): List<TileCell> {
        val keyed = cells.map { it to it.sortKey(anchorLat, anchorLon) }
        return keyed.sortedWith(
            compareBy({ it.second.first }, { it.second.second }, { it.second.third }),
        ).map { it.first }
    }
}
