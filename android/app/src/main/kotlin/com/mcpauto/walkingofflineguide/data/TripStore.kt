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
        }.getOrElse {
            SafeStorage.quarantineCorrupt(configFile)
            TripConfig()
        }
    }

    suspend fun saveConfig(config: TripConfig) = withContext(Dispatchers.IO) {
        SafeStorage.atomicWriteText(configFile, json.encodeToString(config))
    }

    suspend fun loadRegions(): List<RegionRecord> = withContext(Dispatchers.IO) {
        regionsDir.listFiles()?.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val meta = File(dir, "region.json")
            if (!meta.exists()) return@mapNotNull null
            runCatching { json.decodeFromString<RegionRecord>(meta.readText()) }.getOrElse {
                SafeStorage.quarantineCorrupt(meta)
                null
            }
        }?.sortedBy { it.cityName }.orEmpty()
    }

    suspend fun saveRegion(record: RegionRecord) = withContext(Dispatchers.IO) {
        val dir = regionDir(record.id)
        SafeStorage.atomicWriteText(File(dir, "region.json"), json.encodeToString(record))
    }

    suspend fun deleteRegion(id: String) = withContext(Dispatchers.IO) {
        File(regionsDir, id).deleteRecursively()
    }

    fun todayLegIndex(config: TripConfig): Int? {
        val today = LocalDate.now().toEpochDay()
        config.legs.indexOfFirst { leg ->
            leg.startEpochDay > 0 && leg.endEpochDay > 0 && today in leg.startEpochDay..leg.endEpochDay
        }.takeIf { it >= 0 }?.let { return it }
        if (config.tripStartEpochDay <= 0L) return null
        val dayOffset = ChronoUnit.DAYS.between(
            LocalDate.ofEpochDay(config.tripStartEpochDay),
            LocalDate.ofEpochDay(today),
        ).toInt()
        return config.legs.indexOfFirst { dayOffset in it.dayStart..it.dayEnd }.takeIf { it >= 0 }
    }

    fun todayCityDescription(config: TripConfig, regions: List<RegionRecord>): String {
        val idx = todayLegIndex(config) ?: return "오늘 예정된 일정이 없습니다."
        val leg = config.legs.getOrNull(idx) ?: return ""
        val names = buildList {
            if (leg.startPoint.confirmed) add(leg.startPoint.name)
            addAll(leg.waypoints.filter { it.confirmed }.map { it.name })
            if (leg.endPoint.confirmed) add(leg.endPoint.name)
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

    suspend fun deleteAllMapData() = withContext(Dispatchers.IO) {
        regionsDir.listFiles()?.forEach { it.deleteRecursively() }
        regionsDir.mkdirs()
        clearDownloadJob()
    }

    private val downloadJobFile get() = File(root, "download_job.json")

    suspend fun loadDownloadJob(): DownloadJobState? = withContext(Dispatchers.IO) {
        if (!downloadJobFile.exists()) return@withContext null
        runCatching {
            json.decodeFromString<DownloadJobState>(downloadJobFile.readText())
        }.getOrElse {
            SafeStorage.quarantineCorrupt(downloadJobFile)
            null
        }?.takeIf { it.active }
    }

    suspend fun saveDownloadJob(job: DownloadJobState) = withContext(Dispatchers.IO) {
        SafeStorage.atomicWriteText(downloadJobFile, json.encodeToString(job))
    }

    suspend fun clearDownloadJob() = withContext(Dispatchers.IO) {
        downloadJobFile.delete()
    }

    suspend fun findRegionIdForCity(cityName: String): String? = withContext(Dispatchers.IO) {
        loadRegions().firstOrNull {
            it.cityName.equals(cityName, ignoreCase = true)
        }?.id
    }

    /** 여행기간 중 메뉴 건너뛰기 → 오늘 일정 도시 또는 첫 다운로드 지역 */
    fun resolveAutoMapRegion(config: TripConfig, regions: List<RegionRecord>): RegionRecord? {
        val completed = regions.filter { it.downloadComplete }
        if (completed.isEmpty()) return null
        val idx = todayLegIndex(config)
        if (idx != null) {
            val leg = config.legs.getOrNull(idx) ?: return completed.firstOrNull()
            val names = buildList {
                if (leg.startPoint.confirmed) add(leg.startPoint.name)
                addAll(leg.waypoints.filter { it.confirmed }.map { it.name })
                if (leg.endPoint.confirmed) add(leg.endPoint.name)
            }
            for (name in names) {
                completed.find {
                    it.cityName.equals(name, ignoreCase = true) || it.cityName.contains(name, ignoreCase = true)
                }?.let { return it }
            }
        }
        return completed.firstOrNull()
    }

    fun newRegionId(city: String): String =
        city.lowercase().replace(Regex("[^a-z0-9가-힣]+"), "_").take(24) + "_" + System.currentTimeMillis().toString(36)

    /** 같은 도시 재다운로드·이어받기 시 기존 ID 재사용 */
    fun stableRegionId(city: String, existingId: String?): String =
        existingId ?: newRegionId(city)
}
