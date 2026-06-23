package com.mcpauto.walkingofflineguide.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class PoiRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRegionBundle(regionId: String): PoiBundle {
        val file = poiFile(regionId)
        if (!file.exists()) return PoiBundle(region = regionId)
        return runCatching {
            json.decodeFromString(PoiBundle.serializer(), file.readText())
        }.getOrElse {
            SafeStorage.quarantineCorrupt(file)
            PoiBundle(region = regionId)
        }
    }

    fun saveRegionBundle(bundle: PoiBundle) {
        if (bundle.region.isBlank()) return
        val file = poiFile(bundle.region)
        SafeStorage.atomicWriteText(file, json.encodeToString(PoiBundle.serializer(), bundle))
    }

    private fun poiFile(regionId: String): File =
        File(context.filesDir, "walking_data/regions/$regionId/poi.json")
}
