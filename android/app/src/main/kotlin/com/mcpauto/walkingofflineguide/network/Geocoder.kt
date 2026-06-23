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
    /** reverse 시 파싱된 행정 계층 — [adminPlaceLabel] 정본 */
    val adminParts: List<String> = emptyList(),
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
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1&zoom=16&accept-language=ko,en"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "WalkingOfflineGuide/1.0 (Android)")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val o = JSONObject(resp.body?.string().orEmpty())
            val display = o.optString("display_name")
            val addr = o.optJSONObject("address")
            val adminParts = parseAdminHierarchy(display, addr)
            val adminCity = adminParts.firstOrNull()?.ifBlank { adminCityFromAddress(addr, display) }
                ?: adminCityFromAddress(addr, display)
            val adminLabel = formatAdminPlaceLabel(adminParts).ifBlank { adminCity }
            GeoResult(
                name = adminCity,
                lat = lat,
                lon = lon,
                displayName = display,
                description = adminLabel,
                adminParts = adminParts,
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

/** GPS 지도 상단 — 시·구·동 등 행정 계층 (도로·번지 제외, 동급까지) */
fun GeoResult.adminPlaceLabel(): String {
    if (adminParts.isNotEmpty()) {
        return formatAdminPlaceLabel(adminParts)
    }
    if (description.isNotBlank() && description.contains("-") && !description.contains(" · ")) {
        return description
    }
    return formatAdminPlaceLabel(parseAdminHierarchy(displayName))
}

/** Nominatim reverse 결과에서 행정 계층 [대·중·소] — POI·상호명 제외 */
fun parseAdminHierarchy(displayName: String, addr: org.json.JSONObject? = null): List<String> {
    if (addr != null) {
        parseKoreanHierarchy(addr)?.let { return it }
    }
    var large = ""
    var medium = ""
    var small = ""
    if (addr != null) {
        large = pickLargeAdmin(addr)
        medium = pickMediumAdmin(addr)
        small = pickSmallAdmin(addr)
    }
    if (large.isBlank() || medium.isBlank() || small.isBlank()) {
        val parts = displayName.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && !isCountryPart(it) && !looksLikePoiName(it) }
        for (part in parts.asReversed()) {
            when {
                large.isBlank() && isLargeAdmin(part) -> large = normalizeLargeAdmin(part)
                medium.isBlank() && isMediumAdmin(part) -> medium = part
                small.isBlank() && isSmallAdmin(part) -> small = part
            }
        }
    }
    return listOf(large, medium, small).filter { it.isNotBlank() }.distinct().take(3)
}

fun formatAdminPlaceLabel(parts: List<String>): String =
    parts.filter { it.isNotBlank() }
        .take(3)
        .map { stripAdminSuffix(it) }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("-")

/** 시·구·동 접미사 제거 — 서울-송파-방이 */
fun stripAdminSuffix(raw: String): String {
    var s = raw.trim()
    s = s.removeSuffix("특별자치시")
    s = s.removeSuffix("특별자치도")
    s = s.removeSuffix("특별시")
    s = s.removeSuffix("광역시")
    if (s.endsWith("시") && s.length > 2) s = s.dropLast(1)
    if (s.endsWith("구") && s.length > 2) s = s.dropLast(1)
    if (s.endsWith("군") && s.length > 2) s = s.dropLast(1)
    if (s.endsWith("동") && s.length > 2) s = s.dropLast(1)
    if (s.endsWith("읍") && s.length > 2) s = s.dropLast(1)
    if (s.endsWith("면") && s.length > 2) s = s.dropLast(1)
    if (s.endsWith("리") && s.length > 2) s = s.dropLast(1)
    s = s.replace(Regex("\\d+$"), "")
    return s.trim()
}

private val KOREAN_METRO_SHORT = setOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종")

