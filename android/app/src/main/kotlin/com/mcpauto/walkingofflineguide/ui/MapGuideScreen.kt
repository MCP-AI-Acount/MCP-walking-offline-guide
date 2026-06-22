package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.PoiRepository
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.RoutingGraph
import com.mcpauto.walkingofflineguide.data.TileStore
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.data.UserPosition
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.logic.SpeechHelper
import com.mcpauto.walkingofflineguide.map.PoiColors
import kotlinx.coroutines.launch

@Composable
fun MapGuideScreen(
    region: RegionRecord,
    config: TripConfig,
    simulateGps: Boolean,
    onOpenOptions: () -> Unit,
    onMainHub: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PoiRepository(context) }
    val tileStore = remember { TileStore(context) }
    val speech = remember { SpeechHelper(context) }
    val listState = rememberLazyListState()

    var bundle by remember { mutableStateOf(PoiBundle()) }
    var tilesReady by remember { mutableStateOf(false) }
    var availableZooms by remember { mutableStateOf(setOf(10, 11, 12, 13)) }
    var pos by remember {
        mutableStateOf(
            UserPosition(region.lat, region.lon, simulated = simulateGps),
        )
    }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var showSightseeing by remember { mutableStateOf(config.showSight) }
    var showRestaurant by remember { mutableStateOf(config.showRestaurant) }
    var showHotel by remember { mutableStateOf(config.showHotel) }
    var selectedPoiId by remember { mutableStateOf<String?>(null) }
    var routingGraph by remember { mutableStateOf<RoutingGraph?>(null) }
    var routePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var routeDistanceM by remember { mutableStateOf<Int?>(null) }
    var ttsOk by remember { mutableStateOf(true) }

    LaunchedEffect(region.id) {
        bundle = repo.loadRegionBundle(region.id)
        val count = tileStore.loadRegion(region.id)
        availableZooms = tileStore.availableZooms().ifEmpty { setOf(10, 11, 12, 13) }
        tilesReady = count > 0
        routingGraph = RoutingGraph.load(context, region.id)
        val bb = bundle.bbox.takeIf { it.north > it.south } ?: region.bbox
        camera = MapCameraMath.defaultCamera(pos, bb, availableZooms)
    }

    val cam = camera ?: return

    fun matchesCategory(p: Poi): Boolean = when (p.kind) {
        "restaurant" -> showRestaurant
        "hotel" -> showHotel
        else -> showSightseeing
    }

    val radiusPois = remember(bundle, pos) {
        PoiLogic.withinRadiusKm(bundle.pois, pos.lat, pos.lon, MapMath.FOREIGN_RADIUS_KM)
    }

    val viewportAll = remember(cam, radiusPois, mapSize, pos) {
        if (mapSize.width <= 0) return@remember emptyList()
        PoiLogic.visibleInViewport(radiusPois, cam, mapSize.width.toFloat(), mapSize.height.toFloat(), pos.lat, pos.lon)
    }

    val listPois = remember(viewportAll, showSightseeing, showRestaurant, showHotel) {
        viewportAll.filter { matchesCategory(it) }
    }

    LaunchedEffect(selectedPoiId, pos, routingGraph, radiusPois) {
        val target = selectedPoiId?.let { id -> radiusPois.find { it.id == id } }
        if (target == null) {
            routePoints = emptyList()
            routeDistanceM = null
            return@LaunchedEffect
        }
        val graph = routingGraph
        if (graph == null) {
            routePoints = listOf(pos.lat to pos.lon, target.lat to target.lon)
            routeDistanceM = PoiLogic.haversineM(pos.lat, pos.lon, target.lat, target.lon).toInt()
            return@LaunchedEffect
        }
        val pts = graph.route(pos.lat, pos.lon, target.lat, target.lon)
        routePoints = pts
        routeDistanceM = if (pts.size >= 2) graph.routeLengthM(pts) else null
    }

    LaunchedEffect(selectedPoiId, listPois) {
        val id = selectedPoiId ?: return@LaunchedEffect
        val idx = listPois.indexOfFirst { it.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(containerColor = Color(0xFFF4F6F8)) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenOptions) {
                    Icon(Icons.Default.Settings, contentDescription = "옵션", tint = Color(0xFF334155))
                }
                Text(
                    region.cityName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onMainHub) {
                    Text("메인", color = Color(0xFF2563EB), style = MaterialTheme.typography.labelMedium)
                }
            }

            Box(
                Modifier
                    .weight(0.55f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .onSizeChanged { mapSize = it },
            ) {
                OfflineTileMap(
                    camera = cam,
                    onCameraChange = { camera = it },
                    onResetCamera = {
                        camera = MapCameraMath.defaultCamera(pos, bundle.bbox, availableZooms)
                    },
                    tileStore = tileStore,
                    tilesReady = tilesReady,
                    user = pos,
                    pois = viewportAll,
                    highlightedPoiId = selectedPoiId,
                    routePoints = routePoints,
                    routeDistanceM = routeDistanceM,
                    onPoiClick = { poi ->
                        selectedPoiId = if (selectedPoiId == poi.id) null else poi.id
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!tilesReady) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                IconButton(
                    onClick = {
                        camera = MapCameraMath.defaultCamera(pos, bundle.bbox, availableZooms)
                    },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "초기화", tint = Color(0xFF3D8B5E))
                }
            }

            Column(Modifier.weight(0.45f).fillMaxWidth().padding(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = showRestaurant,
                        onClick = { showRestaurant = !showRestaurant },
                        label = { Text("식당", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PoiColors.restaurant.copy(alpha = 0.25f),
                        ),
                    )
                    FilterChip(
                        selected = showHotel,
                        onClick = { showHotel = !showHotel },
                        label = { Text("숙소", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PoiColors.hotel.copy(alpha = 0.25f),
                        ),
                    )
                    FilterChip(
                        selected = showSightseeing,
                        onClick = { showSightseeing = !showSightseeing },
                        label = { Text("명소", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PoiColors.sight.copy(alpha = 0.25f),
                        ),
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (listPois.isEmpty()) {
                        item { Text("주변 장소 없음", color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                    } else {
                        items(listPois, key = { it.id }) { p ->
                            PoiListItem(
                                poi = p,
                                accent = PoiColors.accent(p),
                                typeLabel = PoiLogic.tourismKo[p.tourism ?: p.kind] ?: p.kind,
                                selected = p.id == selectedPoiId,
                                routeDistanceM = if (p.id == selectedPoiId) routeDistanceM else null,
                                onClick = {
                                    selectedPoiId = if (selectedPoiId == p.id) null else p.id
                                },
                                onSpeak = { speech.speak(PoiLogic.ttsText(p), { ttsOk = false }) },
                            )
                        }
                    }
                }
            }
        }
    }
}
