package com.mcpauto.walkingofflineguide.data

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

@Serializable
private data class WorldPlacesFile(
    val countries: List<CountryEntry> = emptyList(),
    val cities: List<CityEntry> = emptyList(),
)

@Serializable
data class CountryEntry(
    val code: String = "",
    @SerialName("name_ko") val nameKo: String = "",
    @SerialName("name_en") val nameEn: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val capital: String = "",
)

@Serializable
data class CityEntry(
    val name: String = "",
    @SerialName("country_code") val countryCode: String = "",
    val country: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

class GeoCatalog(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val countries: List<CountryEntry>
    private val cities: List<CityEntry>

    init {
        val text = context.assets.open("geo/world_places.json").bufferedReader().readText()
        val file = json.decodeFromString<WorldPlacesFile>(text)
        countries = file.countries
        cities = file.cities
    }

    fun searchCountries(query: String, limit: Int = 8): List<CountryEntry> {
        val q = norm(query)
        if (q.isBlank()) return emptyList()
        return countries.filter { c ->
            norm(c.nameKo).contains(q) || norm(c.nameEn).contains(q) || norm(c.code).contains(q)
        }.take(limit)
    }

    fun searchCities(query: String, countryHint: String = "", limit: Int = 10): List<CityEntry> {
        val q = norm(query)
        if (q.length < 1) return emptyList()
        val hint = norm(countryHint)
        return cities.filter { c ->
            val nameMatch = norm(c.name).contains(q) || norm(c.name).startsWith(q)
            val countryMatch = hint.isBlank() ||
                norm(c.country).contains(hint) ||
                norm(c.countryCode).contains(hint) ||
                countries.any { co ->
                    (norm(co.nameKo).contains(hint) || norm(co.nameEn).contains(hint)) &&
                        co.code == c.countryCode
                }
            nameMatch && countryMatch
        }.sortedBy { if (norm(it.name).startsWith(q)) 0 else 1 }
            .take(limit)
    }

    fun resolveCountry(name: String): CountryEntry? {
        val q = norm(name)
        if (q.isBlank()) return null
        countries.firstOrNull { it.code.equals(name.trim(), ignoreCase = true) }?.let { return it }
        return countries.firstOrNull {
            norm(it.nameKo) == q || norm(it.nameEn) == q
        } ?: countries.firstOrNull {
            norm(it.nameKo).contains(q) || norm(it.nameEn).contains(q)
        }
    }

    fun resolveCountryByCode(code: String): CountryEntry? =
        countries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

    fun resolveCity(name: String, countryHint: String = ""): CityEntry? {
        val q = norm(name)
        val hint = norm(countryHint)
        return cities.firstOrNull { c ->
            norm(c.name) == q && (hint.isBlank() || norm(c.country).contains(hint))
        } ?: searchCities(name, countryHint, 1).firstOrNull()
    }

    fun allCountries(): List<CountryEntry> = countries

    fun allCities(): List<CityEntry> = cities

    private fun norm(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}
