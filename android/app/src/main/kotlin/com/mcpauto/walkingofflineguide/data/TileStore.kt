package com.mcpauto.walkingofflineguide.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** 오프라인 타일 — zip/폴더 정본, 디코드는 LRU(on-demand) */
class TileStore(private val context: Context) {
    private val memory = object : LruCache<String, Bitmap>(MEMORY_TILE_CAP) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }
    private val zoomSet = mutableSetOf<Int>()
    private val indexedKeys = HashSet<String>()
    private val mutex = Mutex()
    private var loadedRegionId: String? = null
    private var zipFile: File? = null
    private var tilesDir: File? = null

    val tileCount: Int get() = indexedKeys.size
    fun availableZooms(): Set<Int> = zoomSet.toSet()
    fun isReady(): Boolean = indexedKeys.isNotEmpty()

    suspend fun loadRegion(regionId: String, force: Boolean = false): Int = mutex.withLock {
        if (!force && loadedRegionId == regionId && indexedKeys.isNotEmpty()) return indexedKeys.size
        memory.evictAll()
        zoomSet.clear()
        indexedKeys.clear()
        loadedRegionId = regionId
        val regionDir = File(context.filesDir, "walking_data/regions/$regionId")
        val zip = File(regionDir, "tiles.zip")
        val dir = File(regionDir, "tiles")
        withContext(Dispatchers.IO) {
            SafeStorage.cleanupStaleTmp(regionDir, "tiles.zip")
            val hasFolderTiles = dir.isDirectory && runCatching {
                dir.walkTopDown().any { it.isFile && it.extension.equals("png", ignoreCase = true) }
            }.getOrDefault(false)
            val zipReadable = zip.exists() && !SafeStorage.isWriteInProgress(zip)
            when {
                zipReadable -> {
                    val indexed = runCatching { indexZip(zip) }.isSuccess
                    if (!indexed) {
                        SafeStorage.quarantineCorrupt(zip)
                        if (hasFolderTiles) {
                            indexFolder(dir)
                        } else {
                            clearLoadedRegion()
                        }
                    }
                }
                hasFolderTiles -> indexFolder(dir)
                else -> clearLoadedRegion()
            }
        }
        indexedKeys.size
    }

    fun get(zoom: Int, x: Int, y: Int): Bitmap? {
        val key = "$zoom/$x/$y"
        if (key !in indexedKeys) return null
        memory.get(key)?.let { return it }
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        val bmp = when {
            tilesDir != null -> decodeFromFolder(tilesDir!!, key, opts)
            zipFile != null -> decodeFromZip(zipFile!!, key, opts)
            else -> null
        }
        return bmp?.also { memory.put(key, it) }
    }

    private fun decodeFromFolder(dir: File, key: String, opts: BitmapFactory.Options): Bitmap? {
        val file = File(dir, "$key.png")
        if (!file.exists() || file.length() < 64L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    private fun decodeFromZip(zip: File, key: String, opts: BitmapFactory.Options): Bitmap? {
        val entryPath = "tiles/$key.png"
        return runCatching {
            ZipInputStream(zip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == entryPath) {
                        val bytes = zis.readBytes()
                        if (bytes.size < 64) return@use null
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    }
                    runCatching { zis.closeEntry() }
                    entry = zis.nextEntry
                }
            }
            null
        }.getOrNull()
    }

    private fun clearLoadedRegion() {
        loadedRegionId = null
        zipFile = null
        tilesDir = null
    }

    private fun indexZip(file: File) {
        zipFile = file
        tilesDir = null
        ZipInputStream(file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".png", ignoreCase = true)) {
                    val tileKey = entry.name.removePrefix("tiles/").removeSuffix(".png")
                    registerKey(tileKey)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun indexFolder(dir: File) {
        tilesDir = dir
        zipFile = null
        runCatching {
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
                .forEach { file ->
                    if (file.length() < 64L) return@forEach
                    val tileKey = file.relativeTo(dir).path.replace('\\', '/').removeSuffix(".png")
                    if (tileKey.count { it == '/' } == 2) registerKey(tileKey)
                }
        }
    }

    private fun registerKey(key: String) {
        indexedKeys.add(key)
        key.split("/").firstOrNull()?.toIntOrNull()?.let { zoomSet.add(it) }
    }

    companion object {
        private const val MEMORY_TILE_CAP = 96
    }
}
