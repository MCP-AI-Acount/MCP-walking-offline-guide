package com.mcpauto.walkingofflineguide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class PoiBundle(
    val region: String = "",
    @SerialName("label_ko") val labelKo: String = "",
    val bbox: Bbox = Bbox(),
    val count: Int = 0,
    val pois: List<Poi> = emptyList(),
)

@Serializable
data class Bbox(
    val south: Double = 0.0,
    val west: Double = 0.0,
    val north: Double = 0.0,
    val east: Double = 0.0,
)

@Serializable
data class Poi(
    val id: String,
    val kind: String = "attraction",
    @SerialName("name_ko") val nameKo: String,
    @SerialName("name_home") val nameHome: String? = null,
    val lat: Double,
    val lon: Double,
    @SerialName("description_ko") val descriptionKo: String? = null,
    @SerialName("description_home") val descriptionHome: String? = null,
    @SerialName("distance_m") val distanceM: Int? = null,
    val tourism: String? = null,
    val rating: Float? = null,
)

@Serializable
data class CityStop(
    val name: String,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    /** 출발·경유·도착 각 지점 주변 오프라인 다운로드 반경 */
    @SerialName("radius_km") val radiusKm: Double = STOP_DOWNLOAD_RADIUS_KM,
)

/** 출발지·경유지·도착지 공통 — 지점 중심 반경(km) */
const val STOP_DOWNLOAD_RADIUS_KM = 5.0

@Serializable
data class TripConfig(
    @SerialName("home_country") val homeCountry: String = "",
    @SerialName("home_country_code") val homeCountryCode: String = "",
    @SerialName("home_lat") val homeLat: Double = 0.0,
    @SerialName("home_lon") val homeLon: Double = 0.0,
    @SerialName("destination_country") val destinationCountry: String = "",
    @SerialName("arrival_airport") val arrivalAirport: CityPoint = CityPoint(),
    @SerialName("trip_start_epoch_day") val tripStartEpochDay: Long = 0L,
    @SerialName("trip_end_epoch_day") val tripEndEpochDay: Long = 0L,
    val legs: List<ScheduleLeg> = emptyList(),
    @SerialName("show_hotel") val showHotel: Boolean = true,
    @SerialName("show_restaurant") val showRestaurant: Boolean = true,
    @SerialName("show_sight") val showSight: Boolean = true,
    @SerialName("auto_delete_after_trip") val autoDeleteAfterTrip: Boolean = false,
    @SerialName("manual_delete_prompt") val manualDeletePrompt: Boolean = true,
    @SerialName("skip_hub_menu") val skipHubMenu: Boolean = false,
    @SerialName("basic_setup_complete") val basicSetupComplete: Boolean = false,
    @SerialName("setup_complete") val setupComplete: Boolean = false,
)

@Serializable
data class RegionRecord(
    val id: String,
    @SerialName("city_name") val cityName: String,
    @SerialName("country_label") val countryLabel: String,
    val lat: Double,
    val lon: Double,
    val bbox: Bbox = Bbox(),
    @SerialName("download_complete") val downloadComplete: Boolean = false,
    @SerialName("download_bytes") val downloadBytes: Long = 0L,
    @SerialName("estimated_bytes") val estimatedBytes: Long = 0L,
    val visited: Boolean = false,
    @SerialName("description_ko") val descriptionKo: String = "",
)

data class UserPosition(
    val lat: Double,
    val lon: Double,
    val bearingDeg: Float? = null,
    val speedMps: Float = 0f,
    val simulated: Boolean = false,
)

enum class DeleteMode { AUTO_AFTER_TRIP, MANUAL_PROMPT, KEEP }

fun TripConfig.isInTripPeriod(): Boolean {
    if (tripStartEpochDay <= 0L) return false
    val today = LocalDate.now().toEpochDay()
    val end = if (tripEndEpochDay >= tripStartEpochDay) tripEndEpochDay else tripStartEpochDay + 30
    return today in tripStartEpochDay..end
}
