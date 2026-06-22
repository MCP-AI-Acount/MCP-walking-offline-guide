package com.mcpauto.walkingofflineguide.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mcpauto.walkingofflineguide.data.CityStop
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.download.DownloadProgress
import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import com.mcpauto.walkingofflineguide.logic.LocationHelper
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.WifiGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed class AppScreen {
    data object Loading : AppScreen()
    data object Hub : AppScreen()
    data object Setup : AppScreen()
    data class Map(val regionId: String, val simulated: Boolean = true) : AppScreen()
}

@Composable
fun WalkingApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { TripStore(context) }
    val locationHelper = remember { LocationHelper(context) }
    val geocoder = remember { NominatimGeocoder() }

    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Loading) }
    var config by remember { mutableStateOf(TripConfig()) }
    var regions by remember { mutableStateOf<List<RegionRecord>>(emptyList()) }
    var loadingMessage by remember { mutableStateOf("앱 데이터를 불러오는 중…") }
    var loadingDetail by remember { mutableStateOf("") }
    var showNoWifiDialog by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showMoveConfirm by remember { mutableStateOf<RegionRecord?>(null) }
    var highlightId by remember { mutableStateOf<String?>(null) }
    var blinkHighlight by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        loadingMessage = "데이터 소모가 큽니다.\n가능한 WiFi가 있는 곳에서 실행해 주세요."
        delay(1200)
        config = store.loadConfig()
        regions = store.loadRegions()
        loadingDetail = store.todayCityDescription(config, regions)
        delay(1500)
        if (!WifiGate.hasInternet(context)) {
            showNoWifiDialog = true
        } else {
            screen = if (config.skipHubMenu && config.setupComplete) AppScreen.Hub else AppScreen.Hub
        }
    }

    if (showNoWifiDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("WiFi 없음") },
            text = { Text("모바일 데이터로 진행하면 요금이 발생할 수 있습니다.\n그래도 계속하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showNoWifiDialog = false
                    screen = AppScreen.Hub
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) { Text("나가기") }
            },
        )
    }

    showMoveConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { showMoveConfirm = null },
            title = { Text("${target.cityName}으로 이동") },
            text = { Text("해당 도시로 이동하시겠습니까?\n(GPS 없이 지도 중심만 이동합니다)") },
            confirmButton = {
                TextButton(onClick = {
                    showMoveConfirm = null
                    screen = AppScreen.Map(target.id, simulated = true)
                }) { Text("이동") }
            },
            dismissButton = { TextButton(onClick = { showMoveConfirm = null }) { Text("취소") } },
        )
    }

    if (showOptions) {
        OptionsDialog(
            config = config,
            onDismiss = { showOptions = false },
            onConfirm = { updated ->
                scope.launch {
                    config = updated
                    store.saveConfig(updated)
                    showOptions = false
                }
            },
            onDeleteAllMaps = {
                scope.launch {
                    store.deleteAllMapData()
                    regions = emptyList()
                    showOptions = false
                }
            },
            onExit = { (context as? Activity)?.finish() },
            onMain = {
                showOptions = false
                screen = AppScreen.Hub
            },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("앱 종료") },
            text = { Text("종료하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) { Text("나가기") }
            },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("취소") } },
        )
    }

    when (val s = screen) {
        AppScreen.Loading -> LoadingScreen(loadingMessage, loadingDetail)
        AppScreen.Setup -> SetupFlowScreen(
            initial = config,
            store = store,
            locationHelper = locationHelper,
            geocoder = geocoder,
            onDone = { updated, newRegions ->
                config = updated
                regions = newRegions
                screen = AppScreen.Hub
            },
            onCancel = { screen = AppScreen.Hub },
        )
        AppScreen.Hub -> HubScreen(
            config = config,
            regions = regions,
            highlightId = highlightId,
            blinkHighlight = blinkHighlight,
            listState = listState,
            dropdownExpanded = dropdownExpanded,
            onDropdownExpanded = { dropdownExpanded = it },
            onRegionMapTap = { r ->
                highlightId = r.id
                blinkHighlight = false
                val idx = regions.indexOfFirst { it.id == r.id }
                if (idx >= 0) {
                    scope.launch { listState.animateScrollToItem(idx) }
                }
            },
            onRegionSelect = { r ->
                highlightId = r.id
                blinkHighlight = true
                showMoveConfirm = r
            },
            onSchedule = { screen = AppScreen.Setup },
            onOptions = { showOptions = true },
            onExit = { showExitConfirm = true },
        )
        is AppScreen.Map -> {
            val region = regions.find { it.id == s.regionId }
            if (region == null) {
                screen = AppScreen.Hub
            } else {
                MapGuideScreen(
                    region = region,
                    config = config,
                    simulateGps = s.simulated,
                    onOpenOptions = { showOptions = true },
                    onMainHub = { screen = AppScreen.Hub },
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(title: String, detail: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("도보 여행 어플 (오프라인)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(title, textAlign = TextAlign.Center, color = Color(0xFFDC2626))
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(detail, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF334155))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubScreen(
    config: TripConfig,
    regions: List<RegionRecord>,
    highlightId: String?,
    blinkHighlight: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    dropdownExpanded: Boolean,
    onDropdownExpanded: (Boolean) -> Unit,
    onRegionMapTap: (RegionRecord) -> Unit,
    onRegionSelect: (RegionRecord) -> Unit,
    onSchedule: () -> Unit,
    onOptions: () -> Unit,
    onExit: () -> Unit,
) {
    val completed = regions.filter { it.downloadComplete }
    val homeFirst = completed.sortedWith(
        compareByDescending<RegionRecord> {
            it.countryLabel == config.homeCountry && it.countryLabel.isNotBlank()
        }.thenBy { if (it.visited) 0 else 1 }.thenBy { it.cityName },
    )

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onOptions) {
                    Icon(Icons.Default.Settings, contentDescription = "옵션")
                }
                Text(
                    "도보 여행 어플 (오프라인)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Spacer(Modifier.weight(1f))
            }

            WorldMapCanvas(
                regions = completed,
                highlightId = highlightId,
                blinkHighlight = blinkHighlight,
                onRegionTap = onRegionMapTap,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )

            Text(
                "● 밝은색=방문 · ● 어두운색=다운로드만",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp),
            )

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = onDropdownExpanded,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = homeFirst.find { it.id == highlightId }?.let { "${it.countryLabel} · ${it.cityName}" }
                        ?: "다운로드 완료 지역 (${homeFirst.size})",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { onDropdownExpanded(false) }) {
                    homeFirst.forEach { r ->
                        val label = "${r.countryLabel.ifBlank { "—" }} · ${r.cityName}" +
                            if (r.visited) " (방문)" else ""
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onDropdownExpanded(false)
                                onRegionSelect(r)
                            },
                        )
                    }
                }
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                itemsIndexed(homeFirst) { _, r ->
                    val bg = if (r.id == highlightId) Color(0xFFE0F2FE) else Color.Transparent
                    Text(
                        "${r.countryLabel.ifBlank { "—" }} · ${r.cityName}${if (r.visited) " ✓" else ""}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRegionSelect(r) }
                            .padding(8.dp),
                        color = if (r.visited) Color(0xFF15803D) else Color(0xFF475569),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
                Text("여행 스케줄 짜기")
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("나가기")
            }
        }
    }
}

