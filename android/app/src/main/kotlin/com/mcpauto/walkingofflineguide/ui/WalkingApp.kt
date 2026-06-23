package com.mcpauto.walkingofflineguide.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.mcpauto.walkingofflineguide.map.MapUiColors
import com.mcpauto.walkingofflineguide.data.CityPoint
import com.mcpauto.walkingofflineguide.data.CrashRecovery
import com.mcpauto.walkingofflineguide.data.defaultScheduleLeg
import com.mcpauto.walkingofflineguide.data.DownloadJobState
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.RegionPlayability
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.data.normalizeLegDates
import com.mcpauto.walkingofflineguide.data.tripEpochRangeFromLegs
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.data.isInTripPeriod
import com.mcpauto.walkingofflineguide.download.BatteryOptimizationHelper
import com.mcpauto.walkingofflineguide.download.DownloadEvent
import com.mcpauto.walkingofflineguide.download.DownloadSession
import com.mcpauto.walkingofflineguide.download.RegionDownloadForegroundService
import com.mcpauto.walkingofflineguide.download.DownloadProgress
import com.mcpauto.walkingofflineguide.download.DownloadProgressReader
import com.mcpauto.walkingofflineguide.download.HomeProgressiveDownloader
import com.mcpauto.walkingofflineguide.download.HomeRoutingBootstrap
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
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

private sealed class AppScreen {
    data object Loading : AppScreen()
    data object Hub : AppScreen()
    data object BasicSetup : AppScreen()
    data class TravelSetup(val resumeDownload: Boolean = false) : AppScreen()
    data class Map(val regionId: String) : AppScreen()
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
    var highlightId by remember { mutableStateOf<String?>(null) }
    var blinkHighlight by remember { mutableStateOf(false) }
    var showNeedTravelSetup by remember { mutableStateOf(false) }
    var policyNotice by remember { mutableStateOf<String?>(null) }
    var pendingDownloadJob by remember { mutableStateOf<DownloadJobState?>(null) }
    var showResetBasicConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var lastGps by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var bootOverrideHub by remember { mutableStateOf(CrashRecovery.shouldSafeStart(context)) }
    var recoveryBanner by remember { mutableStateOf<String?>(null) }
    var showLoadingActions by remember { mutableStateOf(false) }

    var downloadRunning by remember { mutableStateOf(DownloadSession.running.value) }
    LaunchedEffect(Unit) {
        DownloadSession.running.collect { downloadRunning = it }
    }

