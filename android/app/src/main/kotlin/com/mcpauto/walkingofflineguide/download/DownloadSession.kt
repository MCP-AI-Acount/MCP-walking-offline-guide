package com.mcpauto.walkingofflineguide.download

import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.TripConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI ↔ 포그라운드 다운로드 서비스 공유 상태 */
object DownloadSession {
    @Volatile
    var activeManager: RegionDownloadManager? = null

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    fun setRunning(running: Boolean) {
        _running.value = running
        if (!running) activeManager = null
    }

    fun publishProgress(p: DownloadProgress) {
        _progress.value = p
    }

    fun publishEvent(event: DownloadEvent) {
        _events.tryEmit(event)
        if (event is DownloadEvent.Completed || event is DownloadEvent.Failed || event is DownloadEvent.Cancelled) {
            setRunning(false)
            _progress.value = null
        }
    }

    fun resetUi() {
        _progress.value = null
        _running.value = false
    }
}

sealed class DownloadEvent {
    data class Completed(
        val config: TripConfig,
        val regions: List<RegionRecord>,
        val entry: RegionRecord?,
    ) : DownloadEvent()

    data class Failed(val message: String) : DownloadEvent()
    data object Cancelled : DownloadEvent()
}
