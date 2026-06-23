package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.TripConfig

/**
 * 지도 앱 이원 정책
 * - HOME_LIVE: 모국 GPS + 인터넷(WiFi 등) → 일반 지도앱 (온라인 타일·GPS)
 * - TRAVEL: 그 외 → 여행 오프라인 (다운로드 지역·preview/on-site)
 * - NEED_TRAVEL_SETUP: 여행 미설정 → 여행 설정 유도
 */
enum class MapAppPolicy {
    HOME_LIVE,
    TRAVEL,
    NEED_TRAVEL_SETUP,
}

data class MapPolicyDecision(
    val policy: MapAppPolicy,
    /** 유저/로그용 한 줄 */
    val reason: String,
)

object MapPolicy {

    /** 온라인 전용 홈 지도 (다운로드 없을 때) */
    const val HOME_LIVE_ONLINE_ID = "__home_live_online__"

    /** 일반 지도앱 = 모국 GPS + 네트워크 둘 다 */
    fun isHomeLiveEligible(
        config: TripConfig,
        gpsLat: Double?,
        gpsLon: Double?,
        hasGpsFix: Boolean,
        hasInternet: Boolean,
    ): Boolean =
        hasGpsFix && gpsLat != null && gpsLon != null &&
            hasInternet &&
            TripNavigation.isAtHomeCountry(config, gpsLat, gpsLon)

    fun decide(
        config: TripConfig,
        regions: List<RegionRecord>,
        gpsLat: Double?,
        gpsLon: Double?,
        hasGpsFix: Boolean,
        hasInternet: Boolean,
    ): MapPolicyDecision {
        if (isHomeLiveEligible(config, gpsLat, gpsLon, hasGpsFix, hasInternet)) {
            return MapPolicyDecision(
                MapAppPolicy.HOME_LIVE,
                "모국 GPS + WiFi — 일반 지도 모드",
            )
        }
        val travelReady = config.setupComplete && regions.any { it.downloadComplete }
        if (!config.setupComplete || !travelReady) {
            val why = when {
                !config.setupComplete -> "여행 목적지·일정 미설정"
                else -> "다운로드된 여행 지역 없음"
            }
            return MapPolicyDecision(MapAppPolicy.NEED_TRAVEL_SETUP, why)
        }
        val why = when {
            !hasGpsFix || gpsLat == null -> "GPS 없음 — 여행 오프라인 지도"
            !hasInternet && gpsLat != null && TripNavigation.isAtHomeCountry(config, gpsLat, gpsLon ?: 0.0) ->
                "모국이지만 WiFi/인터넷 없음 — 여행 오프라인 모드"
            gpsLat != null && !TripNavigation.isAtHomeCountry(config, gpsLat, gpsLon ?: 0.0) ->
                "모국 밖 GPS — 여행지 오프라인 지도"
            else -> "여행 오프라인 모드"
        }
        return MapPolicyDecision(MapAppPolicy.TRAVEL, why)
    }

    /** HOME_LIVE 진입용 region (모국 다운로드 중 GPS와 가장 가까운 도시) */
    fun homeLiveRegion(
        config: TripConfig,
        regions: List<RegionRecord>,
        gpsLat: Double?,
        gpsLon: Double?,
    ): RegionRecord? {
        TripNavigation.resolveHomeMapRegion(config, regions, gpsLat, gpsLon)?.let { return it }
        regions.filter { TripNavigation.isHomeRegion(config, it) }
            .minByOrNull { PoiLogic.haversineM(gpsLat ?: it.lat, gpsLon ?: it.lon, it.lat, it.lon) }
            ?.let { return it }
        if (gpsLat != null && gpsLon != null) {
            return onlineHomeStub(config, gpsLat, gpsLon)
        }
        return null
    }

    fun onlineHomeStub(config: TripConfig, gpsLat: Double, gpsLon: Double): RegionRecord {
        val delta = 0.045
        return RegionRecord(
            id = HOME_LIVE_ONLINE_ID,
            cityName = "현재 위치",
            countryLabel = config.homeCountry.ifBlank { "Home" },
            lat = gpsLat,
            lon = gpsLon,
            bbox = Bbox(
                south = gpsLat - delta,
                west = gpsLon - delta,
                north = gpsLat + delta,
                east = gpsLon + delta,
            ),
            downloadComplete = false,
        )
    }

    /** TRAVEL 자동 진입 region */
    fun travelRegion(
        config: TripConfig,
        regions: List<RegionRecord>,
        gpsLat: Double?,
        gpsLon: Double?,
        hasGpsFix: Boolean,
    ): RegionRecord? = TripNavigation.resolveTravelMapRegion(config, regions, gpsLat, gpsLon, hasGpsFix)
}
