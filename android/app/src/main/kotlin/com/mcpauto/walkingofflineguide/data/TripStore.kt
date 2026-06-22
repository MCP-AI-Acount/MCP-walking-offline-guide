package com.mcpauto.walkingofflineguide.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class TripStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val root = File(context.filesDir, "walking_data")
    private val configFile get() = File(root, "trip_config.json")
    private val regionsDir get() = File(root, "regions")

    init {
        root.mkdirs()
        regionsDir.mkdirs()
    }

    fun regionDir(id: String): File = File(regionsDir, id).also { it.mkdirs() }

    suspend fun loadConfig(): TripConfig = withContext(Dispatchers.IO) {
        if (!configFile.exists()) return@withContext TripConfig()
        runCatching {
            json.decodeFromString<TripConfig>(configFile.readText())
        }.getOrElse { TripConfig() }
    }

    suspend fun saveConfig(config: TripConfig) = withContext(Dispatchers.IO) {
        configFile.writeText(json.encodeToString(config))
    }

    suspend fun loadRegions(): List<RegionRecord> = withContext(Dispatchers.IO) {
        regionsDir.listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val meta = File(dir, "region.json")
            if (!meta.exists()) return@mapNotNull null
            runCatching { json.decodeFromString<RegionRecord>(meta.readText()) }.getOrNull()
        }?.sortedBy { it.cityName }.orEmpty()
    }

    suspend fun saveRegion(record: RegionRecord) = withContext(Dispatchers.IO) {
        val dir = regionDir(record.id)
        File(dir, "region.json").writeText(json.encodeToString(record))
    }

    suspend fun deleteRegion(id: String) = withContext(Dispatchers.IO) {
        File(regionsDir, id).deleteRecursively()
    }

    suspend fun deleteAllMapData() = withContext(Dispatchers.IO) {
        regionsDir.listFiles()?.forEach { it.deleteRecursively() }
        regionsDir.mkdirs()
    }

    fun todayLegIndex(config: TripConfig): Int? {
        if (config.tripStartEpochDay <= 0L) return null
        val today = LocalDate.now().toEpochDay()
        val dayOffset = ChronoUnit.DAYS.between(
            LocalDate.ofEpochDay(config.tripStartEpochDay),
            LocalDate.ofEpochDay(today),
        ).toInt()
        return config.legs.indexOfFirst { dayOffset in it.dayStart..it.dayEnd }.takeIf { it >= 0 }
    }

    fun todayCityDescription(config: TripConfig, regions: List<RegionRecord>): String {
        val idx = todayLegIndex(config) ?: return "오늘 예정된 일정이 없습니다."
        val leg = config.legs.getOrNull(idx) ?: return ""
        val names = leg.cities.map { it.name }.ifEmpty {
            listOfNotNull(
                leg.walkStart.takeIf { it.isNotBlank() },
                *leg.waypoints.toTypedArray(),
                leg.walkDestination.takeIf { it.isNotBlank() },
            )
        }
        if (names.isEmpty()) return "일정 ${idx + 1}: 도시 정보 없음"
        val desc = names.mapNotNull { name ->
            regions.find { it.cityName.equals(name, ignoreCase = true) || it.cityName.contains(name) }
                ?.descriptionKo?.takeIf { it.isNotBlank() }
                ?.let { name to it }
        }
        return if (desc.isEmpty()) {
            "오늘: ${names.joinToString(" → ")}"
        } else {
            desc.joinToString("\n\n") { (n, d) -> "【$n】\n$d" }
        }
    }

    fun newLegId(): String = UUID.randomUUID().toString().take(8)

    fun newRegionId(city: String): String =
        city.lowercase().replace(Regex("[^a-z0-9가-힣]+"), "_").take(24) + "_" + System.currentTimeMillis().toString(36)
}
