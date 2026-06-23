package com.mcpauto.walkingofflineguide.download

import android.content.Context
import com.mcpauto.walkingofflineguide.data.DownloadJobState
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import kotlinx.serialization.json.Json
import java.io.File

/** UI — 디스크 기준 부분 다운로드·타일 진행 (서비스 중단 시) */
object DownloadProgressReader {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun hasPartialWork(context: Context, job: DownloadJobState): Boolean {
        val store = TripStore(context)
        val pending = job.stops.filter { it.name !in job.finishedCityNames.toSet() }
        if (pending.isEmpty()) return job.stops.isNotEmpty()
        return pending.any { stop ->
            val id = store.stableRegionId(stop.name, store.findRegionIdForCity(stop.name))
            val dir = store.regionDir(id)
            dir.exists() && (
                File(dir, "poi.json").exists() ||
                    File(dir, "tiles_progress.json").exists() ||
                    File(dir, "tiles").exists()
                )
        }
    }

    suspend fun tileProgressLine(context: Context, job: DownloadJobState): String? {
        val store = TripStore(context)
        val stop = job.stops.firstOrNull { it.name !in job.finishedCityNames.toSet() } ?: return null
        val id = store.stableRegionId(stop.name, store.findRegionIdForCity(stop.name))
        val dir = store.regionDir(id)
        val progressFile = File(dir, "tiles_progress.json")
        if (!progressFile.exists()) {
            return when {
                File(dir, "poi.json").exists() && !File(dir, "tiles.zip").exists() -> "지도 타일 준비 중…"
                else -> null
            }
        }
        val done = runCatching {
            json.decodeFromString<List<String>>(progressFile.readText()).size
        }.getOrDefault(0)
        val lat = stop.lat.takeIf { it != 0.0 } ?: return "$done 타일 받음"
        val lon = stop.lon.takeIf { it != 0.0 } ?: return "$done 타일 받음"
        val bbox = RegionDownloadManager.stopBbox(stop)
        var total = 0
        RegionDownloadManager.TILE_ZOOMS.forEach { z ->
            val xr = MapMath.tileXRange(bbox, z)
            val yr = MapMath.tileYRange(bbox, z)
            total += (xr.last - xr.first + 1) * (yr.last - yr.first + 1)
        }
        val pct = if (total > 0) (done * 100 / total).coerceIn(0, 99) else 0
        return "지도 타일 $done/$total ($pct%)"
    }
}
