package com.mcpauto.walkingofflineguide.download

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Doze·화면 꺼짐 중에도 다운로드 네트워크·CPU 유지.
 * 포그라운드 서비스와 함께 사용 — 완료/중단 시 반드시 [release].
 */
class DownloadNetworkGuard(context: Context) {
    private val appContext = context.applicationContext
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @Synchronized
    fun acquire() {
        if (wakeLock?.isHeld != true) {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WalkingOfflineGuide:RegionDownload",
            ).apply {
                setReferenceCounted(false)
                acquire(MAX_MS)
            }
        }
        if (wifiLock?.isHeld != true) {
            @Suppress("DEPRECATION")
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "WalkingOfflineGuide:DownloadWifi",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    @Synchronized
    fun release() {
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        wifiLock = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    companion object {
        /** PARTIAL_WAKE_LOCK 상한 (10h) — 장시간 다운로드 대비 */
        private const val MAX_MS = 10L * 60L * 60L * 1000L
    }
}
