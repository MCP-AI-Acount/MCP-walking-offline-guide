package com.mcpauto.walkingofflineguide.download

import android.content.Context
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.SafeStorage
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.logic.TileFetchOrdering
import com.mcpauto.walkingofflineguide.network.OverpassClient
import com.mcpauto.walkingofflineguide.network.RoutingGraphBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * 모국 GPS 주변 — 가까운 타일·POI부터 순차 다운로드 (Q1 1차 구현).
 * 여행국 일괄 5km `downloadLeg`와 별도 경로.
 */
class HomeProgressiveDownloader(
    private val context: Context,
    private val store: TripStore = TripStore(context),
) {
    private val overpass = OverpassClient()
    private val routingBuilder = RoutingGraphBuilder()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    suspend fun runNearToFar(
        lat: Double,
        lon: Double,
        homeCountryLabel: String,
        homeLangTag: String,
        onPoiBatch: suspend () -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        cancelled = false
        val dir = regionDir()
        dir.mkdirs()
        ensureRegionRecord(lat, lon, homeCountryLabel)

        val merged = linkedMapOf<String, com.mcpauto.walkingofflineguide.data.Poi>()
        val poiRadii = listOf(1.0, 3.0, 5.0, 8.0)
        for (rKm in poiRadii) {
            coroutineContext.ensureActive()
            if (cancelled) throw CancellationException("home download cancelled")
            val bb = PoiLogic.bboxAround(lat, lon, rKm)
            val batch = runCatching { overpass.fetchPois(bb, homeLangTag) }.getOrDefault(emptyList())
            batch.forEach { merged[it.id] = it }
            if (merged.isNotEmpty()) {
                savePoiBundle(merged.values.toList(), bb)
                withContext(Dispatchers.Main) { onPoiBatch() }
            }
            if (rKm == 1.0) {
                ensureRoutingGraph(lat, lon, dir)
            }
            Thread.sleep(300)
        }

        downloadTilesNearToFar(lat, lon, dir)
        ensureTilesZip(dir)
    }

    /** 1km 빠른 POI — 전체 다운로드 대기 전 UI 표시용 */
    suspend fun fetchQuickPois(lat: Double, lon: Double, homeLangTag: String): Int = withContext(Dispatchers.IO) {
        val bb = PoiLogic.bboxAround(lat, lon, 1.0)
        val batch = runCatching { overpass.fetchPois(bb, homeLangTag) }.getOrDefault(emptyList())
        if (batch.isEmpty()) return@withContext 0
        savePoiBundle(batch, bb)
        batch.size
    }

    private suspend fun downloadTilesNearToFar(lat: Double, lon: Double, regionDir: File) {
        val tilesRoot = File(regionDir, "tiles").also { it.mkdirs() }
        val progressFile = File(regionDir, "tiles_progress.json")
        val doneKeys = loadTileProgress(progressFile)
        val bbox = PoiLogic.bboxAround(lat, lon, HOME_RADIUS_KM)
        val queue = buildList {
            for (z in RegionDownloadManager.TILE_ZOOMS) {
                val xr = MapMath.tileXRange(bbox, z)
                val yr = MapMath.tileYRange(bbox, z)
                for (x in xr) {
                    for (y in yr) {
                        val key = "$z/$x/$y"
                        if (key in doneKeys) continue
                        add(TileFetchOrdering.TileCell(z, x, y))
                    }
                }
            }
        }
        val ordered = TileFetchOrdering.sortedNearClockwise(queue, lat, lon)

        var bytes = tilesRoot.walkTopDown().filter { it.isFile && it.extension == "png" }.sumOf { it.length() }
        for (coord in ordered) {
            coroutineContext.ensureActive()
            if (cancelled) throw CancellationException("home download cancelled")
            val key = "${coord.z}/${coord.x}/${coord.y}"
            val tileFile = File(tilesRoot, "$key.png")
            if (tileFile.exists() && tileFile.length() > 8000) continue

            val sub = SUBS[(coord.x + coord.y) % SUBS.size]
            val url =
                "https://$sub.basemaps.cartocdn.com/rastertiles/voyager_nolabels/${coord.z}/${coord.x}/${coord.y}@2x.png"
            val png = runCatching {
                http.newCall(
                    Request.Builder().url(url).header("User-Agent", "WalkingOfflineGuide/1.0 (home-progressive)").build(),
                ).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    r.body?.bytes()?.takeIf { it.size > 8000 }
                }
            }.getOrNull() ?: continue

            tileFile.parentFile?.mkdirs()
            tileFile.writeBytes(png)
            doneKeys.add(key)
            bytes += png.size
            if (doneKeys.size % 8 == 0) {
                saveTileProgress(progressFile, doneKeys)
                SafeStorage.zipTilesFolder(tilesRoot, File(regionDir, "tiles.zip"))
            }
            Thread.sleep(100)
        }
        saveTileProgress(progressFile, doneKeys)
        SafeStorage.zipTilesFolder(tilesRoot, File(regionDir, "tiles.zip"))
        store.loadRegions().find { it.id == REGION_ID }?.let { rec ->
            store.saveRegion(rec.copy(downloadBytes = bytes, downloadComplete = doneKeys.isNotEmpty()))
        }
    }

    /** 다운로드 중단·재시작 시에도 zip 정본 생성 */
    private suspend fun ensureTilesZip(regionDir: File) = withContext(Dispatchers.IO) {
        val tilesRoot = File(regionDir, "tiles")
        val zip = File(regionDir, "tiles.zip")
        if (tilesRoot.isDirectory && tilesRoot.walkTopDown().any { it.isFile && it.extension == "png" }) {
            SafeStorage.zipTilesFolder(tilesRoot, zip)
        }
    }

    private suspend fun ensureRoutingGraph(
        lat: Double,
        lon: Double,
        regionDir: File,
        force: Boolean = false,
    ): Boolean {
        val graphFile = File(regionDir, "routing_graph.json")
        if (!force && graphFile.exists() && graphFile.length() > 32) return true
        if (force && graphFile.exists()) graphFile.delete()
        coroutineContext.ensureActive()
        if (cancelled) throw CancellationException("home download cancelled")
        return runCatching {
            val bb = PoiLogic.bboxAround(lat, lon, HOME_RADIUS_KM)
            val body = routingBuilder.buildAndSerialize(bb)
            if (body.length < 48) return@runCatching false
            SafeStorage.atomicWriteText(graphFile, body)
            com.mcpauto.walkingofflineguide.data.RoutingGraph.invalidate(REGION_ID)
            true
        }.getOrDefault(false)
    }

    /** 타일·POI 다운로드와 별도 — 도보 경로 그래프만 우선 확보 */
    suspend fun ensureRoutingGraphForPosition(lat: Double, lon: Double): Boolean = withContext(Dispatchers.IO) {
        val dir = regionDir().also { it.mkdirs() }
        val graphFile = File(dir, "routing_graph.json")
        val rec = store.loadRegions().find { it.id == REGION_ID }
        if (!graphFile.exists() || graphFile.length() <= 32) {
            return@withContext ensureRoutingGraph(lat, lon, dir)
        }
        if (rec != null) {
            val movedM = PoiLogic.haversineM(rec.lat, rec.lon, lat, lon)
            if (movedM > ROUTING_REBUILD_MOVE_M) {
                return@withContext ensureRoutingGraph(lat, lon, dir, force = true).also { ok ->
                    if (ok) store.saveRegion(rec.copy(lat = lat, lon = lon))
                }
            }
        }
        true
    }

    suspend fun forceRebuildRoutingGraphForPosition(lat: Double, lon: Double): Boolean = withContext(Dispatchers.IO) {
        val dir = regionDir().also { it.mkdirs() }
        ensureRoutingGraph(lat, lon, dir, force = true)
    }

    private suspend fun ensureRegionRecord(lat: Double, lon: Double, homeCountryLabel: String) {
        val existing = store.loadRegions().find { it.id == REGION_ID }
        if (existing != null) return
        val bb = PoiLogic.bboxAround(lat, lon, HOME_RADIUS_KM)
        store.saveRegion(
            RegionRecord(
                id = REGION_ID,
                cityName = "현재 위치",
                countryLabel = homeCountryLabel.ifBlank { "Home" },
                lat = lat,
                lon = lon,
                bbox = bb,
                downloadComplete = false,
            ),
        )
    }

    private fun savePoiBundle(pois: List<com.mcpauto.walkingofflineguide.data.Poi>, bbox: Bbox) {
        val dir = regionDir()
        dir.mkdirs()
        val bundle = PoiBundle(
            region = REGION_ID,
            labelKo = "모국 주변",
            bbox = bbox,
            count = pois.size,
            pois = pois,
        )
        SafeStorage.atomicWriteText(File(dir, "poi.json"), json.encodeToString(bundle))
    }

    private fun regionDir(): File = File(context.filesDir, "walking_data/regions/$REGION_ID")

    suspend fun ensureTilesZipOnDisk() = ensureTilesZip(regionDir())

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
        const val REGION_ID = "home_live_cache"
        const val HOME_RADIUS_KM = 8.0
        private val SUBS = listOf("a", "b", "c", "d")
        private val sessionMutex = Mutex()
        @Volatile private var running = false
        private var lastLat: Double? = null
        private var lastLon: Double? = null

        /** GPS 약 500m 이동마다 재호출 */
        private const val RETRIGGER_MOVE_M = 500.0
        /** 도보 그래프 — 이 거리 이상 이동 시 재빌드 */
        private const val ROUTING_REBUILD_MOVE_M = 500.0
        /** GPS 주변 도보 경로 그래프만 즉시 확보 (타일 다운로드와 분리) */
        suspend fun ensureRoutingGraphAt(context: Context, lat: Double, lon: Double): Boolean =
            HomeProgressiveDownloader(context).ensureRoutingGraphForPosition(lat, lon)

        suspend fun forceRebuildRoutingGraphAt(context: Context, lat: Double, lon: Double): Boolean =
            HomeProgressiveDownloader(context).forceRebuildRoutingGraphForPosition(lat, lon)

        suspend fun runIfNeeded(
            context: Context,
            lat: Double,
            lon: Double,
            homeCountryLabel: String,
            homeLangTag: String,
            onPoiBatch: suspend () -> Unit = {},
            onRoutingGraphReady: suspend () -> Unit = {},
        ) {
            val dl = HomeProgressiveDownloader(context)
            val poiFile = File(context.filesDir, "walking_data/regions/$REGION_ID/poi.json")
            if (poiFile.exists() && poiFile.length() > 48) {
                onPoiBatch()
            }

            val graphFile = File(context.filesDir, "walking_data/regions/$REGION_ID/routing_graph.json")
            val graphMissing = !graphFile.exists() || graphFile.length() <= 32
            val poiSparse = !poiFile.exists() || poiFile.length() < 48

            if (poiSparse) {
                val n = dl.fetchQuickPois(lat, lon, homeLangTag)
                if (n > 0) onPoiBatch()
            }

            if (dl.ensureRoutingGraphForPosition(lat, lon)) {
                onRoutingGraphReady()
            }

            sessionMutex.withLock {
                if (running) return
                val prevLat = lastLat
                val prevLon = lastLon
                if (!graphMissing && !poiSparse && prevLat != null && prevLon != null) {
                    val moved = PoiLogic.haversineM(prevLat, prevLon, lat, lon)
                    if (moved < RETRIGGER_MOVE_M) return
                }
                running = true
            }
            try {
                runCatching {
                    dl.runNearToFar(lat, lon, homeCountryLabel, homeLangTag, onPoiBatch)
                }
                lastLat = lat
                lastLon = lon
            } finally {
                running = false
            }
        }
    }
}
