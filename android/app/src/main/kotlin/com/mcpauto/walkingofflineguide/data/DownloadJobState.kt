package com.mcpauto.walkingofflineguide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 앱 종료·중단 후 다운로드 이어받기용 */
@Serializable
data class DownloadJobState(
    val active: Boolean = false,
    @SerialName("country_label") val countryLabel: String = "",
    @SerialName("home_country_code") val homeCountryCode: String = "",
    @SerialName("destination_country") val destinationCountry: String = "",
    @SerialName("trip_start_epoch_day") val tripStartEpochDay: Long = 0L,
    @SerialName("trip_end_epoch_day") val tripEndEpochDay: Long = 0L,
    val legs: List<ScheduleLeg> = emptyList(),
    @SerialName("skip_hub_menu") val skipHubMenu: Boolean = false,
    val stops: List<CityStop> = emptyList(),
    @SerialName("finished_city_names") val finishedCityNames: List<String> = emptyList(),
)
