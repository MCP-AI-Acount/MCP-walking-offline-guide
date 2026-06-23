package com.mcpauto.walkingofflineguide.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mcpauto.walkingofflineguide.MainActivity
import com.mcpauto.walkingofflineguide.R
import com.mcpauto.walkingofflineguide.data.DownloadJobState
import com.mcpauto.walkingofflineguide.data.TripStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 백그라운드·화면 꺼짐에도 지도 다운로드 계속 (알림 표시) */
class RegionDownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var downloadJob: Job? = null
    private lateinit var store: TripStore
    private lateinit var manager: RegionDownloadManager
    private var networkGuard: DownloadNetworkGuard? = null
    private val json = Json { ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        store = TripStore(applicationContext)
        manager = RegionDownloadManager(applicationContext, store)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                manager.cancelled = true
                downloadJob?.cancel()
                DownloadSession.publishEvent(DownloadEvent.Cancelled)
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val jobJson = intent.getStringExtra(EXTRA_JOB).orEmpty()
                if (jobJson.isBlank()) {
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
                if (DownloadSession.running.value) return START_STICKY
                val job = runCatching { json.decodeFromString<DownloadJobState>(jobJson) }.getOrNull()
                if (job == null || job.stops.isEmpty()) {
                    DownloadSession.publishEvent(DownloadEvent.Failed("다운로드 일정이 없습니다."))
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
                startDownload(job)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startDownload(job: DownloadJobState) {
        downloadJob?.cancel()
        manager.cancelled = false
        DownloadSession.activeManager = manager
        DownloadSession.setRunning(true)
        DownloadSession.publishProgress(
            DownloadProgress("", "연결 중", 0, 0, "연결 중", 1),
        )
        startForeground(NOTIFICATION_ID, buildNotification("지도 다운로드 준비 중…", 0, indeterminate = true))
        networkGuard = DownloadNetworkGuard(this).also { it.acquire() }

        downloadJob = serviceScope.launch(Dispatchers.IO) {
            store.saveDownloadJob(job.copy(active = true))
            try {
                val downloaded = manager.downloadLeg(
                    job.countryLabel,
                    job.stops,
                    job.homeCountryCode,
                    job,
                ) { progress ->
                    DownloadSession.publishProgress(progress)
                    launch(Dispatchers.Main) {
                        updateNotification(progress)
                    }
                }
                val updated = store.loadConfig().copy(
                    destinationCountry = job.destinationCountry.ifBlank { job.countryLabel },
                    tripStartEpochDay = job.tripStartEpochDay,
                    tripEndEpochDay = job.tripEndEpochDay,
                    legs = job.legs,
                    skipHubMenu = job.skipHubMenu,
                    setupComplete = true,
                )
                store.saveConfig(updated)
                store.clearDownloadJob()
                val allRegions = store.loadRegions()
                val entry = downloaded.firstOrNull { !job.finishedCityNames.contains(it.cityName) }
                    ?: downloaded.lastOrNull()
                    ?: allRegions.firstOrNull { it.downloadComplete }
                DownloadSession.publishEvent(DownloadEvent.Completed(updated, allRegions, entry))
            } catch (e: CancellationException) {
                DownloadSession.publishEvent(DownloadEvent.Cancelled)
            } catch (e: Exception) {
                DownloadSession.publishEvent(
                    DownloadEvent.Failed(e.message ?: "다운로드 실패"),
                )
            } finally {
                stopForegroundAndSelf()
            }
        }
    }

    private fun updateNotification(progress: DownloadProgress) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(progress.label, progress.percent, indeterminate = false))
    }

    private fun buildNotification(text: String, percent: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("도보 여행 — 지도 다운로드")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "지도 다운로드",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "백그라운드 지도·명소 다운로드 진행"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopForegroundAndSelf() {
        networkGuard?.release()
        networkGuard = null
        DownloadSession.setRunning(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        networkGuard?.release()
        networkGuard = null
        downloadJob?.cancel()
        serviceScope.cancel()
        DownloadSession.setRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "walking_region_download"
        private const val NOTIFICATION_ID = 7101
        private const val EXTRA_JOB = "download_job_json"
        private const val ACTION_START = "com.mcpauto.walkingofflineguide.download.START"
        private const val ACTION_CANCEL = "com.mcpauto.walkingofflineguide.download.CANCEL"

        fun start(context: Context, job: DownloadJobState) {
            val json = Json { ignoreUnknownKeys = true }
            val intent = Intent(context, RegionDownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_JOB, json.encodeToString(job))
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, RegionDownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
