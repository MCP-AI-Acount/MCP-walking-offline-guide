package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object PoiLogic {
    val tourismKo = mapOf(
        "attraction" to "관광명소",
        "viewpoint" to "전망대",
        "museum" to "박물관",
        "gallery" to "미술관",
        "monument" to "기념물",
        "castle" to "성",
        "ruins" to "유적",
        "poi" to "관광지",
        "hotel" to "숙소",
        "restaurant" to "식당",
    )

    val weekdayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    fun bboxAround(lat: Double, lon: Double, radiusKm: Double): Bbox {
        val dLat = radiusKm / 111.0
        val dLon = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        return Bbox(
            south = lat - dLat,
            north = lat + dLat,
            west = lon - dLon,
            east = lon + dLon,
        )
    }

    fun mergeBbox(a: Bbox, b: Bbox): Bbox = Bbox(
        south = minOf(a.south, b.south),
        north = maxOf(a.north, b.north),
        west = minOf(a.west, b.west),
        east = maxOf(a.east, b.east),
    )

    /** 출발→경유→도착 직선 구간을 radiusKm 폭으로 덮는 bbox */
    fun corridorBboxForPath(points: List<Pair<Double, Double>>, radiusKm: Double): Bbox {
        if (points.isEmpty()) return Bbox()
        if (points.size == 1) return bboxAround(points[0].first, points[0].second, radiusKm)
        var merged = bboxAround(points[0].first, points[0].second, radiusKm)
        for (i in 0 until points.size - 1) {
            merged = mergeBbox(merged, corridorBboxForSegment(points[i], points[i + 1], radiusKm))
        }
        return merged
    }

    private fun corridorBboxForSegment(
        a: Pair<Double, Double>,
        b: Pair<Double, Double>,
        radiusKm: Double,
    ): Bbox {
        val distM = haversineM(a.first, a.second, b.first, b.second)
        val steps = (distM / 1000.0).toInt().coerceIn(1, 40)
        var merged = bboxAround(a.first, a.second, radiusKm)
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val lat = a.first + (b.first - a.first) * t
            val lon = a.second + (b.second - a.second) * t
            merged = mergeBbox(merged, bboxAround(lat, lon, radiusKm))
        }
        return mergeBbox(merged, bboxAround(b.first, b.second, radiusKm))
    }

    fun withinRadiusKm(pois: List<Poi>, lat: Double?, lon: Double?, radiusKm: Double): List<Poi> {
        if (radiusKm <= 0.0) return emptyList()
        if (lat == null || lon == null) return pois
        val maxM = radiusKm * 1000.0
        return pois.filter { haversineM(lat, lon, it.lat, it.lon) <= maxM }
    }

    fun inBbox(lat: Double, lon: Double, bbox: Bbox): Boolean =
        lat in bbox.south..bbox.north && lon in bbox.west..bbox.east

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        fun toRad(d: Double) = Math.toRadians(d)
        val dLat = toRad(lat2 - lat1)
        val dLon = toRad(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun displayRating(poi: Poi): Float {
        poi.rating?.takeIf { it in 1f..5f }?.let { return it }
        val seed = poi.id.hashCode().and(0x7fffffff)
        val base = when (poi.kind) {
            "hotel" -> 3.6f
            "restaurant" -> 3.8f
            else -> 4.0f
        }
        return (base + (seed % 10) / 10.0f).coerceIn(3.0f, 5.0f)
    }

    fun formatRating(r: Float): String = "%.1f".format(r)

    fun visibleInViewport(
        pois: List<Poi>,
        camera: MapCamera,
        screenW: Float,
        screenH: Float,
        userLat: Double? = null,
        userLon: Double? = null,
    ): List<Poi> {
        if (screenW <= 0f || screenH <= 0f) return emptyList()
        val bbox = MapCameraMath.visibleGeoBounds(camera, screenW, screenH)
        return pois
            .filter { inBbox(it.lat, it.lon, bbox) }
            .map { p ->
                val dist = if (userLat != null && userLon != null) {
                    haversineM(userLat, userLon, p.lat, p.lon).toInt()
                } else {
                    null
                }
                p.copy(distanceM = dist)
            }
            .sortedWith(compareBy<Poi> { it.distanceM ?: Int.MAX_VALUE }.thenBy { it.nameKo })
    }

    fun ttsText(poi: Poi): String {
        val desc = poi.descriptionKo?.trim().orEmpty()
        return if (desc.length >= 8) "${poi.nameKo}. $desc" else poi.nameKo
    }

    fun formatDate(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }

    fun parseDate(s: String): LocalDate {
        val parts = s.split("-")
        return LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }

    fun daysBetween(start: Long, end: Long): Long =
        ChronoUnit.DAYS.between(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
}
