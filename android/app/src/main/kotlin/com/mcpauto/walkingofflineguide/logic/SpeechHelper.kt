package com.mcpauto.walkingofflineguide.logic

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpeechHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var lastError = false
    private var defaultLocale: Locale = Locale.KOREAN

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyVoiceSettings(defaultLocale)
            } else {
                lastError = true
            }
        }
    }

    private fun applyVoiceSettings(locale: Locale) {
        tts?.language = locale
        tts?.setSpeechRate(SPEECH_RATE)
        tts?.setPitch(1.0f)
    }

    fun isAvailable(): Boolean = ready && !lastError

    fun speak(text: String, locale: Locale = defaultLocale, onError: () -> Unit = {}) {
        if (!ready) {
            lastError = true
            onError()
            return
        }
        defaultLocale = locale
        applyVoiceSettings(locale)
        tts?.stop()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "poi-tts")
        if (result == TextToSpeech.ERROR) {
            lastError = true
            onError()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        /** 유저 고정 — locale 전환 시 엔진이 rate를 리셋하는 경우 대비 speak마다 재적용 */
        const val SPEECH_RATE = 1.15f
    }
}
