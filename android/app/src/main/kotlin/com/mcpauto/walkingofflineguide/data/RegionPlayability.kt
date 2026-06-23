package com.mcpauto.walkingofflineguide.data

import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import java.io.File

/** 허브·세계지도 — 일부만 받아도 열 수 있는지 (모국 제외 여행 지역) */
object RegionPlayability {
    private const val MIN_POI_BYTES = 48L
    private const val MIN_GRAPH_BYTES = 32L
    private const val MIN_TILE_PNG_BYTES = 8_000L

    fun regionDir(root: File, regionId: String): File = File(root, "regions/$regionId")

    /** 완료 또는 POI·타일·경로 중 하나라도 있으면 지도 진입 가능 */
    fun hasPartialLocalContent(regionDir: File): Boolean {
        if (RegionDownloadManager.hasLocalTilePayload(regionDir)) return true
        val tiles = File(regionDir, "tiles")
        if (tiles.isDirectory) {
            val anyTile = runCatching {
                tiles.walkTopDown().any { f ->
                    f.isFile && f.extension.equals("png", ignoreCase = true) && f.length() > MIN_TILE_PNG_BYTES
                }
            }.getOrDefault(false)
            if (anyTile) return true
        }
        val zip = File(regionDir, "tiles.zip")
        if (zip.exists() && zip.length() > MIN_TILE_PNG_BYTES) return true
        val poi = File(regionDir, "poi.json")
        if (poi.exists() && poi.length() > MIN_POI_BYTES) return true
        val graph = File(regionDir, "routing_graph.json")
        if (graph.exists() && graph.length() > MIN_GRAPH_BYTES) return true
        return false
    }

    fun isMapVisible(record: RegionRecord, regionDir: File): Boolean =
        record.downloadComplete || hasPartialLocalContent(regionDir)

    enum class PinState { READY, PARTIAL }

    fun pinState(record: RegionRecord, regionDir: File): PinState? = when {
        record.downloadComplete -> PinState.READY
        hasPartialLocalContent(regionDir) -> PinState.PARTIAL
        else -> null
    }
}
