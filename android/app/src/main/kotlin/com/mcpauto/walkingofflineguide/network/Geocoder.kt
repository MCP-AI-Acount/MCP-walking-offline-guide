package com.mcpauto.walkingofflineguide.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GeoResult(
    val name: String,
    val lat: Double,
    val lon: Double,
    val displayName: String,
    val description: String,
)

class NominatimGeocoder {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(cityQuery: String, countryHint: String = ""): GeoResult? = withContext(Dispatchers.IO) {
        val q = buildString {
            append(cityQuery.trim())
            if (countryHint.isNotBlank()) append(", $countryHint")
        }
        val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1&accept-language=ko,en"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val arr = JSONArray(resp.body?.string().orEmpty())
            if (arr.length() == 0) return@withContext null
            val o = arr.getJSONObject(0)
            val display = o.optString("display_name")
            GeoResult(
                name = cityQuery.trim(),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                displayName = display,
                description = display.split(",").take(3).joinToString(" · "),
            )
        }
    }

    suspend fun reverse(lat: Double, lon: Double): GeoResult? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=ko,en"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val o = JSONObject(resp.body?.string().orEmpty())
            val display = o.optString("display_name")
            val addr = o.optJSONObject("address")
            val country = addr?.optString("country").orEmpty()
            GeoResult(
                name = country.ifBlank { display.split(",").lastOrNull()?.trim().orEmpty() },
                lat = lat,
                lon = lon,
                displayName = display,
                description = display.split(",").take(3).joinToString(" · "),
            )
        }
    }
}
