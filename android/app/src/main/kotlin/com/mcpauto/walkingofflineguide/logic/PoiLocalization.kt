package com.mcpauto.walkingofflineguide.logic

import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.util.HomeLanguage

data class LocalizedPoi(
    val poi: Poi,
    /** 화면 표시 — 모국어(한글) 철자·발음 읽기 */
    val name: String,
    val description: String?,
    /** UI 라벨(식당·명소 등) — 모국어 */
    val typeLabel: String,
    /** TTS — [name]과 동일 (한국어 읽기) */
    val ttsText: String,
)

object PoiLocalization {

    fun forDisplay(poi: Poi, homeLang: String, destLang: String): LocalizedPoi {
        val local = poi.nameKo.trim()
        val desc = poi.descriptionKo?.trim()?.takeIf { it.isNotBlank() }
        val typeLabel = HomeLanguage.poiTypeLabel(poi.kind, poi.tourism, homeLang)
        val name = koreanReadingName(poi, homeLang, destLang)
        return LocalizedPoi(poi, name, desc, typeLabel, name)
    }

    fun fromStored(poi: Poi, homeLang: String, destLang: String): LocalizedPoi =
        forDisplay(poi, homeLang, destLang)

    /** WiFi 다운로드 시 name_home에 한글 읽기 저장 */
    fun enrichForKoreanHome(pois: List<Poi>, destLang: String, homeLang: String): List<Poi> {
        if (homeLang != "ko") return pois
        return pois.map { enrichOne(it, destLang, homeLang) }
    }

    fun enrichOne(poi: Poi, destLang: String, homeLang: String): Poi {
        if (homeLang != "ko") return poi
        val local = poi.nameKo.trim()
        if (local.isBlank() || ForeignNameReading.containsHangul(local)) return poi
        val stored = poi.nameHome?.trim().orEmpty()
        if (stored.isNotBlank() && ForeignNameReading.containsHangul(stored)) return poi
        val reading = ForeignNameReading.toKoreanReading(local, destLang)
        return if (reading == local) poi else poi.copy(nameHome = reading)
    }

    private fun koreanReadingName(poi: Poi, homeLang: String, destLang: String): String {
        val local = poi.nameKo.trim().ifBlank { poi.nameHome?.trim().orEmpty() }
        if (local.isBlank()) return local
        if (homeLang != "ko") {
            return poi.nameHome?.trim()?.takeIf { it.isNotBlank() } ?: local
        }
        if (ForeignNameReading.containsHangul(local)) return local
        poi.nameHome?.trim()?.takeIf { it.isNotBlank() && ForeignNameReading.containsHangul(it) }
            ?.let { return it }
        return ForeignNameReading.toKoreanReading(local, destLang)
    }
}
