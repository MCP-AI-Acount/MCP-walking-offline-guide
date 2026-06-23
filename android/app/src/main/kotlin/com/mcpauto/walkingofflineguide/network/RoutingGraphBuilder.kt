package com.mcpauto.walkingofflineguide.network

import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.round

@Serializable
data class RoutingGraphFile(
    val nodes: List<List<Double>> = emptyList(),
    val edges: List<List<Int>> = emptyList(),
    val version: Int = 1,
)

/** OSM 도보·보행 가능 도로 → 오프라인 최단경로 그래프 */
class RoutingGraphBuilder {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val json = Json { prettyPrint = false }

    suspend fun buildAndSerialize(bbox: Bbox): String = withContext(Dispatchers.IO) {
        val ways = fetchWays(bbox)
        json.encodeToString(buildGraph(ways))
    }

    private fun fetchWays(bbox: Bbox): List<JSONObject> {
        val highway = (
            "footway|path|pedestrian|steps|track|living_street|" +
                "service|residential|unclassified|tertiary|secondary|primary"
            )
        val q = """
            [out:json][timeout:120];
            way["highway"~"$highway"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            out geom;
        """.trimIndent()
        return runCatching {
            val arr = OverpassHttp.postQuery(q, "WalkingOfflineGuide/1.2 (foot routing)")
                .optJSONArray("elements") ?: return@runCatching emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val el = arr.getJSONObject(i)
                if (el.optString("type") == "way") el else null
            }
        }.getOrDefault(emptyList())
    }

    private fun buildGraph(ways: List<JSONObject>): RoutingGraphFile {
        val nodeLl = linkedMapOf<Long, Pair<Double, Double>>()
        val edgeSet = mutableSetOf<Pair<Long, Long>>()

        for (way in ways) {
            val geom = way.optJSONArray("geometry") ?: continue
            var prevId: Long? = null
            for (i in 0 until geom.length()) {
                val pt = geom.getJSONObject(i)
                val lat = pt.getDouble("lat")
                val lon = pt.getDouble("lon")
                val nid = (lat * 1e5).toLong() * 10_000_000L + (lon * 1e5).toLong()
                nodeLl[nid] = lat to lon
                if (prevId != null && prevId != nid) {
                    val a = minOf(prevId, nid)
                    val b = maxOf(prevId, nid)
                    edgeSet.add(a to b)
                }
                prevId = nid
                if (nodeLl.size >= MAX_NODES) break
            }
            if (nodeLl.size >= MAX_NODES) break
        }

        val idMap = linkedMapOf<Long, Int>()
        val nodes = mutableListOf<List<Double>>()
        nodeLl.entries.take(MAX_NODES).forEach { (osmId, ll) ->
            idMap[osmId] = nodes.size
            nodes.add(listOf(round(ll.first * 1e6) / 1e6, round(ll.second * 1e6) / 1e6))
        }

        val edges = mutableListOf<List<Int>>()
        for ((a, b) in edgeSet) {
            val ia = idMap[a] ?: continue
            val ib = idMap[b] ?: continue
            val w = haversineM(nodes[ia][0], nodes[ia][1], nodes[ib][0], nodes[ib][1]).toInt().coerceAtLeast(1)
            edges.add(listOf(ia, ib, w))
            if (edges.size >= MAX_EDGES) break
        }

        return RoutingGraphFile(nodes = nodes, edges = edges, version = 1)
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        PoiLogic.haversineM(lat1, lon1, lat2, lon2)

    companion object {
        private const val MAX_NODES = 30_000
        private const val MAX_EDGES = 80_000
    }
}