    LaunchedEffect(screen) {
        if (screen is AppScreen.Hub) {
            withContext(Dispatchers.IO) {
                val loadedRegions = store.loadRegions()
                val job = store.loadDownloadJob()
                regions = loadedRegions
                pendingDownloadJob = job
            }
        }
    }

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
                    AppScreen.Map(it.id)
                } ?: run {
                    showNeedTravelSetup = true
                    AppScreen.Hub
                }
            }
            MapAppPolicy.TRAVEL -> {
                MapPolicy.travelRegion(cfg, regs, gpsLat, gpsLon, hasGpsFix)?.let {
                    AppScreen.Map(it.id)
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
        showLoadingActions = false
        loadingMessage = "WiFi가 있으면 데이터 사용이 적습니다."
        loadingDetail = ""
        delay(500)
        showLoadingActions = true
        try {
            delay(400)
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
            val hasInternet = WifiGate.hasInternet(context)
            val gps = lastGps
            val hasGpsFix = gps != null
            if (!hasInternet && hasGpsFix && gps != null &&
                TripNavigation.isAtHomeCountry(config, gps.first, gps.second)
            ) {
                policyNotice = "WiFi/인터넷 없음 — 오프라인 모드로 시작합니다."
            }
            if (bootOverrideHub) {
                if (recoveryBanner == null && CrashRecovery.shouldSafeStart(context)) {
                    recoveryBanner = "이전 실행 중 오류가 있어 메인 메뉴로 시작합니다."
                }
                screen = AppScreen.Hub
            } else {
                screen = routeByPolicy(
                    config, regions, pendingDownloadJob, gps?.first, gps?.second, hasGpsFix, hasInternet,
                )
            }
            if (screen is AppScreen.Hub && hasGpsFix && gps != null &&
                TripNavigation.isAtHomeCountry(config, gps.first, gps.second)
            ) {
                highlightId = TripNavigation.resolveHomeMapRegion(config, regions, gps.first, gps.second)?.id
            }
            CrashRecovery.clear(context)
        } catch (_: Exception) {
            recoveryBanner = "시작 중 문제가 있어 메인 메뉴로 엽니다."
            screen = AppScreen.Hub
        }
    }

    Box(Modifier.fillMaxSize()) {
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

    fun openHubRegionMap(target: RegionRecord) {
        highlightId = target.id
        blinkHighlight = false
        scope.launch {
            store.saveRegion(target.copy(visited = true))
            regions = store.loadRegions()
        }
        screen = AppScreen.Map(target.id)
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
        AppScreen.Loading -> LoadingMenuShell(
            subtitle = loadingMessage,
            detail = loadingDetail,
            showActions = showLoadingActions,
            onMain = {
                bootOverrideHub = true
                screen = AppScreen.Hub
            },
            onOptions = { showOptions = true },
        )
        AppScreen.BasicSetup -> BasicSetupScreen(
            initial = config,
            store = store,
            locationHelper = locationHelper,
            geocoder = geocoder,
            onOpenOptions = { showOptions = true },
            onDone = { updated ->
                scope.launch {
                    config = updated
                    store.saveConfig(updated)
                    HomeRoutingBootstrap.prefetchIfOnline(context, updated)
                    screen = AppScreen.Hub
                }
            },
        )
        is AppScreen.TravelSetup -> TravelSetupScreen(
            initial = config,
            resumeJob = if (s.resumeDownload) pendingDownloadJob else null,
            store = store,
            geocoder = geocoder,
            onOpenOptions = { showOptions = true },
            onDone = { updated, newRegions, firstRegion ->
                config = updated
                regions = newRegions
                pendingDownloadJob = null
                if (firstRegion != null) {
                    screen = AppScreen.Map(firstRegion.id)
                } else {
                    screen = AppScreen.Hub
                }
            },
            onCancel = { screen = AppScreen.Hub },
            onGoMain = { screen = AppScreen.Hub },
            onJobUpdated = { pendingDownloadJob = it },
            onRegionImported = { imported ->
                scope.launch {
                    regions = store.loadRegions()
                    pendingDownloadJob = store.loadDownloadJob()
                    highlightId = imported.id
                }
            },
        )
        AppScreen.Hub -> HubScreen(
            config = config,
            regions = regions,
            pendingDownloadJob = pendingDownloadJob,
            downloadRunning = downloadRunning,
            highlightId = highlightId,
            blinkHighlight = blinkHighlight,
            recoveryBanner = recoveryBanner,
            listState = listState,
            onRegionMapTap = { r -> openHubRegionMap(r) },
            onRegionSelect = { r -> openHubRegionMap(r) },
            onSchedule = {
                screen = if (config.basicSetupComplete) {
                    AppScreen.TravelSetup(resumeDownload = pendingDownloadJob?.active == true)
                } else {
                    AppScreen.BasicSetup
                }
            },
            onResumeDownload = {
                screen = AppScreen.TravelSetup(resumeDownload = true)
            },
            onDeleteCity = { regionId ->
                scope.launch {
                    if (downloadRunning) {
                        RegionDownloadForegroundService.cancel(context)
                        delay(400)
                    }
                    withContext(Dispatchers.IO) { store.deleteRegion(regionId) }
                    regions = store.loadRegions()
                    pendingDownloadJob = store.loadDownloadJob()
                    if (highlightId == regionId) highlightId = null
                }
            },
            onRedownloadCity = { regionId ->
                scope.launch {
                    if (downloadRunning) {
                        RegionDownloadForegroundService.cancel(context)
                        delay(400)
                    }
                    withContext(Dispatchers.IO) { store.deleteRegion(regionId) }
                    regions = store.loadRegions()
                    pendingDownloadJob = store.loadDownloadJob()
                    if (highlightId == regionId) highlightId = null
                    screen = AppScreen.TravelSetup(resumeDownload = true)
                }
            },
            onCurrentRegion = {
                scope.launch {
                    val gps = runCatching { locationHelper.acquirePosition() }.getOrNull()
                    if (gps != null) {
                        lastGps = gps.lat to gps.lon
                    }
                    val fix = lastGps ?: return@launch
                    val hasNet = WifiGate.hasInternet(context)
                    when {
                        TripNavigation.isAtHomeCountry(config, fix.first, fix.second) -> {
                            if (!hasNet) return@launch
                            val region = MapPolicy.homeLiveRegion(config, regions, fix.first, fix.second)
                                ?: MapPolicy.onlineHomeStub(config, fix.first, fix.second)
                            screen = AppScreen.Map(region.id)
                        }
                        else -> {
                            val region = MapPolicy.travelRegion(
                                config, regions, fix.first, fix.second, hasGpsFix = true,
                            ) ?: regions.filter { it.downloadComplete }
                                .filter { !TripNavigation.isHomeRegion(config, it) }
                                .minByOrNull {
                                    com.mcpauto.walkingofflineguide.logic.PoiLogic.haversineM(
                                        fix.first, fix.second, it.lat, it.lon,
                                    )
                                }
                            region?.let { screen = AppScreen.Map(it.id) }
                        }
                    }
                }
            },
            showCurrentRegion = config.basicSetupComplete &&
                (
                    config.homeCountry.isNotBlank() ||
                        regions.any { r ->
                            !TripNavigation.isHomeRegion(config, r) &&
                                (
                                    r.downloadComplete ||
                                        RegionPlayability.hasPartialLocalContent(
                                            RegionPlayability.regionDir(
                                                File(context.filesDir, "walking_data"),
                                                r.id,
                                            ),
                                        )
                                    )
                        }
                    ),
            onOptions = { showOptions = true },
            onExit = { showExitConfirm = true },
            store = store,
            onRegionImported = { imported ->
                scope.launch {
                    regions = store.loadRegions()
                    pendingDownloadJob = store.loadDownloadJob()
                    highlightId = imported.id
                }
            },
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
                    locationHelper = locationHelper,
                    onOpenOptions = { showOptions = true },
                    onMainHub = { screen = AppScreen.Hub },
                    onPolicyFallback = { reason -> rerouteFromMap(reason) },
                )
            }
        }
    }

    if (showOptions) {
        Box(Modifier.fillMaxSize().zIndex(50f)) {
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
                onResetBasic = { showResetBasicConfirm = true },
                onExit = { (context as? Activity)?.finish() },
                onMain = {
                    showOptions = false
                    screen = AppScreen.Hub
                },
            )
        }
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

    StatusBarScrim(Modifier.align(Alignment.TopCenter).zIndex(999f))
    }
}

