package com.mcpauto.walkingofflineguide.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import com.mcpauto.walkingofflineguide.logic.TileFetchOrdering
import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 모국+WiFi — Carto 타일 온라인 (다운로드 bbox 밖 GPS 주변) */
class OnlineTileProvider(context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val cache = android.util.LruCache<String, Bitmap>(96)
    private val inflight = mutableSetOf<String>()
    private val mutex = Mutex()
    private var revision = 0

    fun revision(): Int = revision

    fun get(z: Int, x: Int, y: Int): Bitmap? = cache.get(key(z, x, y))

    suspend fun prefetchForCamera(
        camera: MapCamera,
        screenW: Int,
        screenH: Int,
        anchorLat: Double? = null,
        anchorLon: Double? = null,
        clipBbox: Bbox? = null,
    ) = withContext(Dispatchers.IO) {
        val flat = camera.copy(bearingDeg = 0f)
        val w = screenW.toFloat().coerceAtLeast(1f)
        val z = MapCameraMath.pickRenderZoom(
            flat.centerLat,
            w,
            flat.visibleSpanM,
            RegionDownloadManager.ONLINE_ZOOMS,
        )
        val renderCam = flat.copy(baseZoom = z)
        val (xRange, yRange) = MapCameraMath.visibleTileRange(renderCam, w, screenH.toFloat())
        val cells = buildList {
            for (x in xRange) {
                for (y in yRange) {
                    if (clipBbox != null && !MapCameraMath.tileIntersectsBbox(x, y, z, clipBbox)) continue
                    add(TileFetchOrdering.TileCell(z, x, y))
                }
            }
        }
        val ordered = TileFetchOrdering.sortedNearClockwise(cells, anchorLat, anchorLon)
        var added = false
        for (cell in ordered) {
            val k = key(cell.z, cell.x, cell.y)
            if (cache.get(k) != null) continue
            mutex.withLock {
                if (k in inflight) return@withLock
                inflight.add(k)
            }
            val bmp = fetchTile(cell.z, cell.x, cell.y)
            mutex.withLock { inflight.remove(k) }
            if (bmp != null) {
                cache.put(k, bmp)
                added = true
            }
        }
        if (anchorLat != null && anchorLon != null && z < 18) {
            val inner = ordered.take(24).mapNotNull { c ->
                val nx = c.x * 2 + 1
                val ny = c.y * 2 + 1
                val nz = (c.z + 1).coerceAtMost(18)
                if (nz <= c.z) null else TileFetchOrdering.TileCell(nz, nx, ny)
            }.distinctBy { "${it.z}/${it.x}/${it.y}" }
            for (cell in TileFetchOrdering.sortedNearClockwise(inner, anchorLat, anchorLon)) {
                if (clipBbox != null &&
                    !MapCameraMath.tileIntersectsBbox(cell.x, cell.y, cell.z, clipBbox)
                ) {
                    continue
                }
                val k = key(cell.z, cell.x, cell.y)
                if (cache.get(k) != null) continue
                val bmp = fetchTile(cell.z, cell.x, cell.y) ?: continue
                cache.put(k, bmp)
                added = true
            }
        }
        if (added) revision++
    }

    private fun fetchTile(z: Int, x: Int, y: Int): Bitmap? {
        val sub = SUBS[(x + y) % SUBS.size]
        val url = "https://$sub.basemaps.cartocdn.com/rastertiles/voyager_nolabels/$z/$x/$y@2x.png"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (home-live)")
            .build()
        return runCatching {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val bytes = r.body?.bytes()?.takeIf { it.size > 8000 } ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun key(z: Int, x: Int, y: Int) = "$z/$x/$y"

    companion object {
        private val SUBS = listOf("a", "b", "c", "d")
    }
}
