package com.mcpauto.walkingofflineguide.data

import android.content.Context
import java.io.File

/** 비정상 종료 감지 — 다음 실행 시 지도 자동 진입 대신 메인·복구 */
object CrashRecovery {
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private fun flagFile(context: Context): File =
        File(context.filesDir, "walking_data/last_crash.flag")

    fun installHandler(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            markCrash(appContext)
            previous?.uncaughtException(thread, error)
        }
    }

    fun markCrash(context: Context) {
        runCatching {
            val file = flagFile(context)
            file.parentFile?.mkdirs()
            file.writeText(System.currentTimeMillis().toString())
        }
    }

    fun shouldSafeStart(context: Context): Boolean {
        val file = flagFile(context)
        if (!file.exists()) return false
        val age = System.currentTimeMillis() - runCatching { file.readText().trim().toLong() }.getOrDefault(0L)
        if (age > MAX_AGE_MS) {
            file.delete()
            return false
        }
        return true
    }

    fun clear(context: Context) {
        flagFile(context).delete()
    }
}
