package com.mcpauto.walkingofflineguide.data

import android.content.Context
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.PriorityQueue
import kotlin.math.cos

@Serializable
private data class GraphFile(
    val nodes: List<List<Double>> = emptyList(),
    val edges: List<List<Int>> = emptyList(),
    val version: Int = 1,
)

class RoutingGraph private constructor(
    private val nodes: List<Pair<Double, Double>>,
    private val adjacency: List<List<Pair<Int, Int>>>,
) {
    val hasData: Boolean get() = nodes.isNotEmpty()

    fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): List<Pair<Double, Double>> {
        if (nodes.isEmpty()) return listOf(fromLat to fromLon, toLat to toLon)
        val start = nearestNode(fromLat, fromLon, maxM = 1_500.0) ?: return listOf(fromLat to fromLon, toLat to toLon)
        val goal = nearestNode(toLat, toLon, maxM = 1_500.0) ?: return listOf(fromLat to fromLon, toLat to toLon)
        if (start == goal) return listOf(fromLat to fromLon, nodes[start].first to nodes[start].second, toLat to toLon)

        val dist = IntArray(nodes.size) { Int.MAX_VALUE }
        val prev = IntArray(nodes.size) { -1 }
        dist[start] = 0
        val pq = PriorityQueue(compareBy<Pair<Int, Int>> { it.first })
        pq.add(0 to start)

        while (pq.isNotEmpty()) {
            val (d, u) = pq.poll()
            if (d > dist[u]) continue
            if (u == goal) break
            for ((v, w) in adjacency[u]) {
                val nd = d + w
                if (nd < dist[v]) {
                    dist[v] = nd
                    prev[v] = u
                    pq.add(nd to v)
                }
            }
        }

        if (dist[goal] == Int.MAX_VALUE) return listOf(fromLat to fromLon, toLat to toLon)

        val pathIdx = mutableListOf<Int>()
        var cur = goal
        while (cur >= 0) {
            pathIdx.add(cur)
            if (cur == start) break
            cur = prev[cur]
        }
        if (pathIdx.last() != start) return listOf(fromLat to fromLon, toLat to toLon)
        pathIdx.reverse()

        val out = mutableListOf(fromLat to fromLon)
        pathIdx.forEach { i -> out.add(nodes[i].first to nodes[i].second) }
        out.add(toLat to toLon)
        return out
    }

    fun routeLengthM(points: List<Pair<Double, Double>>): Int {
        if (points.size < 2) return 0
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += PoiLogic.haversineM(points[i - 1].first, points[i - 1].second, points[i].first, points[i].second)
        }
        return sum.toInt()
    }

    private fun nearestNode(lat: Double, lon: Double, maxM: Double): Int? {
        var best: Int? = null
        var bestD = maxM
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        nodes.forEachIndexed { i, (nlat, nlon) ->
            val dLat = (nlat - lat) * 111_000.0
            val dLon = (nlon - lon) * 111_000.0 * cosLat
            val d = kotlin.math.hypot(dLat, dLon)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val cache = mutableMapOf<String, RoutingGraph>()
        private val mutex = Mutex()
        private val Empty = RoutingGraph(emptyList(), emptyList())

        suspend fun load(context: Context, regionId: String): RoutingGraph = mutex.withLock {
            cache[regionId]?.let { return it }
            val graph = withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "walking_data/regions/$regionId/routing_graph.json")
                if (!file.exists()) return@withContext Empty
                runCatching {
                    fromFile(json.decodeFromString<GraphFile>(file.readText()))
                }.getOrElse {
                    SafeStorage.quarantineCorrupt(file)
                    Empty
                }
            }
            if (graph.hasData) cache[regionId] = graph
            graph
        }

        suspend fun reload(context: Context, regionId: String): RoutingGraph {
            invalidate(regionId)
            return load(context, regionId)
        }

        fun invalidate(regionId: String) {
            cache.remove(regionId)
        }

        private fun fromFile(file: GraphFile): RoutingGraph {
            val nodes = file.nodes.mapNotNull { ll ->
                if (ll.size < 2) null else ll[0] to ll[1]
            }
            val adjacency = MutableList(nodes.size) { mutableListOf<Pair<Int, Int>>() }
            file.edges.forEach { e ->
                if (e.size < 3) return@forEach
                val a = e[0]
                val b = e[1]
                val w = e[2]
                if (a in adjacency.indices && b in adjacency.indices) {
                    adjacency[a].add(b to w)
                    adjacency[b].add(a to w)
                }
            }
            return RoutingGraph(nodes, adjacency.map { it.toList() })
        }
    }
}
