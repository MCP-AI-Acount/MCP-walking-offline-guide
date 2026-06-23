package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.data.UserPosition

/** 일정·모국·GPS 기준 지도 중심 */
object TripNavigation {
    data class GeoAnchor(val lat: Double, val lon: Double, val label: String = "")

    /** 한국 대략 bbox — 네트워크 없이 모국 판별 */
    fun isInKorea(lat: Double, lon: Double): Boolean =
        lat in 33.0..39.5 && lon in 124.5..132.0

    fun isAtHomeCountry(config: TripConfig, lat: Double, lon: Double): Boolean {
        val code = config.homeCountryCode.uppercase()
        val homeName = config.homeCountry.trim()
        if (code == "KR" || code == "KP" ||
            homeName.contains("한국") || homeName.contains("Korea", ignoreCase = true)
        ) {
            return isInKorea(lat, lon)
        }
        if (config.homeLat != 0.0 && config.homeLon != 0.0) {
            return PoiLogic.haversineM(lat, lon, config.homeLat, config.homeLon) < 800_000.0
        }
        return false
    }

    private fun regionMatchesCity(region: RegionRecord, cityName: String): Boolean =
        region.cityName.equals(cityName, ignoreCase = true) ||
            region.cityName.contains(cityName, ignoreCase = true) ||
            cityName.contains(region.cityName, ignoreCase = true)

    private fun legPoints(leg: ScheduleLeg) =
        buildList {
            if (leg.startPoint.confirmed && leg.startPoint.name.isNotBlank()) add(leg.startPoint)
            addAll(leg.waypoints.filter { it.confirmed && it.name.isNotBlank() })
            if (leg.endPoint.confirmed && leg.endPoint.name.isNotBlank()) add(leg.endPoint)
        }

    /** 아직 방문하지 않은 첫 여행 지점 (일정 순) */
    fun upcomingTravelAnchor(config: TripConfig, regions: List<RegionRecord>): GeoAnchor? {
        val completed = regions.filter { it.downloadComplete }
        for (leg in config.legs.filter { it.legConfirmed }) {
            for (pt in legPoints(leg)) {
                val region = completed.find { regionMatchesCity(it, pt.name) }
                if (region != null && region.visited) continue
                val lat = pt.lat.takeIf { it != 0.0 } ?: region?.lat ?: continue
                val lon = pt.lon.takeIf { it != 0.0 } ?: region?.lon ?: continue
                return GeoAnchor(lat, lon, pt.name)
            }
        }
        val firstLeg = config.legs.firstOrNull { it.legConfirmed } ?: return null
        val firstPt = legPoints(firstLeg).firstOrNull() ?: return null
        if (firstPt.lat == 0.0) {
            completed.find { regionMatchesCity(it, firstPt.name) }?.let {
                return GeoAnchor(it.lat, it.lon, firstPt.name)
            }
            return null
        }
        return GeoAnchor(firstPt.lat, firstPt.lon, firstPt.name)
    }

    /** 여행국(비모국) 다운로드 region — GPS·일정 기준 자동 선택 */
    fun resolveTravelMapRegion(
        config: TripConfig,
        regions: List<RegionRecord>,
        gpsLat: Double?,
        gpsLon: Double?,
        hasGpsFix: Boolean,
    ): RegionRecord? {
        val completed = regions.filter { it.downloadComplete }
        if (completed.isEmpty()) return null
        val travel = completed.filter { !isHomeRegion(config, it) }
        val pool = if (travel.isNotEmpty()) travel else completed

        if (hasGpsFix && gpsLat != null && gpsLon != null &&
            !isAtHomeCountry(config, gpsLat, gpsLon)
        ) {
            pool.minByOrNull { PoiLogic.haversineM(gpsLat, gpsLon, it.lat, it.lon) }?.let { return it }
        }

        upcomingTravelAnchor(config, regions)?.let { anchor ->
            pool.find { regionMatchesCity(it, anchor.label) }?.let { return it }
        }

        for (leg in config.legs.filter { it.legConfirmed }) {
            for (pt in legPoints(leg)) {
                pool.find { regionMatchesCity(it, pt.name) }?.let { return it }
            }
        }
        return pool.firstOrNull()
    }

    /** region이 모국(여행국 라벨)인지 */
    fun isHomeRegion(config: TripConfig, region: RegionRecord): Boolean {
        if (config.homeCountry.isBlank()) return false
        val home = config.homeCountry.trim()
        val label = region.countryLabel.trim()
        if (label.isBlank()) return false
        return label.equals(home, ignoreCase = true) ||
            label.contains(home, ignoreCase = true) ||
            home.contains(label, ignoreCase = true)
    }

    /** 모국 GPS + WiFi + 모국 region — MapGuideScreen 일반 지도 (온라인 타일) */
    fun isHomeLiveMode(
        config: TripConfig,
        pos: UserPosition,
        region: RegionRecord,
        hasRealGpsFix: Boolean,
        hasInternet: Boolean,
    ): Boolean =
        hasRealGpsFix && !pos.simulated &&
            hasInternet &&
            isAtHomeCountry(config, pos.lat, pos.lon) &&
            isHomeRegion(config, region)

    /** GPS가 다운로드 지역 안(또는 15km 이내) */
    fun isGpsNearRegion(pos: UserPosition, region: RegionRecord, bbox: Bbox): Boolean {
        if (PoiLogic.inBbox(pos.lat, pos.lon, bbox)) return true
        return PoiLogic.haversineM(pos.lat, pos.lon, region.lat, region.lon) <= 15_000.0
    }

    /** 실제 GPS fix + 지역 근처 — 현장 모드 */
    fun isOnSite(pos: UserPosition, region: RegionRecord, bbox: Bbox, hasRealGpsFix: Boolean): Boolean =
        hasRealGpsFix && !pos.simulated && isGpsNearRegion(pos, region, bbox)

    /** 모국(다운로드 완료) 지역 — GPS·home 좌표와 가장 가까운 것 */
    fun resolveHomeMapRegion(
        config: TripConfig,
        regions: List<RegionRecord>,
        gpsLat: Double?,
        gpsLon: Double?,
    ): RegionRecord? {
        if (config.homeCountry.isBlank()) return null
        val home = config.homeCountry.trim()
        val candidates = regions.filter { r ->
            r.downloadComplete && (
                r.countryLabel.equals(home, ignoreCase = true) ||
                    r.countryLabel.contains(home, ignoreCase = true) ||
                    home.contains(r.countryLabel, ignoreCase = true)
                )
        }
        if (candidates.isEmpty()) return null
        val refLat = gpsLat?.takeIf { isAtHomeCountry(config, it, gpsLon ?: 0.0) }
            ?: config.homeLat.takeIf { it != 0.0 }
        val refLon = gpsLon?.takeIf { refLat == gpsLat }
            ?: config.homeLon.takeIf { config.homeLat != 0.0 }
        if (refLat != null && refLon != null) {
            return candidates.minByOrNull { PoiLogic.haversineM(refLat, refLon, it.lat, it.lon) }
        }
        return candidates.firstOrNull()
    }
}
