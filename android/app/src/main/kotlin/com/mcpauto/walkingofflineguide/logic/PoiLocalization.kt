package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.util.HomeLanguage

data class LocalizedPoi(
    val poi: Poi,
    /** 목적지 고유명사(현지어·원문) */
    val localName: String,
    /** 모국어 읽기 — localName과 다를 때만 표시 */
    val readingName: String?,
    val description: String?,
    val typeLabel: String,
    /** TTS — 읽기 우선, 없으면 localName */
    val ttsText: String,
)

object PoiLocalization {

    fun forDisplay(poi: Poi, homeLang: String, destLang: String): LocalizedPoi {
        val local = localProperName(poi)
        val desc = poi.descriptionKo?.trim()?.takeIf { it.isNotBlank() }
        val typeLabel = HomeLanguage.poiTypeLabel(poi.kind, poi.tourism, homeLang)
        val reading = homeReadingName(poi, homeLang, destLang)
        val secondary = reading.takeIf { it.isNotBlank() && !namesEquivalent(it, local) }
        val tts = secondary ?: local
        return LocalizedPoi(poi, local, secondary, desc, typeLabel, tts)
    }

    fun fromStored(poi: Poi, homeLang: String, destLang: String): LocalizedPoi =
        forDisplay(poi, homeLang, destLang)

    /** WiFi 다운로드 시 name_home에 모국어 읽기 저장 */
    fun enrichForKoreanHome(pois: List<Poi>, destLang: String, homeLang: String): List<Poi> {
        if (homeLang != "ko") return pois
        return pois.map { enrichOne(it, destLang, homeLang) }
    }

    fun enrichOne(poi: Poi, destLang: String, homeLang: String): Poi {
        if (homeLang != "ko") return poi
        val local = localProperName(poi)
        if (local.isBlank()) return poi
        val stored = poi.nameHome?.trim().orEmpty()
        if (stored.isNotBlank() && ForeignNameReading.containsHangul(stored) && !namesEquivalent(stored, local)) {
            return poi
        }
        if (ForeignNameReading.containsHangul(local)) return poi
        val reading = ForeignNameReading.toKoreanReading(local, destLang)
        return if (namesEquivalent(reading, local)) poi else poi.copy(nameHome = reading)
    }

    private fun localProperName(poi: Poi): String =
        poi.nameKo.trim().ifBlank { poi.nameHome?.trim().orEmpty() }

    private fun namesEquivalent(a: String, b: String): Boolean =
        a.trim().equals(b.trim(), ignoreCase = true)

    private fun homeReadingName(poi: Poi, homeLang: String, destLang: String): String {
        val local = localProperName(poi)
        if (local.isBlank()) return local
        if (homeLang != "ko") {
            return poi.nameHome?.trim()?.takeIf { it.isNotBlank() && !namesEquivalent(it, local) } ?: local
        }
        if (ForeignNameReading.containsHangul(local)) return local
        poi.nameHome?.trim()?.takeIf { it.isNotBlank() && ForeignNameReading.containsHangul(it) }
            ?.let { return it }
        return ForeignNameReading.toKoreanReading(local, destLang)
    }
}
