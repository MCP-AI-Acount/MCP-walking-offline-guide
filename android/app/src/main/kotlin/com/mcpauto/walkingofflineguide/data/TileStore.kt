package com.mcpauto.walkingofflineguide.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

class TileStore(private val context: Context) {
    private val tiles = ConcurrentHashMap<String, Bitmap>()
    private val zoomSet = mutableSetOf<Int>()
    private val mutex = Mutex()
    private var loadedRegionId: String? = null

    val tileCount: Int get() = tiles.size
    fun availableZooms(): Set<Int> = zoomSet.toSet()
    fun isReady(): Boolean = tiles.isNotEmpty()

    suspend fun loadRegion(regionId: String): Int = mutex.withLock {
        if (loadedRegionId == regionId && tiles.isNotEmpty()) return tiles.size
        tiles.clear()
        zoomSet.clear()
        loadedRegionId = regionId
        val zipFile = File(context.filesDir, "walking_data/regions/$regionId/tiles.zip")
        if (!zipFile.exists()) {
            loadedRegionId = null
            return 0
        }
        withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            ZipInputStream(zipFile.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".png")) {
                        val key = entry.name.removePrefix("tiles/").removeSuffix(".png")
                        val parts = key.split("/")
                        if (parts.size == 3) parts[0].toIntOrNull()?.let { zoomSet.add(it) }
                        val bytes = zip.readBytes()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let {
                            tiles[key] = it
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        tiles.size
    }

    fun get(zoom: Int, x: Int, y: Int): Bitmap? = tiles["$zoom/$x/$y"]
}
