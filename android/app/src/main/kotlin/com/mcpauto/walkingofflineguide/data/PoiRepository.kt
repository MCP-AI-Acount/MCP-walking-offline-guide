package com.mcpauto.walkingofflineguide.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class PoiRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRegionBundle(regionId: String): PoiBundle {
        val file = File(context.filesDir, "walking_data/regions/$regionId/poi.json")
        if (!file.exists()) return PoiBundle(region = regionId)
        return json.decodeFromString(PoiBundle.serializer(), file.readText())
    }
}
