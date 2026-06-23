package com.mcpauto.walkingofflineguide.network

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Overpass API — 미러 순차 시도 (de 장애·443 연결 실패 대비) */
object OverpassHttp {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun postQuery(query: String, userAgent: String = "WalkingOfflineGuide/1.2 (Android)"): JSONObject {
        val body = FormBody.Builder().add("data", query).build()
        var last: Exception? = null
        for (base in endpoints) {
            try {
                val req = Request.Builder()
                    .url(base)
                    .post(body)
                    .header("User-Agent", userAgent)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && text.isNotBlank()) {
                        return JSONObject(text)
                    }
                    last = IOException("HTTP ${resp.code} @ $base")
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException(
            "Overpass 서버 연결 실패. WiFi·VPN 확인 후 다시 시도해 주세요. (${last?.message ?: "unknown"})",
            last,
        )
    }
}
