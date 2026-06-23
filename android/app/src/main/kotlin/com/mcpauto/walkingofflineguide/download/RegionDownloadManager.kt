package com.mcpauto.walkingofflineguide.download

import android.content.Context
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.CityStop
import com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM
import com.mcpauto.walkingofflineguide.data.DownloadJobState
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.SafeStorage
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.logic.PoiLocalization
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.OverpassClient
import com.mcpauto.walkingofflineguide.network.RoutingGraphBuilder
import com.mcpauto.walkingofflineguide.util.HomeLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class DownloadProgress(
    val regionId: String,
    val cityName: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val phase: String,
    /** 0~100 — 타일 용량 대비가 아니라 단계 가중치 기준 (초반 POI·번역·경로에서도 올라감) */
    val percent: Int,
    val phaseDetail: String = "",
) {
    val label: String
        get() = buildString {
            if (cityName.isNotBlank()) append("$cityName · ")
            append(phase)
            if (phaseDetail.isNotBlank()) append(" · $phaseDetail")
            append(" · $percent%")
            if (bytesTotal > 0) {
                append(" (받음 ${RegionDownloadManager.formatSize(bytesDone)} / 전체 예상 ${RegionDownloadManager.formatSize(bytesTotal)})")
            }
        }
}

/** 단계별 표시 % — 타일 MB가 전체 추정의 대부분이라 bytes 비율만 쓰면 0%에 오래 머무름 */
private fun phaseDisplayPercent(phase: String, phaseProgress: Int): Int = when (phase) {
    "연결 중" -> 1.coerceAtLeast(phaseProgress.coerceIn(0, 100) * 2 / 100)
    "좌표 확인" -> 2 + phaseProgress.coerceIn(0, 100) * 6 / 100
    "설명" -> 8 + phaseProgress.coerceIn(0, 100) * 2 / 100
    "POI" -> 10 + phaseProgress.coerceIn(0, 100) * 14 / 100
    "번역" -> 24 + phaseProgress.coerceIn(0, 100) * 20 / 100
    "도보경로" -> 44 + phaseProgress.coerceIn(0, 100) * 10 / 100
    "지도" -> 54 + phaseProgress.coerceIn(0, 100) * 45 / 100
    "완료" -> 100
    else -> phaseProgress.coerceIn(0, 100)
}

