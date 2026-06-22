package com.mcpauto.walkingofflineguide.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CityPoint(
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val confirmed: Boolean = false,
)

@Serializable
data class ScheduleLeg(
    val id: String,
    @SerialName("day_start") val dayStart: Int = 0,
    @SerialName("day_end") val dayEnd: Int = 0,
    @SerialName("start_point") val startPoint: CityPoint = CityPoint(),
    val waypoints: List<CityPoint> = emptyList(),
    @SerialName("end_point") val endPoint: CityPoint = CityPoint(),
    @SerialName("leg_confirmed") val legConfirmed: Boolean = false,
)
