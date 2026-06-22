package com.mcpauto.walkingofflineguide.network

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OverpassClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPois(bbox: Bbox): List<Poi> = withContext(Dispatchers.IO) {
        val q = """
            [out:json][timeout:45];
            (
              node["amenity"~"restaurant|cafe|fast_food|bar|food_court"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              node["tourism"~"hotel|guest_house|hostel|motel|apartment"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              node["amenity"~"hotel|guest_house|hostel"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              node["tourism"~"attraction|museum|viewpoint|artwork|gallery|theme_park|zoo"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              node["historic"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              node["heritage"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              way["tourism"~"attraction|museum|hotel"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
              way["historic"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            );
            out center;
        """.trimIndent()
        val body = FormBody.Builder().add("data", q).build()
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .post(body)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            parseElements(JSONObject(resp.body?.string().orEmpty()))
        }
    }

    private fun parseElements(json: JSONObject): List<Poi> {
        val arr = json.optJSONArray("elements") ?: return emptyList()
        val out = mutableListOf<Poi>()
        for (i in 0 until arr.length()) {
            val el = arr.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val lat = when (el.optString("type")) {
                "node" -> el.optDouble("lat")
                else -> el.optJSONObject("center")?.optDouble("lat") ?: Double.NaN
            }
            val lon = when (el.optString("type")) {
                "node" -> el.optDouble("lon")
                else -> el.optJSONObject("center")?.optDouble("lon") ?: Double.NaN
            }
            if (lat.isNaN() || lon.isNaN()) continue
            val (kind, tourism) = classify(tags)
            val name = listOf(
                tags.optString("name:ko"),
                tags.optString("name"),
                tags.optString("brand"),
            ).firstOrNull { it.isNotBlank() } ?: continue
            out += Poi(
                id = "${el.optString("type")}/${el.optLong("id")}",
                kind = kind,
                nameKo = name,
                lat = lat,
                lon = lon,
                descriptionKo = tags.optString("description").ifBlank { null },
                tourism = tourism,
            )
        }
        return out.distinctBy { it.id }
    }

    private fun classify(tags: JSONObject): Pair<String, String?> {
        val amenity = tags.optString("amenity")
        val tourism = tags.optString("tourism")
        val historic = tags.optString("historic")
        return when {
            tourism in HOTEL || amenity in HOTEL -> "hotel" to tourism.ifBlank { amenity }
            amenity in REST -> "restaurant" to amenity
            else -> "attraction" to tourism.ifBlank { historic.ifBlank { null } }
        }
    }

    companion object {
        private val REST = setOf("restaurant", "cafe", "fast_food", "bar", "food_court")
        private val HOTEL = setOf("hotel", "guest_house", "hostel", "motel", "apartment")
    }
}