class RegionDownloadManager(
    private val context: Context,
    private val store: TripStore,
) {
    private val geocoder = NominatimGeocoder()
    private val overpass = OverpassClient()
    private val routingBuilder = RoutingGraphBuilder()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var cancelled: Boolean = false

    fun estimateBytes(stops: List<CityStop>, zooms: List<Int> = TILE_ZOOMS): Long {
        var tiles = 0
        stops.forEach { stop ->
            if (stop.lat == 0.0 && stop.lon == 0.0) {
                tiles += 80
            } else {
                val bb = PoiLogic.bboxAround(stop.lat, stop.lon, stop.radiusKm)
                zooms.forEach { z ->
                    val xr = MapMath.tileXRange(bb, z)
                    val yr = MapMath.tileYRange(bb, z)
                    tiles += (xr.last - xr.first + 1) * (yr.last - yr.first + 1)
                }
            }
        }
        return tiles * 28_000L + stops.size * 180_000L
    }

    /** 지도·명소·번역·도보경로 포함 대략 소요(분) */
    fun estimateDurationMinutes(stops: List<CityStop>, zooms: List<Int> = TILE_ZOOMS): Int {
        if (stops.isEmpty()) return 0
        val bytes = estimateBytes(stops, zooms)
        val tileBytes = (bytes * 0.72).toLong().coerceAtLeast(0)
        val tileCount = (tileBytes / 28_000L).coerceAtLeast(1)
        val tileSec = tileCount * 0.38
        val perCitySec = 150.0 + 120.0
        return ((tileSec + stops.size * perCitySec) / 60.0).toInt().coerceIn(2, 120)
    }

    suspend fun downloadLeg(
        countryLabel: String,
        stops: List<CityStop>,
        homeCountryCode: String,
        jobSeed: DownloadJobState? = null,
        onProgress: (DownloadProgress) -> Unit,
    ): List<RegionRecord> = withContext(Dispatchers.IO) {
        cancelled = false
        val stopsNorm = stops.map { it.copy(radiusKm = STOP_DOWNLOAD_RADIUS_KM) }
        val homeLang = HomeLanguage.langTag(homeCountryCode.ifBlank { "KR" })
        val destLang = HomeLanguage.langTagFromCountryName(countryLabel)
        val finished = jobSeed?.finishedCityNames?.toMutableSet() ?: mutableSetOf()
        val results = mutableListOf<RegionRecord>()

        val resolved = mutableListOf<CityStop>()
        val totalEstimateEarly = estimateBytes(stopsNorm)
        onProgress(
            DownloadProgress("", "연결 중", 0, totalEstimateEarly, "연결 중", phaseDisplayPercent("연결 중", 5)),
        )
        val pendingGeocode = stops.filter { it.name !in finished && (it.lat == 0.0 || it.lon == 0.0) }
        var geocodeIdx = 0
        for (stop in stopsNorm) {
            coroutineContext.ensureActive()
            if (cancelled) throw CancellationException("download paused")
            if (stop.name in finished) continue
            val geo = if (stop.lat != 0.0 && stop.lon != 0.0) {
                stop
            } else {
                geocodeIdx++
                val pctInPhase = if (pendingGeocode.isEmpty()) 100 else geocodeIdx * 100 / pendingGeocode.size
                onProgress(
                    DownloadProgress(
                        "",
                        stop.name,
                        0,
                        totalEstimateEarly,
                        "좌표 확인",
                        phaseDisplayPercent("좌표 확인", pctInPhase),
                        phaseDetail = "$geocodeIdx/${pendingGeocode.size}",
                    ),
                )
                val g = geocoder.search(stop.name, countryLabel)
                if (g == null) {
                    onProgress(
                        DownloadProgress(
                            "",
                            stop.name,
                            0,
                            totalEstimateEarly,
                            "좌표 확인",
                            phaseDisplayPercent("좌표 확인", pctInPhase),
                            phaseDetail = "「${stop.name}」 좌표를 찾지 못했습니다",
                        ),
                    )
                    continue
                }
                Thread.sleep(1100)
                CityStop(stop.name, g.lat, g.lon, stop.radiusKm)
            }
            resolved += geo
        }
        if (resolved.isEmpty() && finished.isEmpty()) {
            onProgress(
                DownloadProgress("", "", 0, totalEstimateEarly, "연결 중", 0, phaseDetail = "도시 좌표를 확인할 수 없습니다"),
            )
            throw IllegalStateException("도시 좌표를 찾지 못했습니다. 도시 이름을 확인해 주세요.")
        }

        // 이미 완료된 도시는 결과에 포함
        store.loadRegions().filter { it.downloadComplete && it.cityName in finished }.forEach { results += it }

        val totalEstimate = estimateBytes(resolved) + finished.size * 50_000L
        var globalDone = finished.size * 200_000L

        var job = (jobSeed ?: DownloadJobState()).copy(
            active = true,
            countryLabel = countryLabel,
            homeCountryCode = homeCountryCode,
            stops = stopsNorm,
            finishedCityNames = finished.toList(),
        )
        store.saveDownloadJob(job)

        for (geo in resolved) {
            if (cancelled) throw CancellationException("download paused")
            val existing = store.findRegionIdForCity(geo.name)
            val existingRecord = store.loadRegions().find { it.id == existing }
            if (existingRecord?.downloadComplete == true) {
                finished += geo.name
                results += existingRecord
                job = job.copy(finishedCityNames = finished.toList())
                store.saveDownloadJob(job)
                continue
            }

            val id = store.stableRegionId(geo.name, existing)
            val bbox = PoiLogic.bboxAround(geo.lat, geo.lon, geo.radiusKm)
            var record = existingRecord ?: RegionRecord(
                id = id,
                cityName = geo.name,
                countryLabel = countryLabel,
                lat = geo.lat,
                lon = geo.lon,
                bbox = bbox,
                estimatedBytes = totalEstimate / resolved.size.coerceAtLeast(1),
            )
            val dir = store.regionDir(id)
            store.saveRegion(record)

            if (!File(dir, "description.txt").exists()) {
                onProgress(
                    DownloadProgress(id, geo.name, globalDone, totalEstimate, "설명", phaseDisplayPercent("설명", 20)),
                )
                val geoInfo = geocoder.search(geo.name, countryLabel)
                val desc = geoInfo?.description.orEmpty()
                SafeStorage.atomicWriteText(File(dir, "description.txt"), desc)
                record = record.copy(descriptionKo = desc)
                globalDone += 50_000
                Thread.sleep(1100)
            }
            if (cancelled) throw CancellationException("download paused")

            val poiFile = File(dir, "poi.json")
            if (!poiFile.exists()) {
                onProgress(
                    DownloadProgress(id, geo.name, globalDone, totalEstimate, "POI", phaseDisplayPercent("POI", 5), "명소 검색 중…"),
                )
                val raw = overpass.fetchPois(bbox, homeLang)
                if (raw.isEmpty()) {
                    throw IllegalStateException(
                        "명소(POI) 데이터를 받지 못했습니다. WiFi 연결·Overpass 서버 상태를 확인해 주세요.",
                    )
                }
                if (cancelled) throw CancellationException("download paused")
                val pois = PoiLocalization.enrichForKoreanHome(raw, destLang, homeLang)
                val bundle = PoiBundle(region = id, labelKo = geo.name, bbox = bbox, count = pois.size, pois = pois)
                SafeStorage.atomicWriteText(poiFile, json.encodeToString(bundle))
                globalDone += 120_000
            }
            if (cancelled) throw CancellationException("download paused")

            if (!File(dir, "routing_graph.json").exists()) {
                onProgress(
                    DownloadProgress(
                        id,
                        geo.name,
                        globalDone,
                        totalEstimate,
                        "도보경로",
                        phaseDisplayPercent("도보경로", 10),
                        phaseDetail = "도로 데이터 (1~3분)",
                    ),
                )
                runCatching {
                    SafeStorage.atomicWriteText(
                        File(dir, "routing_graph.json"),
                        routingBuilder.buildAndSerialize(bbox),
                    )
                }
                onProgress(
                    DownloadProgress(id, geo.name, globalDone, totalEstimate, "도보경로", phaseDisplayPercent("도보경로", 100)),
                )
                globalDone += 80_000
            }
            if (cancelled) throw CancellationException("download paused")

            onProgress(
                DownloadProgress(id, geo.name, globalDone, totalEstimate, "지도", phaseDisplayPercent("지도", 0)),
            )
            val zipFile = File(dir, "tiles.zip")
            val tileBudget = (totalEstimate - globalDone).coerceAtLeast(1L)
            val (bytes, tileCount, tilesTotal) = downloadTiles(bbox, dir, zipFile) { done, tilesDone, total ->
                val sub = if (total > 0) (tilesDone * 100 / total).coerceIn(0, 100) else ((done * 100) / tileBudget).toInt().coerceIn(0, 100)
                onProgress(
                    DownloadProgress(
                        id,
                        geo.name,
                        globalDone + done,
                        totalEstimate,
                        "지도",
                        phaseDisplayPercent("지도", sub),
                        phaseDetail = if (total > 0) "타일 $tilesDone/$total" else "",
                    ),
                )
            }
            globalDone += bytes
            if (cancelled) throw CancellationException("download paused")
            if (tileCount == 0 && !zipFile.exists()) continue

            record = record.copy(downloadBytes = bytes, downloadComplete = true)
            store.saveRegion(record)
            results += record
            finished += geo.name
            job = job.copy(finishedCityNames = finished.toList())
            store.saveDownloadJob(job)
            onProgress(DownloadProgress(id, geo.name, globalDone, totalEstimate, "완료", 100))
        }

        store.clearDownloadJob()
        results
    }

    private fun countTilesInBbox(bbox: Bbox, zooms: List<Int> = TILE_ZOOMS): Int {
        var n = 0
        for (z in zooms) {
            val xr = MapMath.tileXRange(bbox, z)
            val yr = MapMath.tileYRange(bbox, z)
            n += (xr.last - xr.first + 1) * (yr.last - yr.first + 1)
        }
        return n.coerceAtLeast(1)
    }

    private suspend fun downloadTiles(
        bbox: Bbox,
        regionDir: File,
        outZip: File,
        onPartial: (bytes: Long, tilesDone: Int, tilesTotal: Int) -> Unit,
    ): Triple<Long, Int, Int> = withContext(Dispatchers.IO) {
        val tilesRoot = File(regionDir, "tiles").also { it.mkdirs() }
        val progressFile = File(regionDir, "tiles_progress.json")
        val doneKeys = loadTileProgress(progressFile)
        val tilesTotal = countTilesInBbox(bbox)
        var bytes = tilesRoot.walkTopDown().filter { it.isFile && it.extension == "png" }.sumOf { it.length() }
        var count = doneKeys.size
        onPartial(bytes, count, tilesTotal)

        for (z in TILE_ZOOMS) {
            val xr = MapMath.tileXRange(bbox, z)
            val yr = MapMath.tileYRange(bbox, z)
            for (x in xr) {
                for (y in yr) {
                    coroutineContext.ensureActive()
                    if (cancelled) throw CancellationException("download paused")
                    val key = "$z/$x/$y"
                    val tileFile = File(tilesRoot, "$key.png")
                    if (key in doneKeys && tileFile.exists() && tileFile.length() > 8000) continue

                    val sub = SUBS[(x + y) % SUBS.size]
                    val url = "https://$sub.basemaps.cartocdn.com/rastertiles/voyager_nolabels/$z/$x/$y@2x.png"
                    val req = Request.Builder().url(url).header("User-Agent", "WalkingOfflineGuide/1.0").build()
                    val png = runCatching {
                        http.newCall(req).execute().use { r ->
                            if (!r.isSuccessful) return@use null
                            r.body?.bytes()?.takeIf { it.size > 8000 }
                        }
                    }.getOrNull() ?: continue

                    tileFile.parentFile?.mkdirs()
                    tileFile.writeBytes(png)
                    doneKeys.add(key)
                    if (doneKeys.size % 8 == 0) saveTileProgress(progressFile, doneKeys)
                    bytes += png.size
                    count++
                    onPartial(bytes, count, tilesTotal)
                    if (count % 2 == 0) Thread.sleep(80) else Thread.sleep(40)
                }
            }
        }
        saveTileProgress(progressFile, doneKeys)
        SafeStorage.zipTilesFolder(tilesRoot, outZip)
        Triple(bytes, count, tilesTotal)
    }

    private fun loadTileProgress(file: File): MutableSet<String> {
        if (!file.exists()) return mutableSetOf()
        return runCatching {
            json.decodeFromString<List<String>>(file.readText()).toMutableSet()
        }.getOrElse {
            SafeStorage.quarantineCorrupt(file)
            mutableSetOf()
        }
    }

    private fun saveTileProgress(file: File, keys: Set<String>) {
        SafeStorage.atomicWriteText(file, json.encodeToString(keys.toList()))
    }

    companion object {
        private val SUBS = listOf("a", "b", "c", "d")
        val TILE_ZOOMS = listOf(10, 11, 12, 13, 14, 15, 16, 17, 18)
        val ONLINE_ZOOMS = setOf(10, 11, 12, 13, 14, 15, 16, 17, 18)

        fun formatSize(bytes: Long): String = when {
            bytes <= 0 -> "0MB"
            bytes < 1024 -> "${bytes}B"
            bytes < 1_048_576 -> "${(bytes + 1023) / 1024}KB"
            bytes < 10_485_760 -> String.format(java.util.Locale.US, "%.1fMB", bytes / 1_048_576.0)
            else -> "${bytes / 1_048_576}MB"
        }
    }
}
