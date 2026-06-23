package com.mcpauto.walkingofflineguide.network

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.Poi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OverpassClient {
    suspend fun fetchPois(bbox: Bbox, homeLangTag: String = "ko"): List<Poi> = withContext(Dispatchers.IO) {
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
        runCatching {
            parseElements(OverpassHttp.postQuery(q), homeLangTag)
        }.getOrElse { throw it }
    }

    private fun parseElements(json: JSONObject, homeLangTag: String): List<Poi> {
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
            val localName = resolveLocalName(tags, homeLangTag) ?: continue
            val homeTag = "name:$homeLangTag"
            val altHomeTag = when (homeLangTag) {
                "zh-TW" -> "name:zh-Hant"
                "zh", "zh-CN" -> "name:zh"
                else -> homeTag
            }
            val nameHome = listOf(
                tags.optString(homeTag),
                tags.optString(altHomeTag),
                if (homeLangTag == "ko") tags.optString("name:ko") else "",
            ).firstOrNull { it.isNotBlank() }
            val descLocal = tags.optString("description").ifBlank { null }
                ?: tags.optString("description:$homeLangTag").ifBlank { null }
            out += Poi(
                id = "${el.optString("type")}/${el.optLong("id")}",
                kind = kind,
                nameKo = localName,
                nameHome = nameHome,
                lat = lat,
                lon = lon,
                descriptionKo = descLocal,
                descriptionHome = if (nameHome != null && descLocal != null &&
                    tags.optString("description:$homeLangTag").isNotBlank()
                ) {
                    tags.optString("description:$homeLangTag")
                } else {
                    null
                },
                tourism = tourism,
            )
        }
        return out.distinctBy { it.id }
    }

    /** 한국 등 name:ko만 있는 OSM 노드도 수집 */
    private fun resolveLocalName(tags: JSONObject, homeLangTag: String): String? {
        val langTag = "name:$homeLangTag"
        return listOf(
            tags.optString("name"),
            tags.optString(langTag),
            tags.optString("name:ko"),
            tags.optString("name:en"),
            tags.optString("brand"),
            tags.optString("official_name"),
        ).firstOrNull { it.isNotBlank() }
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
