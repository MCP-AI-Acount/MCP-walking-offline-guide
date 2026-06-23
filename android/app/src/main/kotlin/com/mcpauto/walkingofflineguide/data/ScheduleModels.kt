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
    @SerialName("start_epoch_day") val startEpochDay: Long = 0L,
    @SerialName("end_epoch_day") val endEpochDay: Long = 0L,
    @SerialName("start_point") val startPoint: CityPoint = CityPoint(),
    val waypoints: List<CityPoint> = emptyList(),
    @SerialName("end_point") val endPoint: CityPoint = CityPoint(),
    @SerialName("leg_confirmed") val legConfirmed: Boolean = false,
)

fun defaultScheduleLeg(id: String): ScheduleLeg {
    val today = java.time.LocalDate.now().toEpochDay()
    return ScheduleLeg(
        id = id,
        startEpochDay = today,
        endEpochDay = java.time.LocalDate.now().plusDays(7).toEpochDay(),
    )
}

/** 구형 leg·trip 날짜 → 일정별 epoch day 보정 */
fun normalizeLegDates(
    legs: List<ScheduleLeg>,
    tripStartEpochDay: Long = 0L,
    tripEndEpochDay: Long = 0L,
): List<ScheduleLeg> {
    val today = java.time.LocalDate.now().toEpochDay()
    val tripStart = tripStartEpochDay.takeIf { it > 0 } ?: today
    val tripEnd = tripEndEpochDay.takeIf { it > 0 } ?: today + 7
    return legs.map { leg ->
        val start = when {
            leg.startEpochDay > 0 -> leg.startEpochDay
            tripStartEpochDay > 0 -> tripStart
            else -> today
        }
        val end = when {
            leg.endEpochDay > 0 -> leg.endEpochDay.coerceAtLeast(start)
            tripEndEpochDay > 0 -> tripEnd.coerceAtLeast(start)
            else -> start + 7
        }
        val base = tripStart
        val dayStart = java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.ofEpochDay(base),
            java.time.LocalDate.ofEpochDay(start),
        ).toInt().coerceAtLeast(0)
        val dayEnd = java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.ofEpochDay(base),
            java.time.LocalDate.ofEpochDay(end),
        ).toInt().coerceAtLeast(dayStart)
        leg.copy(startEpochDay = start, endEpochDay = end, dayStart = dayStart, dayEnd = dayEnd)
    }
}

fun tripEpochRangeFromLegs(legs: List<ScheduleLeg>): Pair<Long, Long> {
    val starts = legs.map { it.startEpochDay }.filter { it > 0 }
    val ends = legs.map { it.endEpochDay }.filter { it > 0 }
    if (starts.isEmpty() || ends.isEmpty()) return 0L to 0L
    return starts.min() to ends.max()
}
