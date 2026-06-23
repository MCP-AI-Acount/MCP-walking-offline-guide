package com.mcpauto.walkingofflineguide.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.mcpauto.walkingofflineguide.data.SafeStorage
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** MyMemory 무료 API + 디스크 캐시 — WiFi 다운로드·TTS 재생 시 사용 */
class TranslationClient(context: Context) {
    private val cacheFile = File(context.filesDir, "walking_data/translation_cache.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val memory = mutableMapOf<String, String>()
    private val mutex = Mutex()
    private var persistCounter = 0
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    init {
        runCatching {
            if (cacheFile.exists()) {
                val file = json.decodeFromString<CacheFile>(cacheFile.readText())
                memory.putAll(file.entries)
            }
        }.onFailure {
            SafeStorage.quarantineCorrupt(cacheFile)
        }
    }

    suspend fun translate(text: String, fromLang: String, toLang: String): String = withContext(Dispatchers.IO) {
        translatePair(text, fromLang, toLang)
    }

    /** 원문 언어 자동 감지 → 모국어 (MyMemory auto|to) */
    suspend fun translateAutoToHome(text: String, homeLang: String): String = withContext(Dispatchers.IO) {
        translatePair(text, "auto", homeLang)
    }

    private suspend fun translatePair(text: String, fromLang: String, toLang: String): String =
        withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return@withContext trimmed
        val from = if (fromLang.equals("auto", ignoreCase = true)) "auto" else normalizeLang(fromLang)
        val to = normalizeLang(toLang)
        if (from != "auto" && from == to) return@withContext trimmed

        val key = "$from|$to|${trimmed.hashCode()}|$trimmed"
        memory[key]?.let { return@withContext it }

        mutex.withLock {
            memory[key]?.let { return@withLock it }
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$from|$to"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "WalkingOfflineGuide/1.0 (translate)")
                .build()
            val translated = runCatching {
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use trimmed
                    val body = JSONObject(resp.body?.string().orEmpty())
                    body.optJSONObject("responseData")
                        ?.optString("translatedText")
                        ?.takeIf { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
                        ?: trimmed
                }
            }.getOrDefault(trimmed)
            memory[key] = translated
            persistCounter++
            if (persistCounter % 8 == 0) persistCache()
            Thread.sleep(250)
            translated
        }
    }

    private fun persistCache() {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val trimmedMap = if (memory.size > 8000) {
                memory.entries.toList().takeLast(6000).associate { it.key to it.value }
            } else {
                memory.toMap()
            }
            memory.clear()
            memory.putAll(trimmedMap)
            SafeStorage.atomicWriteText(cacheFile, json.encodeToString(CacheFile(trimmedMap)))
        }
    }

    private fun normalizeLang(tag: String): String =
        when (tag.lowercase()) {
            "zh-tw", "zh-hant" -> "zh-TW"
            "zh", "zh-cn", "zh-hans" -> "zh-CN"
            else -> tag.lowercase().substringBefore('-')
        }

    @Serializable
    private data class CacheFile(val entries: Map<String, String> = emptyMap())
}
