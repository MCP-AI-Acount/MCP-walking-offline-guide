package com.mcpauto.walkingofflineguide.download

import android.content.Context
import android.net.Uri
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM
import com.mcpauto.walkingofflineguide.data.SafeStorage
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Serializable
data class RegionImportManifest(
    val schema: String = "",
    @SerialName("city_name") val cityName: String = "",
    @SerialName("country_label") val countryLabel: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val bbox: Bbox = Bbox(),
    @SerialName("region_id") val regionId: String? = null,
)

data class RegionImportResult(
    val region: RegionRecord,
    val summary: String,
)

/** PC·USB로 받은 지역 번들(zip) → 앱 내부 저장소로 가져오기 */
class RegionImportManager(
    private val context: Context,
    private val store: TripStore = TripStore(context),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromUri(uri: Uri): RegionImportResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "region_import_${System.currentTimeMillis()}").also { it.mkdirs() }
        try {
            extractZip(uri, staging)
            val bundleRoot = findBundleRoot(staging)
            val manifestFile = File(bundleRoot, MANIFEST_NAME)
            if (!manifestFile.exists()) {
                throw IllegalArgumentException(
                    "import_manifest.json 이 없습니다. PC에서 export_region_bundle.py 로 만든 zip인지 확인해 주세요.",
                )
            }
            val manifest = json.decodeFromString<RegionImportManifest>(manifestFile.readText())
            if (manifest.schema.isNotBlank() && manifest.schema != SCHEMA) {
                throw IllegalArgumentException("지원하지 않는 번들 형식입니다: ${manifest.schema}")
            }
            require(manifest.cityName.isNotBlank()) { "도시 이름(city_name)이 비어 있습니다." }

            val poiBundle = readPoiBundle(File(bundleRoot, "poi.json"))
            val lat = manifest.lat.takeIf { it != 0.0 }
                ?: poiBundle?.pois?.firstOrNull()?.lat
                ?: 0.0
            val lon = manifest.lon.takeIf { it != 0.0 }
                ?: poiBundle?.pois?.firstOrNull()?.lon
                ?: 0.0
            val bbox = manifest.bbox.takeIf { it.north > it.south }
                ?: poiBundle?.bbox?.takeIf { it.north > it.south }
                ?: if (lat != 0.0 && lon != 0.0) {
                    PoiLogic.bboxAround(lat, lon, STOP_DOWNLOAD_RADIUS_KM)
                } else {
                    Bbox()
                }

            val hasTiles = hasTilePayload(bundleRoot)
            require(hasTiles) { "지도 타일(tiles.zip 또는 tiles/ 폴더)이 없습니다." }

            val existingId = manifest.regionId?.takeIf { it.isNotBlank() }
                ?: store.findRegionIdForCity(manifest.cityName)
            val regionId = store.stableRegionId(manifest.cityName, existingId)
            val destDir = store.regionDir(regionId)

            copyOptional(bundleRoot, destDir, "description.txt")
            copyOptional(bundleRoot, destDir, "poi.json")
            copyOptional(bundleRoot, destDir, "routing_graph.json")
            importTiles(bundleRoot, destDir)

            val bytes = tileBytes(destDir)
            val desc = File(destDir, "description.txt").takeIf { it.exists() }?.readText().orEmpty()

            val record = RegionRecord(
                id = regionId,
                cityName = manifest.cityName,
                countryLabel = manifest.countryLabel,
                lat = lat,
                lon = lon,
                bbox = bbox,
                downloadComplete = true,
                downloadBytes = bytes,
                estimatedBytes = bytes,
                descriptionKo = desc,
            )
            store.saveRegion(record)
            markDownloadJobCityFinished(manifest.cityName)

            RegionImportResult(
                region = record,
                summary = "「${manifest.cityName}」 가져오기 완료 · ${RegionDownloadManager.formatSize(bytes)}",
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractZip(uri: Uri, dest: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("zip 파일을 열 수 없습니다.")
        input.use { stream ->
            ZipInputStream(stream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = safeOutputFile(dest, entry.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }

    private fun safeOutputFile(root: File, entryName: String): File {
        val out = File(root, entryName)
        val rootPath = root.canonicalPath
        val outPath = out.canonicalPath
        if (!outPath.startsWith(rootPath + File.separator) && outPath != rootPath) {
            throw SecurityException("zip 경로가 올바르지 않습니다: $entryName")
        }
        return out
    }

    /** zip 루트 또는 하위 한 단계에서 manifest 탐색 */
    private fun findBundleRoot(staging: File): File {
        if (File(staging, MANIFEST_NAME).exists()) return staging
        staging.listFiles()?.filter { it.isDirectory }?.forEach { child ->
            if (File(child, MANIFEST_NAME).exists()) return child
        }
        return staging
    }

    private fun readPoiBundle(file: File): PoiBundle? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<PoiBundle>(file.readText()) }.getOrNull()
    }

    private fun hasTilePayload(root: File): Boolean {
        if (File(root, "tiles.zip").exists()) return true
        val tilesDir = File(root, "tiles")
        return tilesDir.isDirectory && tilesDir.walkTopDown().any {
            it.isFile && it.extension.equals("png", ignoreCase = true) && it.length() > 64L
        }
    }

    private fun copyOptional(srcRoot: File, destDir: File, name: String) {
        val src = File(srcRoot, name)
        if (!src.exists()) return
        val dest = File(destDir, name)
        src.copyTo(dest, overwrite = true)
    }

    private fun importTiles(srcRoot: File, destDir: File) {
        val srcZip = File(srcRoot, "tiles.zip")
        val srcTiles = File(srcRoot, "tiles")
        val destZip = File(destDir, "tiles.zip")
        val destTiles = File(destDir, "tiles")

        when {
            srcZip.exists() -> {
                destZip.parentFile?.mkdirs()
                srcZip.copyTo(destZip, overwrite = true)
                if (destTiles.exists()) destTiles.deleteRecursively()
            }
            srcTiles.isDirectory -> {
                if (destTiles.exists()) destTiles.deleteRecursively()
                srcTiles.copyRecursively(destTiles, overwrite = true)
                SafeStorage.zipTilesFolder(destTiles, destZip)
            }
        }
    }

    private fun tileBytes(destDir: File): Long {
        val zip = File(destDir, "tiles.zip")
        if (zip.exists()) return zip.length()
        val tilesDir = File(destDir, "tiles")
        if (!tilesDir.isDirectory) return 0L
        return tilesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private suspend fun markDownloadJobCityFinished(cityName: String) {
        val job = store.loadDownloadJob() ?: return
        if (!job.active) return
        val match = job.stops.any { it.name.equals(cityName, ignoreCase = true) }
        if (!match) return
        val finished = (job.finishedCityNames + cityName).distinctBy { it.lowercase() }
        if (finished.size >= job.stops.size) {
            store.clearDownloadJob()
        } else {
            store.saveDownloadJob(job.copy(finishedCityNames = finished))
        }
    }

    companion object {
        const val SCHEMA = "wog-region-import-v1"
        const val MANIFEST_NAME = "import_manifest.json"
        const val MIME_ZIP = "application/zip"
    }
}
