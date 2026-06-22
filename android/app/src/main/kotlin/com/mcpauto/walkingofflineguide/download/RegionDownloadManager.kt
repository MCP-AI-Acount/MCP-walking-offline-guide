package com.mcpauto.walkingofflineguide.download

import android.content.Context
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.CityStop
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.OverpassClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.cos
import kotlin.math.pow

data class DownloadProgress(
    val regionId: String,
    val cityName: String,
    val bytesDone: Long,
    val bytesTotal: Long,
    val phase: String,
    val percent: Int,
) {
    val label: String get() = "$cityName · $phase · $percent% (${bytesDone / 1_048_576}MB / ${bytesTotal / 1_048_576}MB)"
}

class RegionDownloadManager(
    private val context: Context,
    private val store: TripStore,
) {
    private val geocoder = NominatimGeocoder()
    private val overpass = OverpassClient()
    private val json = Json { prettyPrint = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var cancelled: Boolean = false

    fun estimateBytes(stops: List<CityStop>, zooms: List<Int> = listOf(10, 11, 12, 13)): Long {
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
        return tiles * 18_000L + stops.size * 120_000L
    }

    suspend fun downloadLeg(
        countryLabel: String,
        stops: List<CityStop>,
        onProgress: (DownloadProgress) -> Unit,
    ): List<RegionRecord> = withContext(Dispatchers.IO) {
        cancelled = false
        val results = mutableListOf<RegionRecord>()
        val resolved = mutableListOf<CityStop>()
        for (stop in stops) {
            coroutineContext.ensureActive()
            if (cancelled) throw CancellationException("download cancelled")
            val geo = if (stop.lat != 0.0 && stop.lon != 0.0) {
                stop
            } else {
                val g = geocoder.search(stop.name, countryLabel) ?: continue
                CityStop(stop.name, g.lat, g.lon, stop.radiusKm)
            }
            resolved += geo
        }
        val totalEstimate = estimateBytes(resolved)
        var globalDone = 0L
        for (geo in resolved) {
            if (cancelled) throw CancellationException("download cancelled")
            val id = store.newRegionId(geo.name)
            val bbox = PoiLogic.bboxAround(geo.lat, geo.lon, geo.radiusKm)
            var record = RegionRecord(
                id = id,
                cityName = geo.name,
                countryLabel = countryLabel,
                lat = geo.lat,
                lon = geo.lon,
                bbox = bbox,
                estimatedBytes = totalEstimate / resolved.size.coerceAtLeast(1),
            )
            val dir = store.regionDir(id)

            onProgress(DownloadProgress(id, geo.name, globalDone, totalEstimate, "설명", pct(globalDone, totalEstimate)))
            val geoInfo = geocoder.search(geo.name, countryLabel)
            val desc = geoInfo?.description.orEmpty()
            File(dir, "description.txt").writeText(desc)
            record = record.copy(descriptionKo = desc)
            globalDone += 50_000
            if (cancelled) throw CancellationException("download cancelled")

            onProgress(DownloadProgress(id, geo.name, globalDone, totalEstimate, "POI", pct(globalDone, totalEstimate)))
            val pois = overpass.fetchPois(bbox)
            val bundle = PoiBundle(
                region = id,
                labelKo = geo.name,
                bbox = bbox,
                count = pois.size,
                pois = pois,
            )
            File(dir, "poi.json").writeText(json.encodeToString(bundle))
            globalDone += 120_000
            if (cancelled) throw CancellationException("download cancelled")

            onProgress(DownloadProgress(id, geo.name, globalDone, totalEstimate, "지도", pct(globalDone, totalEstimate)))
            val zipFile = File(dir, "tiles.zip")
            val (bytes, tileCount) = downloadTiles(bbox, zipFile) { done ->
                onProgress(
                    DownloadProgress(
                        id, geo.name,
                        globalDone + done,
                        totalEstimate,
                        "지도",
                        pct(globalDone + done, totalEstimate),
                    ),
                )
            }
            globalDone += bytes
            if (cancelled) throw CancellationException("download cancelled")
            if (tileCount == 0) continue

            record = record.copy(
                downloadBytes = bytes,
                downloadComplete = true,
            )
            store.saveRegion(record)
            results += record
            onProgress(DownloadProgress(id, geo.name, globalDone, totalEstimate, "완료", pct(globalDone, totalEstimate)))
        }
        results
    }

    private fun pct(done: Long, total: Long): Int =
        if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 100)

    private suspend fun downloadTiles(
        bbox: Bbox,
        outZip: File,
        onPartial: (Long) -> Unit,
    ): Pair<Long, Int> = withContext(Dispatchers.IO) {
        val zooms = listOf(10, 11, 12, 13)
        var bytes = 0L
        var count = 0
        ZipOutputStream(FileOutputStream(outZip)).use { zip ->
            for (z in zooms) {
                val xr = MapMath.tileXRange(bbox, z)
                val yr = MapMath.tileYRange(bbox, z)
                for (x in xr) {
                    for (y in yr) {
                        coroutineContext.ensureActive()
                        if (cancelled) throw CancellationException("download cancelled")
                        val sub = SUBS[(x + y) % SUBS.size]
                        val url = "https://$sub.basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png"
                        val req = Request.Builder()
                            .url(url)
                            .header("User-Agent", "WalkingOfflineGuide/1.0")
                            .build()
                        val png = runCatching {
                            http.newCall(req).execute().use { r ->
                                if (!r.isSuccessful) return@use null
                                r.body?.bytes()?.takeIf { it.size > 8000 }
                            }
                        }.getOrNull() ?: continue
                        zip.putNextEntry(ZipEntry("tiles/$z/$x/$y.png"))
                        zip.write(png)
                        zip.closeEntry()
                        bytes += png.size
                        count++
                        onPartial(bytes)
                        Thread.sleep(120)
                    }
                }
            }
        }
        bytes to count
    }

    companion object {
        private val SUBS = listOf("a", "b", "c", "d")
    }
}
