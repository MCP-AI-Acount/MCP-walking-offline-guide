package com.mcpauto.walkingofflineguide.network

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale

/** Nominatim 실패·오프라인 시 Android 역지오코더 — 서울-송파-방이 형식 */
object LocalAdminGeocoder {
    fun reverseLabel(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.KOREAN).getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.let { formatAddress(it) }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun formatAddress(a: Address): String {
        val parts = buildList {
            a.adminArea?.trim()?.takeIf { it.isNotBlank() }?.let { add(stripAdminSuffix(normalizeMetro(it))) }
            a.subAdminArea?.trim()?.takeIf { it.isNotBlank() }?.let { add(stripAdminSuffix(it)) }
            (a.subLocality ?: a.locality)?.trim()?.takeIf { it.isNotBlank() }?.let { add(stripAdminSuffix(it)) }
        }.distinct().filter { it.isNotBlank() }
        return parts.joinToString("-")
    }

    private fun normalizeMetro(raw: String): String {
        var s = raw.trim()
        s = s.removeSuffix("특별자치시")
        s = s.removeSuffix("특별자치도")
        s = s.removeSuffix("특별시")
        s = s.removeSuffix("광역시")
        if (s.endsWith("시") && s.length > 2) s = s.dropLast(1)
        return s.trim().ifBlank { raw.trim() }
    }
}