private data class HubRow(
    val key: String,
    val countryLabel: String,
    val cityName: String,
    val subtitle: String,
    val isDownloading: Boolean,
    val activeDownload: Boolean,
    val region: RegionRecord?,
    val visited: Boolean,
    val mapPlayable: Boolean = false,
)

@Composable
private fun HubCityCard(
    row: HubRow,
    selected: Boolean,
    onTap: () -> Unit,
    onHold1s: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val bg = when {
        selected -> Color(0xFFDCE8CC)
        row.isDownloading && row.activeDownload -> Color(0xFFFFF0D6)
        row.isDownloading -> Color(0xFFF5F2E8)
        else -> Color(0xFFF7FAF2)
    }
    val borderColor = when {
        selected -> Color(0xFF8FAF72)
        row.isDownloading && row.activeDownload -> Color(0xFFE8B86D)
        row.isDownloading -> Color(0xFFC8D4BC)
        else -> AppMenuStyle.scrollBorder
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(bg, RoundedCornerShape(10.dp))
            .pointerInput(row.key) {
                detectTapGestures(
                    onPress = {
                        var held = false
                        val job = scope.launch {
                            delay(1000L)
                            held = true
                            onHold1s()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        if (!held) onTap()
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            "${row.countryLabel.ifBlank { "—" }} · ${row.cityName}",
            color = when {
                row.isDownloading && row.activeDownload -> Color(0xFFEA580C)
                row.visited -> Color(0xFF15803D)
                else -> AppMenuStyle.text
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (row.isDownloading) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            row.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (row.isDownloading) Color(0xFF64748B) else AppMenuStyle.muted,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun HubCityActionSheet(
    row: HubRow,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onOpenMap: () -> Unit,
    onResumeDownload: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppMenuStyle.card, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
    ) {
        Text(
            row.cityName,
            fontWeight = FontWeight.SemiBold,
            color = AppMenuStyle.text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        if ((!row.isDownloading && row.region != null) || (row.isDownloading && row.mapPlayable)) {
            HubActionRow("지도 열기", AppMenuStyle.accent, onOpenMap)
        }
        if (row.isDownloading) {
            HubActionRow(
                if (row.activeDownload) "다운로드 화면으로" else "다운로드 이어받기",
                AppMenuStyle.accent,
                onResumeDownload,
            )
            if (row.region != null) {
                HubActionRow("처음부터 다시 받기", Color(0xFF0F766E), onRedownload)
            }
        }
        if (row.region != null) {
            HubActionRow("오프라인 데이터 삭제", AppMenuStyle.danger, onDelete)
        }
        HubActionRow("닫기", AppMenuStyle.muted, onDismiss)
    }
}

@Composable
private fun HubActionRow(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun isHomeHubRegion(region: RegionRecord, config: TripConfig): Boolean {
    if (region.id == HomeProgressiveDownloader.REGION_ID) return true
    val home = config.homeCountry.trim()
    if (home.isBlank()) return false
    val label = region.countryLabel.trim()
    return label.equals(home, ignoreCase = true) ||
        label.contains(home, ignoreCase = true) ||
        home.contains(label, ignoreCase = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubScreen(
    config: TripConfig,
    regions: List<RegionRecord>,
    pendingDownloadJob: DownloadJobState?,
    downloadRunning: Boolean,
    highlightId: String?,
    blinkHighlight: Boolean,
    recoveryBanner: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRegionMapTap: (RegionRecord) -> Unit,
    onRegionSelect: (RegionRecord) -> Unit,
    onSchedule: () -> Unit,
    onResumeDownload: () -> Unit,
    onDeleteCity: (regionId: String) -> Unit,
    onRedownloadCity: (regionId: String) -> Unit,
    onCurrentRegion: () -> Unit,
    showCurrentRegion: Boolean,
    onOptions: () -> Unit,
    onExit: () -> Unit,
    store: TripStore,
    onRegionImported: (RegionRecord) -> Unit,
) {
    val context = LocalContext.current
    var importError by remember { mutableStateOf("") }
    var importOk by remember { mutableStateOf("") }
    val completed = regions.filter { it.downloadComplete && !isHomeHubRegion(it, config) }
    val partialRegions = regions.filter {
        !it.downloadComplete && it.cityName.isNotBlank() && !isHomeHubRegion(it, config)
    }
    var downloadHints by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(pendingDownloadJob, downloadRunning, regions) {
        val job = pendingDownloadJob ?: return@LaunchedEffect
        if (!job.active) return@LaunchedEffect
        val hints = mutableMapOf<String, String>()
        job.stops.filter { it.name !in job.finishedCityNames.toSet() }.forEach { stop ->
            hints[stop.name] = withContext(Dispatchers.IO) {
                DownloadProgressReader.tileProgressLine(context, job)
            } ?: if (downloadRunning) "다운로드 진행 중…" else "다운로드 일시 중지"
        }
        downloadHints = hints
    }

    val walkingDataRoot = remember { File(context.filesDir, "walking_data") }

    fun regionPlayable(region: RegionRecord?): Boolean {
        if (region == null) return false
        if (region.downloadComplete) return true
        return RegionPlayability.hasPartialLocalContent(RegionPlayability.regionDir(walkingDataRoot, region.id))
    }

    val downloadRows = remember(pendingDownloadJob, partialRegions, downloadHints, downloadRunning, regions) {
        val rows = mutableListOf<HubRow>()
        val seen = mutableSetOf<String>()
        val job = pendingDownloadJob
        if (job?.active == true) {
            job.stops.filter { it.name !in job.finishedCityNames.toSet() }.forEach { stop ->
                seen += stop.name
                val partial = partialRegions.find { pr ->
                    pr.cityName.equals(stop.name, ignoreCase = true) ||
                        pr.cityName.contains(stop.name, ignoreCase = true) ||
                        stop.name.contains(pr.cityName, ignoreCase = true)
                }
                val playable = regionPlayable(partial)
                val hint = downloadHints[stop.name]
                val subtitle = when {
                    playable && downloadRunning && !hint.isNullOrBlank() ->
                        "일부 받음 · 탭하여 지도 · $hint"
                    playable && downloadRunning -> "일부 받음 · 탭하여 지도 열기"
                    playable && !hint.isNullOrBlank() ->
                        "일부 받음 · 탭하여 지도 · $hint · 이어받기 가능"
                    playable -> "일부 받음 · 탭하여 지도 · 이어받기 가능"
                    downloadRunning && !hint.isNullOrBlank() -> "다운로드 중 · $hint"
                    downloadRunning -> "다운로드 중 · WiFi 유지"
                    !hint.isNullOrBlank() -> "일시 중지 · $hint · 탭하여 이어받기"
                    else -> "일시 중지 · 탭하여 이어받기"
                }
                rows += HubRow(
                    key = "dl:${stop.name}",
                    countryLabel = job.countryLabel.ifBlank { job.destinationCountry },
                    cityName = stop.name,
                    subtitle = subtitle,
                    isDownloading = true,
                    activeDownload = downloadRunning,
                    region = partial,
                    visited = false,
                    mapPlayable = playable,
                )
            }
        }
        partialRegions.filter { pr -> pr.cityName !in seen }.forEach { pr ->
            val playable = regionPlayable(pr)
            rows += HubRow(
                key = "partial:${pr.id}",
                countryLabel = pr.countryLabel,
                cityName = pr.cityName,
                subtitle = when {
                    playable && downloadRunning -> "일부 받음 · 탭하여 지도 · 다운로드 중"
                    playable -> "일부 받음 · 탭하여 지도 · 이어받기 가능"
                    downloadRunning -> "다운로드 중 · 미완료 지역"
                    else -> "미완료 · 탭하여 이어받기"
                },
                isDownloading = true,
                activeDownload = downloadRunning,
                region = pr,
                visited = false,
                mapPlayable = playable,
            )
        }
        rows
    }

    val readyRows = completed.sortedWith(
        compareBy<RegionRecord> { if (it.visited) 0 else 1 }.thenBy { it.cityName },
    ).map { r ->
        HubRow(
            key = "ready:${r.id}",
            countryLabel = r.countryLabel,
            cityName = r.cityName,
            subtitle = if (r.visited) "방문 완료 · 오프라인 지도" else "오프라인 지도 · 탭하여 열기",
            isDownloading = false,
            activeDownload = false,
            region = r,
            visited = r.visited,
        )
    }

    val hubRows = downloadRows + readyRows
    val mapPins = remember(regions, partialRegions, completed) {
        (partialRegions + completed).distinctBy { it.id }.mapNotNull { r ->
            val dir = RegionPlayability.regionDir(walkingDataRoot, r.id)
            RegionPlayability.pinState(r, dir)?.let { WorldMapPin(r, it) }
        }
    }
    var menuRow by remember { mutableStateOf<HubRow?>(null) }
    var confirmDeleteRow by remember { mutableStateOf<HubRow?>(null) }

    if (confirmDeleteRow != null) {
        val row = confirmDeleteRow!!
        AlertDialog(
            onDismissRequest = { confirmDeleteRow = null },
            title = { Text("오프라인 데이터 삭제") },
            text = {
                Text(
                    "${row.cityName}의 지도·명소 데이터를 삭제합니다.\n" +
                        if (row.isDownloading) "다운로드는 중단됩니다." else "목록에서 제거됩니다.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        row.region?.id?.let { onDeleteCity(it) }
                        confirmDeleteRow = null
                        menuRow = null
                    },
                ) { Text("삭제", color = AppMenuStyle.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteRow = null }) { Text("취소") }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(AppMenuStyle.bg)) {
        HubTopBar(onOptions)
        recoveryBanner?.let {
            MenuWarnBanner(it, Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            WorldMapCanvas(
                pins = mapPins,
                highlightId = highlightId,
                blinkHighlight = blinkHighlight,
                onRegionTap = onRegionMapTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, AppMenuStyle.panelBorder, RoundedCornerShape(12.dp))
                    .background(AppMenuStyle.card, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    "도시 목록 (${hubRows.size})",
                    fontWeight = FontWeight.SemiBold,
                    color = AppMenuStyle.text,
                )
                Text(
                    if (downloadRows.isNotEmpty()) {
                        "탭 — 지도 열기 · 1초 길게 누르기 — 옵션"
                    } else {
                        "탭 — 지도 · 1초 길게 누르기 — 옵션"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = AppMenuStyle.muted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                MenuScrollSurface(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        if (hubRows.isEmpty()) {
                            item {
                                Text(
                                    "다운로드한 도시가 없습니다.\n여행 설정에서 지도·명소를 받을 수 있습니다.",
                                    color = AppMenuStyle.muted,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        } else {
                            itemsIndexed(hubRows, key = { _, row -> row.key }) { _, row ->
                                HubCityCard(
                                    row = row,
                                    selected = row.region?.id == highlightId,
                                    onTap = {
                                        menuRow = null
                                        when {
                                            row.region != null && (row.mapPlayable || row.region.downloadComplete) ->
                                                onRegionSelect(row.region)
                                            row.isDownloading -> onResumeDownload()
                                        }
                                    },
                                    onHold1s = { menuRow = row },
                                )
                            }
                        }
                    }
                    menuRow?.let { row ->
                        Box(
                            Modifier
                                .matchParentSize()
                                .zIndex(1f)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { menuRow = null }
                                .background(Color(0x40000000)),
                        )
                        HubCityActionSheet(
                            row = row,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .zIndex(2f)
                                .padding(horizontal = 28.dp),
                            onDismiss = { menuRow = null },
                            onOpenMap = {
                                menuRow = null
                                row.region?.let { onRegionSelect(it) }
                            },
                            onResumeDownload = {
                                menuRow = null
                                onResumeDownload()
                            },
                            onRedownload = {
                                menuRow = null
                                row.region?.id?.let { onRedownloadCity(it) }
                            },
                            onDelete = {
                                menuRow = null
                                confirmDeleteRow = row
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, AppMenuStyle.panelBorder, RoundedCornerShape(12.dp))
                    .background(AppMenuStyle.card, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                MenuPrimaryButton("여행 설정", onSchedule)
                RegionImportButton(
                    store = store,
                    enabled = !downloadRunning,
                    modifier = Modifier.padding(top = 6.dp),
                    onImported = { r ->
                        importOk = ""
                        importError = ""
                        onRegionImported(r)
                    },
                    onError = { importError = it; importOk = "" },
                    onSuccessMessage = { importOk = it; importError = "" },
                )
                if (importOk.isNotBlank()) {
                    Text(importOk, color = Color(0xFF15803D), style = MaterialTheme.typography.labelSmall)
                }
                if (importError.isNotBlank()) {
                    Text(importError, color = AppMenuStyle.danger, style = MaterialTheme.typography.labelSmall)
                }
                if (showCurrentRegion) {
                    MenuPurpleButton("현재 지역 보기", onCurrentRegion, Modifier.padding(top = 6.dp))
                }
                MenuSecondaryButton("나가기", onExit, Modifier.padding(top = 6.dp))
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
    onOpenOptions: () -> Unit,
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

    AppMenuScaffold(
        title = "기본 설정",
        onOptions = onOpenOptions,
        content = { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "처음 한 번만 설정합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppMenuStyle.muted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                MenuCard {
                    CountryAutocompleteField(
                        homeCountry,
                        homeCountryCode,
                        { homeCountry = it },
                        { c -> homeCountryCode = c?.code },
                        catalog,
                        "모국",
                    )
                }
                Spacer(Modifier.height(10.dp))
                MenuCard {
                    Text("표시 정보", fontWeight = FontWeight.SemiBold, color = AppMenuStyle.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(showHotel, { showHotel = !showHotel }, { Text("숙소") })
                        FilterChip(showRestaurant, { showRestaurant = !showRestaurant }, { Text("음식점") })
                        FilterChip(showSight, { showSight = !showSight }, { Text("명소") })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Checkbox(autoDelete, { autoDelete = it })
                        Text("여행 후 자동 삭제", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(16.dp))
                MenuPrimaryButton(
                    text = "저장",
                    enabled = homeCountry.isNotBlank(),
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun TravelSetupScreen(
    initial: TripConfig,
    resumeJob: DownloadJobState?,
    store: TripStore,
    geocoder: NominatimGeocoder,
    onOpenOptions: () -> Unit,
    onDone: (TripConfig, List<RegionRecord>, RegionRecord?) -> Unit,
    onCancel: () -> Unit,
    onGoMain: () -> Unit,
    onJobUpdated: (DownloadJobState?) -> Unit,
    onRegionImported: (RegionRecord) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val catalog = remember { GeoCatalog(context) }

    var step by remember { mutableIntStateOf(if (resumeJob != null) 2 else 1) }
    val isFreshSetup = resumeJob == null && initial.legs.isEmpty() && initial.arrivalAirport.name.isBlank()
    var destCountry by remember(resumeJob, initial) {
        mutableStateOf(
            when {
                initial.destinationCountry.isNotBlank() -> initial.destinationCountry
                isFreshSetup -> "이탈리아"
                else -> resumeJob?.destinationCountry?.ifBlank { resumeJob.countryLabel }.orEmpty()
            },
        )
    }
    var arrivalAirport by remember(resumeJob, initial) {
        mutableStateOf(
            when {
                initial.arrivalAirport.confirmed && initial.arrivalAirport.name.isNotBlank() -> initial.arrivalAirport
                isFreshSetup -> CityPoint(
                    name = "Venice Marco Polo Airport",
                    lat = 45.5053,
                    lon = 12.3519,
                    confirmed = true,
                )
                else -> CityPoint()
            },
        )
    }
    var tripPeriodStart by remember(resumeJob, initial) {
        mutableLongStateOf(
            when {
                resumeJob != null && resumeJob.tripStartEpochDay > 0 -> resumeJob.tripStartEpochDay
                initial.tripStartEpochDay > 0 -> initial.tripStartEpochDay
                isFreshSetup -> LocalDate.of(2026, 6, 25).toEpochDay()
                else -> LocalDate.now().toEpochDay()
            },
        )
    }
    var tripPeriodEnd by remember(resumeJob, initial) {
        mutableLongStateOf(
            when {
                resumeJob != null && resumeJob.tripEndEpochDay > 0 -> resumeJob.tripEndEpochDay
                initial.tripEndEpochDay > 0 -> initial.tripEndEpochDay
                isFreshSetup -> LocalDate.of(2026, 7, 14).toEpochDay()
                initial.tripStartEpochDay > 0 -> initial.tripStartEpochDay + 7
                else -> LocalDate.now().toEpochDay() + 7
            },
        )
    }
    var skipHub by remember { mutableStateOf(resumeJob?.skipHubMenu ?: initial.skipHubMenu) }
    var scheduleLocked by remember { mutableStateOf(resumeJob != null) }
    var destCountryCode by remember(resumeJob) { mutableStateOf<String?>(null) }
    val homeCountryCode = initial.homeCountryCode.ifBlank { catalog.resolveCountry(initial.homeCountry)?.code ?: "KR" }
    val homeEntry = remember(initial.homeCountry, homeCountryCode) {
        catalog.resolveCountry(initial.homeCountry)
            ?: catalog.resolveCountryByCode(homeCountryCode)
            ?: catalog.allCountries().firstOrNull { it.code.equals(homeCountryCode, ignoreCase = true) }
    }
    val travelCountryPlaceholder = remember(homeEntry, initial.homeCountry) {
        homeEntry?.nameKo?.takeIf { it.isNotBlank() }
            ?: homeEntry?.nameEn?.takeIf { it.isNotBlank() }
            ?: initial.homeCountry.takeIf { it.isNotBlank() }
            ?: "모국"
    }
    val travelCityPlaceholder = remember(homeEntry) {
        homeEntry?.capital?.takeIf { it.isNotBlank() } ?: "수도"
    }

    val legs = remember(resumeJob, initial) {
        mutableStateListOf<ScheduleLeg>().also { list ->
            val raw = when {
                resumeJob?.legs?.isNotEmpty() == true ->
                    resumeJob.legs.map { it.copy(legConfirmed = true) }
                initial.legs.isNotEmpty() ->
                    initial.legs.map { it.copy(legConfirmed = false) }
                else -> emptyList()
            }
            list.addAll(normalizeLegDates(raw, tripPeriodStart, tripPeriodEnd))
        }
    }
    var downloading by remember { mutableStateOf(DownloadSession.running.value) }
    var downloadProgress by remember { mutableStateOf(DownloadSession.progress.value) }
    var error by remember { mutableStateOf("") }
    var legToDelete by remember { mutableStateOf<Int?>(null) }
    var resumeStarted by remember { mutableStateOf(false) }
    var showBatteryOptDialog by remember { mutableStateOf(false) }
    var pendingDownloadSeed by remember { mutableStateOf<DownloadJobState?>(null) }
    var autoResumeCount by remember { mutableIntStateOf(0) }
    var diskTileHint by remember { mutableStateOf<String?>(null) }
    var importOk by remember { mutableStateOf("") }

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
        val normalized = normalizeLegDates(legs.toList(), tripPeriodStart, tripPeriodEnd)
        val stops = buildStopsFromLegs(normalized)
        if (stops.isEmpty()) return null
        val (fromLegsStart, fromLegsEnd) = tripEpochRangeFromLegs(normalized)
        val startDay = tripPeriodStart.takeIf { it > 0 } ?: fromLegsStart
        val endDay = tripPeriodEnd.takeIf { it > 0 } ?: fromLegsEnd
        return DownloadJobState(
            active = true,
            countryLabel = destCountry,
            homeCountryCode = homeCountryCode,
            destinationCountry = destCountry,
            tripStartEpochDay = startDay,
            tripEndEpochDay = endDay,
            legs = normalized,
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
        scope.launch {
            val dl = RegionDownloadManager(context, store)
            if (jobSeed == null && dl.allStopsHaveLocalMaps(job.stops)) {
                error = "이미 저장된 지도가 있습니다. 다운로드를 시작하지 않습니다."
                return@launch
            }
            val normalized = normalizeLegDates(legs.toList(), tripPeriodStart, tripPeriodEnd)
            store.saveConfig(
                store.loadConfig().copy(
                    destinationCountry = destCountry,
                    arrivalAirport = arrivalAirport,
                    tripStartEpochDay = tripPeriodStart,
                    tripEndEpochDay = tripPeriodEnd,
                    legs = normalized,
                    skipHubMenu = skipHub,
                ),
            )
            store.saveDownloadJob(job)
            onJobUpdated(job)
            RegionDownloadForegroundService.start(context, job)
        }
    }

    /** 이어받기(jobSeed 있음)는 배터리 팝업 없이 즉시 시작 */
    fun requestDownload(jobSeed: DownloadJobState? = null) {
        if (BatteryOptimizationHelper.isExempt(context) || jobSeed != null) {
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
                launchDownloadService(resumeJob)
            }
        }
    }

    LaunchedEffect(step, downloading, resumeJob) {
        if (step != 2) return@LaunchedEffect
        while (true) {
            delay(12_000)
            if (downloading || DownloadSession.running.value) continue
            val job = withContext(Dispatchers.IO) { store.loadDownloadJob() } ?: continue
            if (!job.active) continue
            if (autoResumeCount >= 6) continue
            autoResumeCount++
            diskTileHint = withContext(Dispatchers.IO) {
                DownloadProgressReader.tileProgressLine(context, job)
            }
            launchDownloadService(job)
        }
    }

    LaunchedEffect(step, downloading, resumeJob) {
        if (step != 2 || downloading) return@LaunchedEffect
        val job = resumeJob ?: withContext(Dispatchers.IO) { store.loadDownloadJob() } ?: return@LaunchedEffect
        if (job.active) {
            diskTileHint = withContext(Dispatchers.IO) {
                DownloadProgressReader.tileProgressLine(context, job)
            }
        }
    }

    AppMenuScaffold(
        title = "여행 설정 $step/2",
        onBack = when {
            downloading -> null
            step == 2 -> {
                {
                    step = 1
                    scheduleLocked = false
                    error = ""
                }
            }
            else -> onCancel
        },
        onOptions = onOpenOptions,
        content = { padding ->
            if (step == 1) {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    TripOverviewPanel(
                        arrivalAirport = arrivalAirport,
                        onAirportChange = { p ->
                            arrivalAirport = p
                            scheduleLocked = false
                            scope.launch {
                                runCatching { geocoder.reverse(p.lat, p.lon) }.getOrNull()?.let { geo ->
                                    val hint = geo.adminParts.lastOrNull()
                                        ?: geo.displayName.split(",").lastOrNull()?.trim().orEmpty()
                                    catalog.resolveCountry(hint)?.nameKo?.takeIf { it.isNotBlank() }?.let {
                                        destCountry = it
                                    }
                                }
                            }
                        },
                        tripStartEpochDay = tripPeriodStart,
                        tripEndEpochDay = tripPeriodEnd,
                        onTripStartChange = {
                            tripPeriodStart = it
                            scheduleLocked = false
                        },
                        onTripEndChange = {
                            tripPeriodEnd = it
                            scheduleLocked = false
                        },
                        catalog = catalog,
                        geocoder = geocoder,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    ScheduleLegsVerticalList(
                        legs = legs,
                        tripYear = tripYearFromEpoch(tripPeriodStart),
                        tripStartBound = tripPeriodStart,
                        tripEndBound = tripPeriodEnd,
                        destCountry = destCountry,
                        arrivalAirport = arrivalAirport,
                        homeCountryCode = homeCountryCode,
                        catalog = catalog,
                        geocoder = geocoder,
                        onLegChange = { idx, updated ->
                            legs[idx] = updated
                            scheduleLocked = false
                        },
                        onAddLeg = {
                            legs.add(
                                defaultScheduleLeg(store.newLegId()).copy(
                                    startEpochDay = tripPeriodStart,
                                    endEpochDay = tripPeriodEnd,
                                ),
                            )
                            scheduleLocked = false
                        },
                        onDeleteLeg = { idx -> legToDelete = idx },
                        modifier = Modifier.weight(1f),
                        cityPlaceholder = travelCityPlaceholder,
                    )
                    val airportReady = arrivalAirport.confirmed && arrivalAirport.name.isNotBlank()
                    val tripDatesReady = tripPeriodStart > 0 && tripPeriodEnd >= tripPeriodStart
                    val allLegsReady = legs.isNotEmpty() && legs.all { isLegReady(it) && it.legConfirmed }
                    MenuPrimaryButton(
                        text = "일정 전체 확정 → 다음",
                        enabled = airportReady && tripDatesReady && allLegsReady,
                        onClick = {
                            if (!airportReady) {
                                error = "입국 공항을 확인해 주세요."
                                return@MenuPrimaryButton
                            }
                            if (!tripDatesReady) {
                                error = "여행 기간(출국·귀국일)을 선택해 주세요."
                                return@MenuPrimaryButton
                            }
                            if (legs.isEmpty()) {
                                error = "「지역 추가」로 하이킹 지역을 하나 이상 넣어 주세요."
                                return@MenuPrimaryButton
                            }
                            if (!allLegsReady) {
                                error = "모든 지역에서 출발·도착 확인 후 「이 지역 확정」을 눌러 주세요."
                                return@MenuPrimaryButton
                            }
                            scheduleLocked = true
                            error = ""
                            step = 2
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = AppMenuStyle.danger, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    if (resumeJob != null) {
                        Text(
                            "이전 다운로드 이어받기 (${resumeJob.finishedCityNames.size}/${resumeJob.stops.size.coerceAtLeast(1)} 완료)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2563EB),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    if (!downloading && resumeJob?.active == true) {
                        Text(
                            buildString {
                                append("다운로드가 잠시 멈춘 상태입니다. 자동으로 이어받기 시도 중…")
                                diskTileHint?.let { append("\n($it)") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEA580C),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Text(
                        scheduleSummary(legs, destCountry),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    val dl = remember(legs, scheduleLocked) { RegionDownloadManager(context, store) }
                    val stops = remember(legs, scheduleLocked) {
                        buildStopsFromLegs(normalizeLegDates(legs.toList(), tripPeriodStart, tripPeriodEnd))
                    }
                    var localMapsReady by remember(stops) { mutableStateOf(false) }
                    LaunchedEffect(stops) {
                        localMapsReady = dl.allStopsHaveLocalMaps(stops)
                    }
                    val estBytes = dl.estimateBytes(stops)
                    val estMin = dl.estimateDurationMinutes(stops)
                    Text(
                        if (localMapsReady && resumeJob == null) {
                            "저장된 지도가 있어 새 다운로드는 건너뜁니다."
                        } else {
                            "경로 직선 구간 + 반경 ${com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM.toInt()}km · 예상 ${RegionDownloadManager.formatSize(estBytes)} · 약 ${estMin}분"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (localMapsReady && resumeJob == null) Color(0xFF15803D) else Color(0xFF64748B),
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

                    Button(
                        onClick = {
                            if (downloading) {
                                RegionDownloadForegroundService.cancel(context)
                                return@Button
                            }
                            requestDownload(resumeJob)
                        },
                        enabled = scheduleLocked && (downloading || resumeJob != null || !localMapsReady),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                downloading -> "다운로드 중단"
                                resumeJob != null -> "이어받기"
                                localMapsReady -> "지도 있음 (다운로드 생략)"
                                else -> "다운로드 시작"
                            },
                        )
                    }

                    if (!BatteryOptimizationHelper.isExempt(context) && !downloading) {
                        Text(
                            "다운로드 전 「배터리 최적화 제외」 허용을 권장합니다 (화면 꺼짐·Doze 대비).",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2563EB),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    RegionImportButton(
                        store = store,
                        enabled = !downloading,
                        modifier = Modifier.padding(top = 12.dp),
                        onImported = { r ->
                            importOk = ""
                            error = ""
                            onRegionImported(r)
                        },
                        onError = { importOk = ""; error = it },
                        onSuccessMessage = { importOk = it; error = "" },
                    )
                    if (importOk.isNotBlank()) {
                        Text(importOk, color = Color(0xFF15803D), modifier = Modifier.padding(top = 4.dp))
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (skipHub) Color(0xFF6366F1) else AppMenuStyle.scrollBorder,
                                RoundedCornerShape(8.dp),
                            )
                            .background(if (skipHub) Color(0xFFEDE9FE) else AppMenuStyle.scroll.copy(alpha = 0.35f))
                            .clickable { skipHub = !skipHub }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = skipHub,
                            onCheckedChange = { skipHub = it },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                        ) {
                            Text(
                                "여행기간동안 설정 다시 보지 않기",
                                fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.8f,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 0.8f,
                                fontWeight = if (skipHub) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                "(앱 실행 시 메뉴 없이 바로 지도)",
                                fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.75f,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 0.75f,
                                color = AppMenuStyle.muted,
                            )
                        }
                    }

                    OutlinedButton(onClick = onGoMain, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("메인으로 가기")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("취소")
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = AppMenuStyle.danger, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        },
    )

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
            title = { Text("지역 삭제") },
            text = { Text("지역 ${idx + 1}을(를) 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    if (idx in legs.indices) {
                        legs.removeAt(idx)
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
    onResetBasic: () -> Unit,
    onExit: () -> Unit,
    onMain: () -> Unit,
) {
    var showHotel by remember { mutableStateOf(config.showHotel) }
    var showRestaurant by remember { mutableStateOf(config.showRestaurant) }
    var showSight by remember { mutableStateOf(config.showSight) }
    var autoDelete by remember { mutableStateOf(config.autoDeleteAfterTrip) }
    var manualDelete by remember { mutableStateOf(config.manualDeletePrompt) }

    AppMenuScaffold(
        title = "옵션",
        onBack = onDismiss,
        actionLabel = "저장",
        onAction = {
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
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MenuCard {
                Text("표시", fontWeight = FontWeight.SemiBold, color = AppMenuStyle.text)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(showHotel, { showHotel = !showHotel }, { Text("숙소") })
                    FilterChip(showRestaurant, { showRestaurant = !showRestaurant }, { Text("음식점") })
                    FilterChip(showSight, { showSight = !showSight }, { Text("명소") })
                }
            }
            MenuCard {
                Text("지도 저장", fontWeight = FontWeight.SemiBold, color = AppMenuStyle.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(autoDelete, { autoDelete = it })
                    Text("여행 후 자동 삭제", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(manualDelete, { manualDelete = it })
                    Text("삭제 안내", style = MaterialTheme.typography.bodySmall)
                }
            }
            MenuCard {
                TextButton(onClick = onResetBasic, modifier = Modifier.fillMaxWidth()) {
                    Text("기본 설정 초기화", color = AppMenuStyle.danger)
                }
                TextButton(onClick = onMain, modifier = Modifier.fillMaxWidth()) {
                    Text("메인으로", color = AppMenuStyle.accent)
                }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Text("앱 종료", color = AppMenuStyle.muted)
                }
            }
        }
    }
}
