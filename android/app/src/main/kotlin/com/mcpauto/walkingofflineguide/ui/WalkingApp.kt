package com.mcpauto.walkingofflineguide.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.mcpauto.walkingofflineguide.map.MapUiColors
import com.mcpauto.walkingofflineguide.data.DownloadJobState
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.data.isInTripPeriod
import com.mcpauto.walkingofflineguide.download.BatteryOptimizationHelper
import com.mcpauto.walkingofflineguide.download.DownloadEvent
import com.mcpauto.walkingofflineguide.download.DownloadSession
import com.mcpauto.walkingofflineguide.download.RegionDownloadForegroundService
import com.mcpauto.walkingofflineguide.download.DownloadProgress
import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import com.mcpauto.walkingofflineguide.logic.LocationHelper
import com.mcpauto.walkingofflineguide.logic.MapAppPolicy
import com.mcpauto.walkingofflineguide.logic.MapPolicy
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.logic.TripNavigation
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.WifiGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private sealed class AppScreen {
    data object Loading : AppScreen()
    data object Hub : AppScreen()
    data object BasicSetup : AppScreen()
    data class TravelSetup(val resumeDownload: Boolean = false) : AppScreen()
    data class Map(val regionId: String, val simulated: Boolean = false) : AppScreen()
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
    var showNeedTravelSetup by remember { mutableStateOf(false) }
    var policyNotice by remember { mutableStateOf<String?>(null) }
    var pendingDownloadJob by remember { mutableStateOf<DownloadJobState?>(null) }
    var showResetBasicConfirm by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var lastGps by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    fun routeByPolicy(
        cfg: TripConfig,
        regs: List<RegionRecord>,
        job: DownloadJobState?,
        gpsLat: Double?,
        gpsLon: Double?,
        hasGpsFix: Boolean,
        hasInternet: Boolean,
    ): AppScreen {
        if (!cfg.basicSetupComplete) return AppScreen.BasicSetup
        if (job?.active == true) return AppScreen.TravelSetup(resumeDownload = true)

        val decision = MapPolicy.decide(cfg, regs, gpsLat, gpsLon, hasGpsFix, hasInternet)
        return when (decision.policy) {
            MapAppPolicy.HOME_LIVE -> {
                MapPolicy.homeLiveRegion(cfg, regs, gpsLat, gpsLon)?.let {
                    AppScreen.Map(it.id, simulated = false)
                } ?: run {
                    showNeedTravelSetup = true
                    AppScreen.Hub
                }
            }
            MapAppPolicy.TRAVEL -> {
                MapPolicy.travelRegion(cfg, regs, gpsLat, gpsLon, hasGpsFix)?.let {
                    AppScreen.Map(it.id, simulated = false)
                } ?: AppScreen.Hub
            }
            MapAppPolicy.NEED_TRAVEL_SETUP -> {
                showNeedTravelSetup = true
                AppScreen.Hub
            }
        }
    }

    fun rerouteFromMap(reason: String) {
        policyNotice = reason
        val gps = lastGps
        val hasNet = WifiGate.hasInternet(context)
        val hasFix = gps != null
        screen = routeByPolicy(
            config, regions, pendingDownloadJob, gps?.first, gps?.second, hasFix, hasNet,
        )
    }

    LaunchedEffect(Unit) {
        loadingMessage = "데이터 소모가 큽니다.\n가능한 WiFi가 있는 곳에서 실행해 주세요."
        delay(1200)
        config = store.loadConfig()
        if (config.homeCountry.isNotBlank() && !config.basicSetupComplete) {
            config = config.copy(basicSetupComplete = true)
            store.saveConfig(config)
        }
        regions = store.loadRegions()
        pendingDownloadJob = store.loadDownloadJob()
        loadingDetail = store.todayCityDescription(config, regions)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                val fix = locationHelper.acquirePosition()
                lastGps = fix.lat to fix.lon
                if (config.homeLat == 0.0) {
                    config = config.copy(homeLat = fix.lat, homeLon = fix.lon)
                    store.saveConfig(config)
                }
            }
        }
        delay(1500)
        val hasInternet = WifiGate.hasInternet(context)
        val gps = lastGps
        val hasGpsFix = gps != null
        if (!hasInternet && hasGpsFix && gps != null &&
            TripNavigation.isAtHomeCountry(config, gps.first, gps.second)
        ) {
            policyNotice = "WiFi/인터넷 없음 — 일반 지도 대신 여행 오프라인 모드로 시작합니다."
        }
        screen = routeByPolicy(
            config, regions, pendingDownloadJob, gps?.first, gps?.second, hasGpsFix, hasInternet,
        )
        if (screen is AppScreen.Hub && hasGpsFix && gps != null &&
            TripNavigation.isAtHomeCountry(config, gps.first, gps.second)
        ) {
            highlightId = TripNavigation.resolveHomeMapRegion(config, regions, gps.first, gps.second)?.id
        }
    }

    policyNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { policyNotice = null },
            title = { Text("지도 모드 안내") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { policyNotice = null }) { Text("확인") }
            },
        )
    }

    if (showNeedTravelSetup) {
        AlertDialog(
            onDismissRequest = { showNeedTravelSetup = false },
            title = { Text("여행 설정 필요") },
            text = {
                Text(
                    "일반 지도(모국 GPS + WiFi)를 쓰려면 둘 다 필요합니다.\n\n" +
                        "그렇지 않을 때는 여행 목적지·일정 설정 후 오프라인 지도를 받아 주세요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNeedTravelSetup = false
                    screen = if (config.basicSetupComplete) {
                        AppScreen.TravelSetup(resumeDownload = false)
                    } else {
                        AppScreen.BasicSetup
                    }
                }) { Text("여행 설정") }
            },
            dismissButton = {
                TextButton(onClick = { showNeedTravelSetup = false }) { Text("허브") }
            },
        )
    }

    if (showNoWifiDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("WiFi 없음") },
            text = { Text("모바일 데이터로 진행하면 요금이 발생할 수 있습니다.\n여행 오프라인 모드로 계속합니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showNoWifiDialog = false
                    val gps = lastGps
                    val hasNet = WifiGate.hasInternet(context)
                    screen = routeByPolicy(
                        config, regions, pendingDownloadJob,
                        gps?.first, gps?.second, gps != null, hasNet,
                    )
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
            text = { Text("해당 도시로 이동하시겠습니까?\n(GPS 위치는 유지됩니다)") },
            confirmButton = {
                TextButton(onClick = {
                    showMoveConfirm = null
                    scope.launch {
                        store.saveRegion(target.copy(visited = true))
                        regions = store.loadRegions()
                    }
                    screen = AppScreen.Map(target.id, simulated = false)
                }) { Text("이동") }
            },
            dismissButton = { TextButton(onClick = { showMoveConfirm = null }) { Text("취소") } },
        )
    }

    if (showOptions) {
        OptionsScreen(
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
                    pendingDownloadJob = null
                    showOptions = false
                }
            },
            onResetBasic = { showResetBasicConfirm = true },
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

    if (showResetBasicConfirm) {
        AlertDialog(
            onDismissRequest = { showResetBasicConfirm = false },
            title = { Text("기본 설정 초기화") },
            text = { Text("모국·표시 정보 등 기본 설정을 처음부터 다시 입력합니다.\n여행 일정·다운로드 지도는 유지됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetBasicConfirm = false
                    showOptions = false
                    scope.launch {
                        config = config.copy(basicSetupComplete = false)
                        store.saveConfig(config)
                        screen = AppScreen.BasicSetup
                    }
                }) { Text("초기화", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { showResetBasicConfirm = false }) { Text("취소") } },
        )
    }

    when (val s = screen) {
        AppScreen.Loading -> LoadingScreen(loadingMessage, loadingDetail)
        AppScreen.BasicSetup -> BasicSetupScreen(
            initial = config,
            store = store,
            locationHelper = locationHelper,
            geocoder = geocoder,
            onDone = { updated ->
                scope.launch {
                    config = updated
                    store.saveConfig(updated)
                    screen = AppScreen.Hub
                }
            },
        )
        is AppScreen.TravelSetup -> TravelSetupScreen(
            initial = config,
            resumeJob = if (s.resumeDownload) pendingDownloadJob else null,
            store = store,
            geocoder = geocoder,
            onDone = { updated, newRegions, firstRegion ->
                config = updated
                regions = newRegions
                pendingDownloadJob = null
                if (firstRegion != null) {
                    screen = AppScreen.Map(firstRegion.id, simulated = false)
                } else {
                    screen = AppScreen.Hub
                }
            },
            onCancel = { screen = AppScreen.Hub },
            onJobUpdated = { pendingDownloadJob = it },
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
            onSchedule = {
                screen = if (config.basicSetupComplete) {
                    AppScreen.TravelSetup(resumeDownload = false)
                } else {
                    AppScreen.BasicSetup
                }
            },
            onOptions = { showOptions = true },
            onExit = { showExitConfirm = true },
        )
        is AppScreen.Map -> {
            val gps = lastGps
            val region = regions.find { it.id == s.regionId }
                ?: if (s.regionId == MapPolicy.HOME_LIVE_ONLINE_ID && gps != null) {
                    MapPolicy.onlineHomeStub(config, gps.first, gps.second)
                } else {
                    null
                }
            if (region == null) {
                screen = AppScreen.Hub
            } else {
                MapGuideScreen(
                    region = region,
                    config = config,
                    regions = regions,
                    simulateGps = s.simulated,
                    locationHelper = locationHelper,
                    onOpenOptions = { showOptions = true },
                    onMainHub = { screen = AppScreen.Hub },
                    onPolicyFallback = { reason -> rerouteFromMap(reason) },
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen(title: String, detail: String) {
    Row(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                "도보 여행 어플 (오프라인)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        ) {
            Text(title, color = Color(0xFFDC2626), style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155),
                )
            }
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

    Scaffold(
        containerColor = Color(0xFFF4F6F8),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOptions) {
                    Icon(Icons.Default.Settings, contentDescription = "옵션")
                }
                Text(
                    "도보 여행 어플 (오프라인)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(48.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                WorldMapCanvas(
                    regions = completed,
                    highlightId = highlightId,
                    blinkHighlight = blinkHighlight,
                    onRegionTap = onRegionMapTap,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f),
                )
                Text(
                    "● 밝은색=방문 · ● 어두운색=다운로드만",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                        .zIndex(1f),
                )
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.42f)
                        .zIndex(2f)
                        .background(MapUiColors.sidePanelBg, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "다운로드한 도시를 지도에서 고르거나, 목록에서 탭하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
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
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { onDropdownExpanded(false) },
                        ) {
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

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).padding(top = 4.dp),
                    ) {
                        itemsIndexed(homeFirst) { _, r ->
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
                    Text(
                        "새 여행국·일정을 입력하고 지도·명소 데이터를 받습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Text("나가기")
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicSetupScreen(
    initial: TripConfig,
    store: TripStore,
    locationHelper: LocationHelper,
    geocoder: NominatimGeocoder,
    onDone: (TripConfig) -> Unit,
) {
    val context = LocalContext.current
    val catalog = remember { GeoCatalog(context) }
    var homeCountry by remember { mutableStateOf(initial.homeCountry) }
    var showHotel by remember { mutableStateOf(initial.showHotel) }
    var showRestaurant by remember { mutableStateOf(initial.showRestaurant) }
    var showSight by remember { mutableStateOf(initial.showSight) }
    var autoDelete by remember { mutableStateOf(initial.autoDeleteAfterTrip) }
    var homeCountryCode by remember {
        mutableStateOf(
            initial.homeCountryCode.ifBlank { null }
                ?: catalog.resolveCountry(initial.homeCountry)?.code,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    var homeLat by remember { mutableStateOf(initial.homeLat) }
    var homeLon by remember { mutableStateOf(initial.homeLon) }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            runCatching {
                val pos = locationHelper.acquirePosition()
                homeLat = pos.lat
                homeLon = pos.lon
                if (homeCountry.isBlank()) {
                    val geo = geocoder.reverse(pos.lat, pos.lon) ?: return@runCatching
                    homeCountry = geo.name.ifBlank { geo.displayName.split(",").lastOrNull()?.trim().orEmpty() }
                    catalog.resolveCountry(homeCountry)?.let { homeCountryCode = it.code }
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            Text("기본 설정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "처음 한 번만 설정합니다. 이후 변경은 옵션 → 「기본 설정 초기화」에서만 다시 입력할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            CountryAutocompleteField(
                homeCountry,
                homeCountryCode,
                { homeCountry = it },
                { c -> homeCountryCode = c?.code },
                catalog,
                "모국 (GPS·자동완성)",
            )
            Text("원하는 정보", modifier = Modifier.padding(top = 8.dp))
            Text("켜 둔 종류만 지도·목록에 표시됩니다.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(showHotel, { showHotel = !showHotel }, { Text("숙소") })
                FilterChip(showRestaurant, { showRestaurant = !showRestaurant }, { Text("음식점") })
                FilterChip(showSight, { showSight = !showSight }, { Text("명소") })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(autoDelete, { autoDelete = it })
                Text("여행 기간 종료 후 자동 삭제")
            }
        }
        Column(
            Modifier.weight(0.55f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    val code = homeCountryCode ?: catalog.resolveCountry(homeCountry)?.code ?: "KR"
                    onDone(
                        initial.copy(
                            homeCountry = homeCountry,
                            homeCountryCode = code,
                            homeLat = homeLat,
                            homeLon = homeLon,
                            showHotel = showHotel,
                            showRestaurant = showRestaurant,
                            showSight = showSight,
                            autoDeleteAfterTrip = autoDelete,
                            basicSetupComplete = true,
                        ),
                    )
                },
                enabled = homeCountry.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("기본 설정 저장") }
        }
    }
}

@Composable
private fun TravelSetupScreen(
    initial: TripConfig,
    resumeJob: DownloadJobState?,
    store: TripStore,
    geocoder: NominatimGeocoder,
    onDone: (TripConfig, List<RegionRecord>, RegionRecord?) -> Unit,
    onCancel: () -> Unit,
    onJobUpdated: (DownloadJobState?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val catalog = remember { GeoCatalog(context) }

    var step by remember { mutableIntStateOf(if (resumeJob != null) 2 else 1) }
    var destCountry by remember {
        mutableStateOf(resumeJob?.destinationCountry?.ifBlank { resumeJob.countryLabel } ?: initial.destinationCountry)
    }
    var tripStart by remember {
        mutableStateOf(
            when {
                resumeJob != null && resumeJob.tripStartEpochDay > 0 ->
                    PoiLogic.formatDate(resumeJob.tripStartEpochDay)
                initial.tripStartEpochDay > 0 -> PoiLogic.formatDate(initial.tripStartEpochDay)
                else -> PoiLogic.formatDate(LocalDate.now().toEpochDay())
            },
        )
    }
    var tripEnd by remember {
        mutableStateOf(
            when {
                resumeJob != null && resumeJob.tripEndEpochDay > 0 ->
                    PoiLogic.formatDate(resumeJob.tripEndEpochDay)
                initial.tripEndEpochDay > 0 -> PoiLogic.formatDate(initial.tripEndEpochDay)
                else -> PoiLogic.formatDate(LocalDate.now().plusDays(7).toEpochDay())
            },
        )
    }
    var skipHub by remember { mutableStateOf(resumeJob?.skipHubMenu ?: initial.skipHubMenu) }
    var scheduleLocked by remember { mutableStateOf(resumeJob != null) }
    var destCountryCode by remember { mutableStateOf<String?>(null) }
    val homeCountryCode = initial.homeCountryCode.ifBlank { catalog.resolveCountry(initial.homeCountry)?.code ?: "KR" }

    val legs = remember(resumeJob) {
        mutableStateListOf<ScheduleLeg>().also { list ->
            val seed = resumeJob?.legs?.takeIf { it.isNotEmpty() }?.map { it.copy(legConfirmed = true) }
                ?: initial.legs.takeIf { it.isNotEmpty() }?.map { it.copy(legConfirmed = false) }
                ?: listOf(ScheduleLeg(id = store.newLegId()))
            list.addAll(seed)
        }
    }
    var downloading by remember { mutableStateOf(DownloadSession.running.value) }
    var downloadProgress by remember { mutableStateOf(DownloadSession.progress.value) }
    var error by remember { mutableStateOf("") }
    var legToDelete by remember { mutableStateOf<Int?>(null) }
    var resumeStarted by remember { mutableStateOf(false) }
    var showBatteryOptDialog by remember { mutableStateOf(false) }
    var pendingDownloadSeed by remember { mutableStateOf<DownloadJobState?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> /* 거부해도 포그라운드 다운로드는 시도 */ }

    LaunchedEffect(Unit) {
        DownloadSession.progress.collect { downloadProgress = it }
    }
    LaunchedEffect(Unit) {
        DownloadSession.running.collect { downloading = it }
    }
    LaunchedEffect(Unit) {
        DownloadSession.events.collect { event ->
            when (event) {
                is DownloadEvent.Completed -> onDone(event.config, event.regions, event.entry)
                is DownloadEvent.Cancelled -> {
                    error = "다운로드가 중단되었습니다. 앱을 다시 열면 이어받기됩니다."
                    val job = store.loadDownloadJob()
                    onJobUpdated(job)
                }
                is DownloadEvent.Failed -> error = event.message
            }
        }
    }

    LaunchedEffect(destCountry) {
        catalog.resolveCountry(destCountry)?.let { destCountryCode = it.code }
    }

    fun buildJob(jobSeed: DownloadJobState? = null): DownloadJobState? {
        val stops = buildStopsFromLegs(legs)
        if (stops.isEmpty()) return null
        return DownloadJobState(
            active = true,
            countryLabel = destCountry,
            homeCountryCode = homeCountryCode,
            destinationCountry = destCountry,
            tripStartEpochDay = PoiLogic.parseDate(tripStart).toEpochDay(),
            tripEndEpochDay = PoiLogic.parseDate(tripEnd).toEpochDay(),
            legs = legs.toList(),
            skipHubMenu = skipHub,
            stops = stops,
            finishedCityNames = jobSeed?.finishedCityNames ?: emptyList(),
        )
    }

    fun launchDownloadService(jobSeed: DownloadJobState? = null) {
        error = ""
        val job = buildJob(jobSeed)
        if (job == null) {
            error = "확정된 도시가 없습니다."
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        onJobUpdated(job)
        scope.launch { store.saveDownloadJob(job) }
        RegionDownloadForegroundService.start(context, job)
    }

    /** 다운로드 시작 — 미제외 시 배터리 최적화 해제 먼저 요청 */
    fun requestDownload(jobSeed: DownloadJobState? = null) {
        if (BatteryOptimizationHelper.isExempt(context)) {
            launchDownloadService(jobSeed)
            return
        }
        pendingDownloadSeed = jobSeed
        showBatteryOptDialog = true
    }

    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (BatteryOptimizationHelper.isExempt(context)) {
            launchDownloadService(pendingDownloadSeed)
            pendingDownloadSeed = null
            showBatteryOptDialog = false
        }
    }

    DisposableEffect(lifecycleOwner, showBatteryOptDialog, pendingDownloadSeed) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showBatteryOptDialog &&
                BatteryOptimizationHelper.isExempt(context) && pendingDownloadSeed != null
            ) {
                launchDownloadService(pendingDownloadSeed)
                pendingDownloadSeed = null
                showBatteryOptDialog = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeJob) {
        if (resumeJob != null && !resumeStarted && resumeJob.active) {
            resumeStarted = true
            if (!DownloadSession.running.value) {
                requestDownload(resumeJob)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("여행 설정 $step/2", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            when (step) {
                1 -> "여행국·기간·도시 일정을 입력합니다. 도시마다 확인 버튼을 눌러 주세요."
                else -> "WiFi에서 지도·명소·도보 경로를 받습니다. 다른 앱으로 나가도 알림으로 계속 받습니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        if (initial.homeCountry.isNotBlank()) {
            Text("모국: ${initial.homeCountry}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        when (step) {
            1 -> {
                Text("1단계 — 목적지·일정", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                CountryAutocompleteField(
                    destCountry,
                    destCountryCode,
                    { destCountry = it; scheduleLocked = false },
                    { c ->
                        destCountryCode = c?.code
                        scheduleLocked = false
                    },
                    catalog,
                    "여행국 (자동완성)",
                )
                if (destCountry.isNotBlank()) {
                    catalog.resolveCountry(destCountry)?.let { c ->
                        Text("선택: ${c.nameKo} · 수도 ${c.capital}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                DateMaskField(tripStart, { tripStart = it; scheduleLocked = false }, "시작일")
                DateMaskField(tripEnd, { tripEnd = it; scheduleLocked = false }, "종료일")
                Text(
                    "일정을 나눠 추가하시면 다운로드가 더 정확해집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                legs.forEachIndexed { idx, leg ->
                    LegRouteEditor(
                        leg = leg,
                        legIndex = idx,
                        countryHint = destCountry,
                        catalog = catalog,
                        geocoder = geocoder,
                        onLegChange = { updated ->
                            legs[idx] = updated
                            scheduleLocked = false
                        },
                        onDeleteLeg = { legToDelete = idx },
                        canDelete = legs.size > 1 || leg.startPoint.name.isNotBlank() ||
                            leg.endPoint.name.isNotBlank() || leg.waypoints.any { it.name.isNotBlank() },
                    )
                    if (isLegReady(leg) && !leg.legConfirmed) {
                        Button(
                            onClick = { legs[idx] = leg.copy(legConfirmed = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("이 일정 확정") }
                    }
                }
                TextButton(onClick = {
                    legs.add(ScheduleLeg(id = store.newLegId()))
                    scheduleLocked = false
                }) { Text("+ 다른 일정 추가") }

                val allLegsReady = legs.isNotEmpty() && legs.all { isLegReady(it) && it.legConfirmed }
                Button(
                    onClick = {
                        if (!allLegsReady) {
                            error = "모든 일정에서 출발·도착 확인 후 「이 일정 확정」을 눌러 주세요."
                            return@Button
                        }
                        scheduleLocked = true
                        error = ""
                        step = 2
                    },
                    enabled = allLegsReady,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("일정 전체 확정 → 다음") }
            }
            2 -> {
                Text("2단계 — 다운로드", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                if (resumeJob != null) {
                    Text(
                        "이전 다운로드 이어받기 중… (${resumeJob.finishedCityNames.size}/${resumeJob.stops.size.coerceAtLeast(1)} 도시 완료)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    "지도(글자 없음·고해상도), 명소, 도보 도로를 각 지점 주변 ${com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM.toInt()}km만 받습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                )
                Text(
                    scheduleSummary(legs, destCountry),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val dl = remember(legs, scheduleLocked) { RegionDownloadManager(context, store) }
                val stops = buildStopsFromLegs(legs)
                val estBytes = dl.estimateBytes(stops)
                val estMin = dl.estimateDurationMinutes(stops)
                Text(
                    "예상 용량: 약 ${RegionDownloadManager.formatSize(estBytes)} · 대략 ${estMin}분\n(지도+명소+번역+도보경로, WiFi·명소 수에 따라 달라짐)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )

                downloadProgress?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    if (downloading && p.percent <= 3) {
                        Text(
                            "잠시만 기다려 주세요… (${p.phase}${if (p.phaseDetail.isNotBlank()) " · ${p.phaseDetail}" else ""})",
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { p.percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(p.label, style = MaterialTheme.typography.bodySmall)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            step = 1
                            scheduleLocked = false
                        },
                        enabled = !downloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("이전으로 가기")
                    }
                    Button(
                        onClick = {
                            if (downloading) {
                                RegionDownloadForegroundService.cancel(context)
                                return@Button
                            }
                            requestDownload(resumeJob)
                        },
                        enabled = scheduleLocked,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (downloading) "다운로드 중단" else if (resumeJob != null) "이어받기" else "다운로드 시작") }
                }

                if (!BatteryOptimizationHelper.isExempt(context) && !downloading) {
                    Text(
                        "다운로드 전 「배터리 최적화 제외」 허용을 권장합니다 (화면 꺼짐·Doze 대비).",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2563EB),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(skipHub, { skipHub = it })
                    Text("여행기간 동안 메뉴 다시 보지 않기")
                }
                Text(
                    "체크 시 여행 시작~종료일 사이 앱 실행 시 메뉴 없이 바로 지도로 들어갑니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("취소") }
        if (error.isNotBlank()) Text(error, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
    }

    if (showBatteryOptDialog) {
        AlertDialog(
            onDismissRequest = {
                showBatteryOptDialog = false
                pendingDownloadSeed = null
            },
            title = { Text("배터리 최적화 제외") },
            text = {
                Text(
                    "화면이 꺼지거나 기기가 깊은 절전(Doze)에 들어가도 다운로드가 끊기지 않도록, " +
                        "이 앱의 배터리 최적화를 해제해 주세요.\n\n" +
                        "Android는 한 번 허용하면 유지됩니다(다운로드할 때만 묻습니다). " +
                        "WakeLock·WifiLock과 함께 동작합니다.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val intent = BatteryOptimizationHelper.requestExemptionIntent(context)
                            ?: BatteryOptimizationHelper.openBatterySettingsIntent(context)
                        runCatching { batteryOptLauncher.launch(intent) }
                            .onFailure {
                                runCatching {
                                    context.startActivity(
                                        BatteryOptimizationHelper.openBatterySettingsIntent(context),
                                    )
                                }
                            }
                    },
                ) { Text("허용 설정 열기") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBatteryOptDialog = false
                        pendingDownloadSeed?.let { launchDownloadService(it) }
                        pendingDownloadSeed = null
                    },
                ) { Text("건너뛰고 시작") }
            },
        )
    }

    legToDelete?.let { idx ->
        AlertDialog(
            onDismissRequest = { legToDelete = null },
            title = { Text("일정 삭제") },
            text = { Text("일정 ${idx + 1}을(를) 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    if (idx in legs.indices) {
                        legs.removeAt(idx)
                        if (legs.isEmpty()) {
                            legs.add(ScheduleLeg(id = store.newLegId()))
                        }
                    }
                    scheduleLocked = false
                    legToDelete = null
                }) { Text("삭제", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(onClick = { legToDelete = null }) { Text("취소") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsScreen(
    config: TripConfig,
    onDismiss: () -> Unit,
    onConfirm: (TripConfig) -> Unit,
    onDeleteAllMaps: () -> Unit,
    onResetBasic: () -> Unit,
    onExit: () -> Unit,
    onMain: () -> Unit,
) {
    var showHotel by remember { mutableStateOf(config.showHotel) }
    var showRestaurant by remember { mutableStateOf(config.showRestaurant) }
    var showSight by remember { mutableStateOf(config.showSight) }
    var autoDelete by remember { mutableStateOf(config.autoDeleteAfterTrip) }
    var manualDelete by remember { mutableStateOf(config.manualDeletePrompt) }

    Scaffold(
        containerColor = Color(0xFFF4F6F8),
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "닫기")
                }
                Text(
                    "옵션",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onConfirm(
                            config.copy(
                                showHotel = showHotel,
                                showRestaurant = showRestaurant,
                                showSight = showSight,
                                autoDeleteAfterTrip = autoDelete,
                                manualDeletePrompt = manualDelete,
                            ),
                        )
                    },
                ) { Text("저장") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text("표시 정보", fontWeight = FontWeight.Bold)
                Text(
                    "끄면 지도·목록에서 해당 종류가 숨겨집니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(showHotel, { showHotel = !showHotel }, { Text("숙소") })
                    FilterChip(showRestaurant, { showRestaurant = !showRestaurant }, { Text("음식점") })
                    FilterChip(showSight, { showSight = !showSight }, { Text("명소") })
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text("다운로드 지도", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(autoDelete, { autoDelete = it })
                    Text("여행 후 자동 삭제")
                }
                Text(
                    "종료일이 지나면 다운로드 지도를 자동으로 지웁니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(manualDelete, { manualDelete = it })
                    Text("수동 삭제 안내")
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TextButton(onClick = onResetBasic, modifier = Modifier.fillMaxWidth()) {
                    Text("기본 설정 초기화", color = Color(0xFFDC2626))
                }
                Text(
                    "모국·표시 정보를 처음부터 다시 설정합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp),
                )
                TextButton(onClick = onDeleteAllMaps, modifier = Modifier.fillMaxWidth()) {
                    Text("지도 데이터 전부 삭제", color = Color(0xFFDC2626))
                }
                Text(
                    "저장된 타일·명소·도보경로를 모두 지웁니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp),
                )
                TextButton(onClick = onMain, modifier = Modifier.fillMaxWidth()) {
                    Text("메인으로")
                }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("나가기")
                }
            }
        }
    }
}