/** 대한민국 Nominatim addr — 서울-송파-방이 */
private fun parseKoreanHierarchy(addr: org.json.JSONObject): List<String>? {
    val cc = addr.optString("country_code").lowercase()
    val country = addr.optString("country")
    val isKr = cc == "kr" || country.contains("한국") || country.contains("Korea", ignoreCase = true)
    if (!isKr) return null

    val state = addr.optString("state").trim()
    val city = addr.optString("city").trim()
    val borough = addr.optString("borough").trim()
        .ifBlank { addr.optString("city_district").trim() }
    val dong = listOf("quarter", "suburb", "neighbourhood", "village", "hamlet", "town")
        .firstNotNullOfOrNull { key -> addr.optString(key).trim().takeIf { it.isNotBlank() } }
        .orEmpty()

    val large = when {
        state.isNotBlank() -> normalizeLargeAdmin(state).ifBlank { stripAdminSuffix(state) }
        city.isNotBlank() -> normalizeLargeAdmin(city).ifBlank { stripAdminSuffix(city) }
        else -> ""
    }
    val medium = stripAdminSuffix(borough)
    val small = stripAdminSuffix(dong)
    val parts = listOf(large, medium, small).filter { it.isNotBlank() }.distinct()
    return parts.takeIf { it.isNotEmpty() }
}

private fun pickLargeAdmin(addr: org.json.JSONObject): String {
    listOf("state", "city", "town", "municipality", "province", "region").forEach { key ->
        addr.optString(key).takeIf { it.isNotBlank() && isLargeAdmin(it) }?.let {
            return normalizeLargeAdmin(it)
        }
    }
    return ""
}

private fun pickMediumAdmin(addr: org.json.JSONObject): String {
    listOf("city_district", "borough", "county", "district", "suburb").forEach { key ->
        addr.optString(key).takeIf { it.isNotBlank() && isMediumAdmin(it) }?.let { return it }
    }
    return ""
}

private fun pickSmallAdmin(addr: org.json.JSONObject): String {
    listOf("suburb", "neighbourhood", "quarter", "village", "hamlet").forEach { key ->
        addr.optString(key).takeIf { it.isNotBlank() && isSmallAdmin(it) }?.let { return it }
    }
    return ""
}

private fun isLargeAdmin(raw: String): Boolean {
    if (isRoadLike(raw) || isMediumAdmin(raw) || isSmallAdmin(raw) || looksLikePoiName(raw)) return false
    if (raw in KOREAN_METRO_SHORT) return true
    if (raw.endsWith("특별시") || raw.endsWith("광역시") || raw.endsWith("특별자치시") || raw.endsWith("특별자치도")) return true
    if (raw.endsWith("시") && !raw.endsWith("특별시") && !raw.endsWith("광역시")) return true
    if (raw.endsWith("도") && !raw.endsWith("특별자치도")) return false
    return false
}

/** 상호·시설명 — 행정구역 접미사 없음 */
private fun looksLikePoiName(raw: String): Boolean {
    if (isLargeAdmin(raw) || isMediumAdmin(raw) || isSmallAdmin(raw) || isRoadLike(raw)) return false
    return raw.length in 2..40
}

private fun isMediumAdmin(raw: String): Boolean =
    raw.endsWith("구") || raw.endsWith("군") ||
        raw.endsWith("County") || raw.endsWith("county")

private fun isSmallAdmin(raw: String): Boolean =
    raw.endsWith("동") || raw.endsWith("읍") || raw.endsWith("면") || raw.endsWith("리")

private fun isCountryPart(raw: String): Boolean =
    raw.equals("대한민국", true) || raw.equals("South Korea", true) ||
        raw.equals("Republic of Korea", true) || raw.length <= 3 && raw.all { it.isUpperCase() }

private fun normalizeLargeAdmin(raw: String): String {
    var s = raw.trim()
    s = s.removeSuffix("특별자치시")
    s = s.removeSuffix("특별자치도")
    s = s.removeSuffix("특별시")
    s = s.removeSuffix("광역시")
    if (s.endsWith("시") && s.length > 2) s = s.dropLast(1)
    return s.trim()
}

private fun isRoadLike(raw: String): Boolean =
    raw.endsWith("로") || raw.endsWith("길") || raw.contains("번길") || raw.contains("번지")

/** @deprecated 단일 시급 — [adminPlaceLabel] 사용 */
fun GeoResult.adminCityLabel(): String =
    adminPlaceLabel().split("-").firstOrNull().orEmpty().ifBlank {
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
