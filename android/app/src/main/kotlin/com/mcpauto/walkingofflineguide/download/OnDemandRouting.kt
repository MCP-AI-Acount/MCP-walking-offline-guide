package com.mcpauto.walkingofflineguide.download

import android.content.Context
import com.mcpauto.walkingofflineguide.data.RoutingGraph
import com.mcpauto.walkingofflineguide.data.SafeStorage
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.MapPolicy
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.network.RoutingGraphBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** POI 경로 표시 전 도보 그래프 on-demand 확보 */
object OnDemandRouting {
    private val builder = RoutingGraphBuilder()

    fun regionKeyFor(regionId: String): String =
        if (regionId == MapPolicy.HOME_LIVE_ONLINE_ID) {
            HomeProgressiveDownloader.REGION_ID
        } else {
            regionId
        }

    suspend fun ensureGraph(
        context: Context,
        regionId: String,
        lat: Double,
        lon: Double,
        radiusKm: Double = MapMath.POI_VIEW_RADIUS_KM,
    ): Boolean = withContext(Dispatchers.IO) {
        val key = regionKeyFor(regionId)
        if (key == HomeProgressiveDownloader.REGION_ID) {
            return@withContext HomeProgressiveDownloader.ensureRoutingGraphAt(context, lat, lon)
        }
        val graphFile = File(context.filesDir, "walking_data/regions/$key/routing_graph.json")
        if (graphFile.exists() && graphFile.length() > 32) return@withContext true
        runCatching {
            graphFile.parentFile?.mkdirs()
            val body = builder.buildAndSerialize(PoiLogic.bboxAround(lat, lon, radiusKm))
            if (body.length < 48) return@runCatching false
            SafeStorage.atomicWriteText(graphFile, body)
            RoutingGraph.invalidate(key)
            true
        }.getOrDefault(false)
    }

    suspend fun forceRebuildHomeGraph(context: Context, lat: Double, lon: Double): Boolean =
        withContext(Dispatchers.IO) {
            HomeProgressiveDownloader.forceRebuildRoutingGraphAt(context, lat, lon)
        }

    suspend fun reloadGraph(context: Context, regionId: String): RoutingGraph {
        val key = regionKeyFor(regionId)
        return RoutingGraph.reload(context, key)
    }
}
