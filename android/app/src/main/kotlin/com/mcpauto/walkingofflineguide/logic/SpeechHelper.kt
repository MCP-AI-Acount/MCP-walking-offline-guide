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
                tts?.language = defaultLocale
                tts?.setSpeechRate(1.1f)
            } else {
                lastError = true
            }
        }
    }

    fun isAvailable(): Boolean = ready && !lastError

    fun speak(text: String, locale: Locale = defaultLocale, onError: () -> Unit = {}) {
        if (!ready) {
            lastError = true
            onError()
            return
        }
        defaultLocale = locale
        tts?.language = locale
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
}