@Composable
private fun SetupFlowScreen(
    initial: TripConfig,
    store: TripStore,
    locationHelper: LocationHelper,
    geocoder: NominatimGeocoder,
    onDone: (TripConfig, List<RegionRecord>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember { RegionDownloadManager(context, store) }

    var step by remember { mutableIntStateOf(1) }
    var homeCountry by remember { mutableStateOf(initial.homeCountry) }
    var destCountry by remember { mutableStateOf(initial.destinationCountry) }
    var tripStart by remember { mutableStateOf(PoiLogic.formatDate(initial.tripStartEpochDay.takeIf { it > 0 } ?: LocalDate.now().toEpochDay())) }
    var tripEnd by remember { mutableStateOf(PoiLogic.formatDate(initial.tripEndEpochDay.takeIf { it > 0 } ?: LocalDate.now().plusDays(7).toEpochDay())) }
    var showHotel by remember { mutableStateOf(initial.showHotel) }
    var showRestaurant by remember { mutableStateOf(initial.showRestaurant) }
    var showSight by remember { mutableStateOf(initial.showSight) }
    var autoDelete by remember { mutableStateOf(initial.autoDeleteAfterTrip) }
    var skipHub by remember { mutableStateOf(initial.skipHubMenu) }

    val legs = remember {
        mutableStateListOf(
            initial.legs.firstOrNull() ?: ScheduleLeg(
                id = store.newLegId(),
                walkStart = "",
                waypoints = emptyList(),
                walkDestination = "",
            ),
        )
    }
    var waypointInputs = remember { mutableStateListOf("") }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    var error by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (homeCountry.isBlank()) {
            runCatching {
                val pos = locationHelper.acquirePosition()
                val geo = geocoder.reverse(pos.lat, pos.lon) ?: return@runCatching
                homeCountry = geo.name.ifBlank { geo.displayName.split(",").lastOrNull()?.trim().orEmpty() }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("여행 설정 ${step}/2", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (step == 1) {
            Text("1단계 — 기본 설정", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(homeCountry, { homeCountry = it }, label = { Text("모국 (GPS/WiFi 기반)") }, modifier = Modifier.fillMaxWidth())
            Text("원하는 정보", modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(showHotel, { showHotel = !showHotel }, { Text("숙소") })
                FilterChip(showRestaurant, { showRestaurant = !showRestaurant }, { Text("음식점") })
                FilterChip(showSight, { showSight = !showSight }, { Text("명소") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(autoDelete, { autoDelete = it })
                Text("여행 기간 종료 후 자동 삭제")
            }
            Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("다음") }
        } else {
            Text("2단계 — 목적지·일정", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            OutlinedTextField(destCountry, { destCountry = it }, label = { Text("여행국") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tripStart, { tripStart = it }, label = { Text("시작일 (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tripEnd, { tripEnd = it }, label = { Text("종료일") }, modifier = Modifier.fillMaxWidth())

            Text(
                "일정을 나눠 추가하시면 다운로드가 더 정확해집니다.\n하루에 여러 도시·한 도시 며칠 모두 가능합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            legs.forEachIndexed { idx, leg ->
                Text("일정 ${idx + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    leg.walkStart,
                    { legs[idx] = leg.copy(walkStart = it) },
                    label = { Text("도보 시작지 (도시명)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                waypointInputs.forEachIndexed { wi, w ->
                    OutlinedTextField(
                        w,
                        { waypointInputs[wi] = it },
                        label = { Text("경유지 ${wi + 1}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { waypointInputs.add("") }) {
                    Icon(Icons.Default.Add, "경유지 추가")
                }
                OutlinedTextField(
                    leg.walkDestination,
                    { legs[idx] = leg.copy(walkDestination = it) },
                    label = { Text("도보 목적지") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("도시 이름으로 적어주세요. 경유지가 많을수록 좋습니다.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            TextButton(onClick = {
                legs.add(ScheduleLeg(id = store.newLegId()))
            }) { Text("+ 일정 추가") }

            downloadProgress?.let { p ->
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text(p.label, style = MaterialTheme.typography.bodySmall)
            }

            if (downloading) {
                OutlinedButton(
                    onClick = {
                        downloader.cancelled = true
                        downloading = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("다운로드 취소") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
                Button(
                    onClick = {
                        scope.launch {
                            error = ""
                            downloading = true
                            val stops = buildCityStops(legs, waypointInputs)
                            if (stops.isEmpty()) {
                                error = "도시 이름을 입력해 주세요."
                                downloading = false
                                return@launch
                            }
                            try {
                                val newRegions = downloader.downloadLeg(destCountry, stops) { downloadProgress = it }
                                val updated = TripConfig(
                                    homeCountry = homeCountry,
                                    destinationCountry = destCountry,
                                    tripStartEpochDay = PoiLogic.parseDate(tripStart).toEpochDay(),
                                    tripEndEpochDay = PoiLogic.parseDate(tripEnd).toEpochDay(),
                                    legs = legs.mapIndexed { i, l ->
                                        l.copy(
                                            waypoints = waypointInputs.filter { it.isNotBlank() },
                                            dayStart = i,
                                            dayEnd = i,
                                        )
                                    },
                                    showHotel = showHotel,
                                    showRestaurant = showRestaurant,
                                    showSight = showSight,
                                    autoDeleteAfterTrip = autoDelete,
                                    skipHubMenu = skipHub,
                                    setupComplete = true,
                                )
                                store.saveConfig(updated)
                                val allRegions = store.loadRegions()
                                downloading = false
                                onDone(updated, allRegions)
                            } catch (e: Exception) {
                                error = e.message ?: "다운로드 실패"
                                downloading = false
                            }
                        }
                    },
                    enabled = !downloading,
                    modifier = Modifier.weight(1f),
                ) { Text("다운로드 시작") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Checkbox(skipHub, { skipHub = it })
                Text("메뉴 다시 보지 않기")
            }
            if (error.isNotBlank()) Text(error, color = Color.Red)
        }
    }
}

private fun buildCityStops(legs: List<ScheduleLeg>, waypoints: List<String>): List<CityStop> {
    val names = linkedSetOf<String>()
    legs.forEach { leg ->
        if (leg.walkStart.isNotBlank()) names.add(leg.walkStart.trim())
        waypoints.filter { it.isNotBlank() }.forEach { names.add(it.trim()) }
        if (leg.walkDestination.isNotBlank()) names.add(leg.walkDestination.trim())
    }
    return names.map { CityStop(name = it) }
}

@Composable
private fun OptionsDialog(
    config: TripConfig,
    onDismiss: () -> Unit,
    onConfirm: (TripConfig) -> Unit,
    onDeleteAllMaps: () -> Unit,
    onExit: () -> Unit,
    onMain: () -> Unit,
) {
    var showHotel by remember { mutableStateOf(config.showHotel) }
    var showRestaurant by remember { mutableStateOf(config.showRestaurant) }
    var showSight by remember { mutableStateOf(config.showSight) }
    var autoDelete by remember { mutableStateOf(config.autoDeleteAfterTrip) }
    var manualDelete by remember { mutableStateOf(config.manualDeletePrompt) }
    var skipHub by remember { mutableStateOf(config.skipHubMenu) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("옵션") },
        text = {
            Column {
                Text("표시 정보", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(showHotel, { showHotel = !showHotel }, { Text("숙소") })
                    FilterChip(showRestaurant, { showRestaurant = !showRestaurant }, { Text("음식점") })
                    FilterChip(showSight, { showSight = !showSight }, { Text("명소") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(autoDelete, { autoDelete = it })
                    Text("여행 후 자동 삭제")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(manualDelete, { manualDelete = it })
                    Text("수동 삭제 안내")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(skipHub, { skipHub = it })
                    Text("메뉴 다시 보지 않기")
                }
                TextButton(onClick = onDeleteAllMaps) { Text("지도 데이터 전부 삭제", color = Color.Red) }
                TextButton(onClick = onMain) { Text("메인으로") }
                TextButton(onClick = onExit) { Text("나가기") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    config.copy(
                        showHotel = showHotel,
                        showRestaurant = showRestaurant,
                        showSight = showSight,
                        autoDeleteAfterTrip = autoDelete,
                        manualDeletePrompt = manualDelete,
                        skipHubMenu = skipHub,
                    ),
                )
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
