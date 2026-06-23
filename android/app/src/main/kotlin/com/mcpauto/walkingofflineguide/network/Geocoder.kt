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
    val placeType: String = "",
)

class NominatimGeocoder {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 도시·거리·장소·명소 — WiFi 설정 화면 자동완성용 */
    suspend fun searchSuggestions(
        query: String,
        countryHint: String = "",
        limit: Int = 8,
    ): List<GeoResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val q = buildString {
            append(trimmed)
            if (countryHint.isNotBlank()) append(", $countryHint")
        }
        val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=$limit&addressdetails=1&accept-language=ko,en"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONArray(resp.body?.string().orEmpty())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val display = o.optString("display_name")
                if (display.isBlank()) return@mapNotNull null
                val short = o.optString("name").ifBlank {
                    display.split(",").firstOrNull()?.trim().orEmpty()
                }.ifBlank { trimmed }
                GeoResult(
                    name = short,
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    displayName = display,
                    description = display.split(",").take(3).joinToString(" · "),
                    placeType = o.optString("type"),
                )
            }
        }
    }

    suspend fun search(cityQuery: String, countryHint: String = ""): GeoResult? =
        searchSuggestions(cityQuery, countryHint, limit = 1).firstOrNull()

    suspend fun reverse(lat: Double, lon: Double): GeoResult? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1&accept-language=ko,en"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val o = JSONObject(resp.body?.string().orEmpty())
            val display = o.optString("display_name")
            val addr = o.optJSONObject("address")
            val adminCity = adminCityFromAddress(addr, display)
            GeoResult(
                name = adminCity,
                lat = lat,
                lon = lon,
                displayName = display,
                description = adminCity,
            )
        }
    }

    /** 시·광역시급 1개 — 도(경기도)·동·구·도로는 제외 */
    private fun adminCityFromAddress(addr: JSONObject?, display: String): String {
        if (addr != null) {
            val city = addr.optString("city").takeIf { it.isNotBlank() }
            val town = addr.optString("town").takeIf { it.isNotBlank() }
            val municipality = addr.optString("municipality").takeIf { it.isNotBlank() }
            val state = addr.optString("state").takeIf { it.isNotBlank() }

            city?.takeIf { isSiLevelAdmin(it) }?.let { return normalizeAdminCity(it) }
            town?.takeIf { isSiLevelAdmin(it) }?.let { return normalizeAdminCity(it) }
            municipality?.takeIf { isSiLevelAdmin(it) }?.let { return normalizeAdminCity(it) }
            // city 필드가 있으면(해외 등) 도보다 우선
            city?.let { return normalizeAdminCity(it) }
            town?.let { return normalizeAdminCity(it) }
            municipality?.let { return normalizeAdminCity(it) }
            // 시가 없을 때만 도/주
            state?.takeIf { !isGuOrDong(it) && !isRoadLike(it) }?.let { return normalizeAdminCity(it) }
        }
        val parts = display.split(",").map { it.trim() }.filter { it.isNotBlank() }
        parts.firstOrNull { isSiLevelAdmin(it) }?.let { return normalizeAdminCity(it) }
        parts.firstOrNull { !isGuOrDong(it) && !isRoadLike(it) && !it.endsWith("도") }
            ?.let { return normalizeAdminCity(it) }
        return parts.firstOrNull()?.let { normalizeAdminCity(it) }.orEmpty()
    }

    private fun isSiLevelAdmin(raw: String): Boolean {
        if (raw.endsWith("특별시") || raw.endsWith("광역시") || raw.endsWith("특별자치시")) return true
        if (raw.endsWith("시") && !raw.endsWith("특별시") && !raw.endsWith("광역시")) return true
        if (raw.endsWith("도") && !raw.endsWith("특별자치도")) return false
        if (isGuOrDong(raw) || isRoadLike(raw)) return false
        return raw.length in 2..24
    }

    private fun isGuOrDong(raw: String): Boolean =
        raw.endsWith("구") || raw.endsWith("동") || raw.endsWith("읍") || raw.endsWith("면")

    private fun isRoadLike(raw: String): Boolean =
        raw.endsWith("로") || raw.endsWith("길") || raw.contains("번길")

    private fun normalizeAdminCity(raw: String): String {
        var s = raw.trim()
        s = s.removeSuffix("특별자치시")
        s = s.removeSuffix("특별자치도")
        s = s.removeSuffix("특별시")
        s = s.removeSuffix("광역시")
        if (s.endsWith("시") && s.length > 2) s = s.dropLast(1)
        return s.trim()
    }

}

/** GPS 지도 상단 — 시·광역시급 행정단위 1개 (서울·평택 등) */
fun GeoResult.adminCityLabel(): String =
    name.ifBlank {
        adminCityFromDisplay(displayName)
    }

private fun adminCityFromDisplay(display: String): String {
    val parts = display.split(",").map { it.trim() }.filter { it.isNotBlank() }
    return parts.firstOrNull { part ->
        part.endsWith("특별시") || part.endsWith("광역시") || part.endsWith("시") ||
            (part.length in 2..20 && !part.endsWith("도") && !part.endsWith("구"))
    }?.let { raw ->
        raw.removeSuffix("특별시").removeSuffix("광역시").removeSuffix("시").trim()
    } ?: parts.firstOrNull().orEmpty()
}
