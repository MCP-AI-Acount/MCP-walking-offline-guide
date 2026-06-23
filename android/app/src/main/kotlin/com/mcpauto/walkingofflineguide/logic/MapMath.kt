package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM
import com.mcpauto.walkingofflineguide.data.TripConfig
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

    /**
     * 타일·온라인만 원거리 차단 — GPS 중심 반경 뷰는 유지.
     * 행정지역 경계 클립 아님(가장자리 맵 잘림 방지).
     */
    fun resolveDistantTileClipBbox(
        config: TripConfig,
        region: RegionRecord,
        gpsLat: Double?,
        gpsLon: Double?,
        visibleSpanM: Double = MapCameraMath.SPAN_HEADING_DEFAULT_M,
    ): Bbox? {
        if (gpsLat == null || gpsLon == null) return null
        if (TripNavigation.isAtHomeCountry(config, gpsLat, gpsLon)) {
            if (TripNavigation.isInKorea(gpsLat, gpsLon)) {
                return Bbox(south = 33.0, west = 124.5, north = 39.5, east = 132.0)
            }
            val homeRadiusKm = maxOf(40.0, visibleSpanM / 1000.0 * 12.0)
            return PoiLogic.bboxAround(gpsLat, gpsLon, homeRadiusKm)
        }
        val padKm = maxOf(20.0, visibleSpanM / 1000.0 * 5.0)
        val base = region.bbox.takeIf { MapCameraMath.bboxIsValid(it) }
            ?: PoiLogic.bboxAround(region.lat, region.lon, STOP_DOWNLOAD_RADIUS_KM)
        return expandBbox(base, padKm, region.lat)
    }

    private fun expandBbox(bbox: Bbox, padKm: Double, centerLat: Double): Bbox {
        val dLat = padKm / 111.0
        val dLon = padKm / (111.0 * cos(Math.toRadians(centerLat)).coerceAtLeast(0.01))
        return Bbox(
            south = bbox.south - dLat,
            north = bbox.north + dLat,
            west = bbox.west - dLon,
            east = bbox.east + dLon,
        )
    }

    /** @deprecated 행정지역 경계 클립 — [resolveDistantTileClipBbox] 사용 */
    @Deprecated("Use resolveDistantTileClipBbox", ReplaceWith("resolveDistantTileClipBbox(config, region, gpsLat, gpsLon)"))
    fun resolveActiveRegionClipBbox(
        bundle: PoiBundle,
        region: RegionRecord,
        config: TripConfig,
        allRegions: List<RegionRecord>,
        adminCityLabel: String? = null,
        gpsLat: Double? = null,
        gpsLon: Double? = null,
    ): Bbox? = resolveDistantTileClipBbox(config, region, gpsLat, gpsLon)

    /** 레이더 POI 반경(m) — 0=OFF · 탭마다 순환 */
    const val DEFAULT_RADAR_RADIUS_M = 400
    val RADAR_RADIUS_STEPS_M = listOf(0, 200, 400, 500, 1000)

    fun nextRadarRadiusM(currentM: Int): Int {
        val idx = RADAR_RADIUS_STEPS_M.indexOf(currentM)
        return if (idx < 0) {
            DEFAULT_RADAR_RADIUS_M
        } else {
            RADAR_RADIUS_STEPS_M[(idx + 1) % RADAR_RADIUS_STEPS_M.size]
        }
    }

    fun radarRadiusKm(radiusM: Int): Double = radiusM / 1000.0

    /** POI fetch 상한(km) — 레이더 최대 */
    const val POI_USER_RADIUS_KM = 1.0
    /** 미리보기·일반 지도 (GPS 없음) */
    const val POI_VIEW_RADIUS_KM = 4.0
    @Deprecated("Use POI_VIEW_RADIUS_KM", ReplaceWith("POI_VIEW_RADIUS_KM"))
    const val FOREIGN_RADIUS_KM = POI_VIEW_RADIUS_KM
}
