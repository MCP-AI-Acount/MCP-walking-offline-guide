package com.mcpauto.walkingofflineguide.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import java.util.Locale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mcpauto.walkingofflineguide.data.Bbox
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.Poi
import com.mcpauto.walkingofflineguide.data.PoiBundle
import com.mcpauto.walkingofflineguide.data.PoiRepository
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.RoutingGraph
import com.mcpauto.walkingofflineguide.data.TileStore
import com.mcpauto.walkingofflineguide.data.TripConfig
import com.mcpauto.walkingofflineguide.data.UserPosition
import com.mcpauto.walkingofflineguide.logic.LocalizedPoi
import com.mcpauto.walkingofflineguide.logic.LocationHelper
import com.mcpauto.walkingofflineguide.logic.MapCamera
import com.mcpauto.walkingofflineguide.logic.MapCameraMath
import com.mcpauto.walkingofflineguide.logic.MapMath
import com.mcpauto.walkingofflineguide.logic.MapPolicy
import com.mcpauto.walkingofflineguide.logic.NavigationAnchor
import com.mcpauto.walkingofflineguide.logic.NavigationBearingProvider
import com.mcpauto.walkingofflineguide.logic.PoiLocalization
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import com.mcpauto.walkingofflineguide.logic.SpeechHelper
import com.mcpauto.walkingofflineguide.logic.TripNavigation
import com.mcpauto.walkingofflineguide.map.MapUiColors
import com.mcpauto.walkingofflineguide.map.PoiColors
import com.mcpauto.walkingofflineguide.download.HomeProgressiveDownloader
import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.OverpassClient
import com.mcpauto.walkingofflineguide.network.adminCityLabel
import com.mcpauto.walkingofflineguide.network.WifiGate
import com.mcpauto.walkingofflineguide.network.OnlineTileProvider
import com.mcpauto.walkingofflineguide.util.HomeLanguage
import com.mcpauto.walkingofflineguide.util.MapUiStrings
import com.mcpauto.walkingofflineguide.util.countryFlagEmoji
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun effectiveRegionBbox(bundle: PoiBundle, region: RegionRecord): Bbox =
    bundle.bbox.takeIf { it.north > it.south } ?: region.bbox

/** 지도·우측 패널 상단 — 상태바 직하, 여백 최소 */
private fun Modifier.mapChromeTop(): Modifier =
    statusBarsPadding().padding(top = 1.dp)

private fun matchesKindFilter(
    p: Poi,
    showRestaurant: Boolean,
    showHotel: Boolean,
    showSightseeing: Boolean,
): Boolean = when (p.kind) {
    "restaurant", "cafe", "fast_food", "bar", "food_court" -> showRestaurant
    "hotel", "guest_house", "hostel", "motel", "apartment" -> showHotel
    else -> showSightseeing
}

@Composable
fun MapGuideScreen(
    region: RegionRecord,
    config: TripConfig,
    regions: List<RegionRecord>,
    simulateGps: Boolean,
    locationHelper: LocationHelper,
    onOpenOptions: () -> Unit,
    onMainHub: () -> Unit,
    onPolicyFallback: (reason: String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PoiRepository(context) }
    val tileStore = remember { TileStore(context) }
    val homeCacheTiles = remember { TileStore(context) }
    val onlineTiles = remember { OnlineTileProvider(context) }
    val overpass = remember { OverpassClient() }
    val geocoder = remember { NominatimGeocoder() }
    val bearingProvider = remember { NavigationBearingProvider(context) }
    val speech = remember { SpeechHelper(context) }
    val catalog = remember { GeoCatalog(context) }
    val listState = rememberLazyListState()

    val homeCode = remember(config) {
        config.homeCountryCode.ifBlank {
            catalog.resolveCountry(config.homeCountry)?.code ?: "KR"
        }
    }
    val homeLang = remember(homeCode) { HomeLanguage.langTag(homeCode) }
    val homeLocale = remember(homeCode) { HomeLanguage.locale(homeCode) }
    val ui: MapUiStrings = remember(homeCode) { HomeLanguage.mapUi(homeCode) }
    val destLang = remember(region) { HomeLanguage.langTagFromCountryName(region.countryLabel) }
    val destLocale = remember(destLang) { java.util.Locale.forLanguageTag(destLang) }
    val abroadRegion = remember(config, region) { !TripNavigation.isHomeRegion(config, region) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    var bundle by remember { mutableStateOf(PoiBundle()) }
    var regionLoaded by remember { mutableStateOf(false) }
    var tilesReady by remember { mutableStateOf(false) }
    var availableZooms by remember { mutableStateOf(RegionDownloadManager.ONLINE_ZOOMS) }
    var hasRealGpsFix by remember(region.id) { mutableStateOf(simulateGps) }
    var pos by remember(region.id) {
        mutableStateOf(UserPosition(region.lat, region.lon, simulated = true))
    }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var showSightseeing by remember { mutableStateOf(config.showSight) }
    var showRestaurant by remember { mutableStateOf(config.showRestaurant) }
    var showHotel by remember { mutableStateOf(config.showHotel) }
    var minStarFilter by remember { mutableStateOf(0f) }
    var followGps by remember { mutableStateOf(false) }
    /** 오른쪽 위 크로스헤어 — GPS 현재 위치 화면 중앙 고정 */
    var gpsLocked by remember(region.id) { mutableStateOf(true) }
    var selectedPoiId by remember { mutableStateOf<String?>(null) }
    var routingGraph by remember { mutableStateOf<RoutingGraph?>(null) }
    var routePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var routeDistanceM by remember { mutableStateOf<Int?>(null) }
    var ttsOk by remember { mutableStateOf(true) }
    var hasInternet by remember { mutableStateOf(WifiGate.hasInternet(context)) }
    var wasHomeLive by remember(region.id) { mutableStateOf(false) }
    var policyFallbackSent by remember(region.id) { mutableStateOf(false) }
    var livePois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var homeCachePois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var headingDeg by remember { mutableStateOf<Float?>(null) }
    var targetHeadingDeg by remember { mutableStateOf<Float?>(null) }
    var tileCacheGeneration by remember { mutableIntStateOf(0) }
    var radarRadiusM by remember { mutableIntStateOf(100) }
    var gpsPlaceLabel by remember { mutableStateOf<String?>(null) }
    var lastReverseLat by remember { mutableStateOf<Double?>(null) }
    var lastReverseLon by remember { mutableStateOf<Double?>(null) }
    val navAnchor = remember { NavigationAnchor() }
    var navMapLat by remember { mutableStateOf(0.0) }
    var navMapLon by remember { mutableStateOf(0.0) }

    val upcomingAnchor = remember(config, regions) {
        TripNavigation.upcomingTravelAnchor(config, regions)
    }

    DisposableEffect(Unit) {
        onDispose { speech.shutdown() }
    }

    LaunchedEffect(Unit) {
        WifiGate.wifiFlow(context).collect {
            hasInternet = WifiGate.hasInternet(context)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    fun applyGpsFix(fix: UserPosition) {
        hasRealGpsFix = true
        pos = fix.copy(bearingDeg = headingDeg, simulated = false)
        if (navAnchor.update(fix.lat, fix.lon, fix.speedMps)) {
            navMapLat = navAnchor.lat
            navMapLon = navAnchor.lon
        }
    }

    LaunchedEffect(hasLocationPermission, simulateGps) {
        if (simulateGps) {
            hasRealGpsFix = true
            pos = UserPosition(region.lat, region.lon, simulated = false)
            return@LaunchedEffect
        }
        if (!hasLocationPermission) return@LaunchedEffect
        runCatching { locationHelper.acquirePosition() }.onSuccess { applyGpsFix(it) }
    }

    LaunchedEffect(config.showHotel, config.showRestaurant, config.showSight) {
        showHotel = config.showHotel
        showRestaurant = config.showRestaurant
        showSightseeing = config.showSight
    }

    LaunchedEffect(region.id) {
        regionLoaded = false
        bundle = repo.loadRegionBundle(region.id)
        selectedPoiId = null
        val count = tileStore.loadRegion(region.id)
        val atHomeGps = hasRealGpsFix && TripNavigation.isAtHomeCountry(config, pos.lat, pos.lon)
        val homeCount = if (TripNavigation.isHomeRegion(config, region) || atHomeGps) {
            homeCacheTiles.loadRegion(HomeProgressiveDownloader.REGION_ID)
        } else {
            0
        }
        availableZooms = (
            tileStore.availableZooms() + homeCacheTiles.availableZooms() + RegionDownloadManager.ONLINE_ZOOMS
            ).ifEmpty { RegionDownloadManager.ONLINE_ZOOMS }
        homeCachePois = repo.loadRegionBundle(HomeProgressiveDownloader.REGION_ID).pois
        tilesReady = count > 0 || homeCount > 0 ||
            (hasInternet && (TripNavigation.isHomeRegion(config, region) || atHomeGps))
        if (homeCount > 0 || count > 0) tileCacheGeneration++
        routingGraph = RoutingGraph.load(context, region.id)
        if (TripNavigation.isHomeRegion(config, region) || region.id == MapPolicy.HOME_LIVE_ONLINE_ID) {
            val homeGraph = RoutingGraph.load(context, HomeProgressiveDownloader.REGION_ID)
            if (homeGraph.hasData) routingGraph = homeGraph
        }
        regionLoaded = true
        val bb = effectiveRegionBbox(bundle, region)
        val homeLiveInit = TripNavigation.isHomeLiveMode(config, pos, region, hasRealGpsFix, hasInternet)
        val onSiteNow = TripNavigation.isOnSite(pos, region, bb, hasRealGpsFix)
        followGps = onSiteNow || homeLiveInit
        gpsLocked = onSiteNow || homeLiveInit
        val centerGps = onSiteNow || homeLiveInit
        camera = MapCameraMath.defaultCamera(
            if (centerGps) pos else null,
            bb,
            availableZooms,
            centerOnPos = centerGps,
            heading = centerGps,
        )
    }

    val regionBbox = effectiveRegionBbox(bundle, region)
    val distantTileClipBbox = remember(
        config, region, pos.lat, pos.lon, camera?.visibleSpanM,
    ) {
        MapMath.resolveDistantTileClipBbox(
            config,
            region,
            pos.lat,
            pos.lon,
            camera?.visibleSpanM ?: MapCameraMath.SPAN_HEADING_DEFAULT_M,
        )
    }
    val homeLive = TripNavigation.isHomeLiveMode(config, pos, region, hasRealGpsFix, hasInternet)
    val onSite = TripNavigation.isOnSite(pos, region, regionBbox, hasRealGpsFix)
    val useOnlineMap = homeLive

    LaunchedEffect(hasRealGpsFix, homeLive, onSite, hasInternet, pos.lat, pos.lon) {
        if (!hasRealGpsFix || (!homeLive && !onSite)) {
            gpsPlaceLabel = null
            lastReverseLat = null
            lastReverseLon = null
            return@LaunchedEffect
        }
        val prevLat = lastReverseLat
        val prevLon = lastReverseLon
        if (prevLat != null && prevLon != null && gpsPlaceLabel != null) {
            if (PoiLogic.haversineM(prevLat, prevLon, pos.lat, pos.lon) < 150.0) return@LaunchedEffect
        }
        if (!hasInternet) {
            gpsPlaceLabel = String.format(Locale.getDefault(), "%.4f°, %.4f°", pos.lat, pos.lon)
            lastReverseLat = pos.lat
            lastReverseLon = pos.lon
            return@LaunchedEffect
        }
        delay(350)
        val label = runCatching { geocoder.reverse(pos.lat, pos.lon)?.adminCityLabel() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.getDefault(), "%.4f°, %.4f°", pos.lat, pos.lon)
        gpsPlaceLabel = label
        lastReverseLat = pos.lat
        lastReverseLon = pos.lon
    }

    val headerCountryCode = remember(config, region, homeLive, homeCode) {
        if (homeLive) {
            homeCode
        } else {
            catalog.resolveCountry(region.countryLabel)?.code?.ifBlank { null } ?: homeCode
        }
    }
    val headerFlag = remember(headerCountryCode) { countryFlagEmoji(headerCountryCode) }

    val mapHeaderTitle = when {
        hasRealGpsFix && (homeLive || onSite) -> {
            val place = gpsPlaceLabel
                ?: String.format(Locale.getDefault(), "%.4f°, %.4f°", pos.lat, pos.lon)
            "$headerFlag $place"
        }
        else -> "$headerFlag ${region.cityName}"
    }

    LaunchedEffect(homeLive) {
        if (homeLive) wasHomeLive = true
    }

    LaunchedEffect(wasHomeLive, hasInternet, pos.lat, pos.lon, hasRealGpsFix) {
        if (policyFallbackSent || !wasHomeLive || !TripNavigation.isHomeRegion(config, region)) {
            return@LaunchedEffect
        }
        if (!TripNavigation.isHomeLiveMode(config, pos, region, hasRealGpsFix, hasInternet)) {
            policyFallbackSent = true
            val reason = when {
                !hasInternet -> "WiFi/인터넷 연결이 끊겼습니다"
                !TripNavigation.isAtHomeCountry(config, pos.lat, pos.lon) -> "모국 밖으로 이동했습니다"
                else -> "일반 지도 조건이 해제되었습니다"
            }
            onPolicyFallback(reason)
        }
    }
    val showGpsPin = hasRealGpsFix && !pos.simulated && (onSite || homeLive)
    val gpsControlEnabled = showGpsPin
    val headingModeActive = showGpsPin && gpsLocked && hasLocationPermission && !simulateGps

    LaunchedEffect(showGpsPin, pos.lat, pos.lon) {
        if (!showGpsPin || !hasRealGpsFix) return@LaunchedEffect
        if (!navAnchor.isReady) {
            navAnchor.reset(pos.lat, pos.lon)
            navMapLat = navAnchor.lat
            navMapLon = navAnchor.lon
        }
    }

    fun disableGpsLock() {
        if (!gpsLocked) return
        gpsLocked = false
        // 해제 = 헤딩·중앙핀만 OFF — follow·배율·줌 변경 금지
        camera?.let { c ->
            if (mapSize.width > 0 && mapSize.height > 0) {
                camera = MapCameraMath.bakePan(
                    c,
                    mapSize.width.toFloat(),
                    mapSize.height.toFloat(),
                )
            }
        }
    }

    fun enableGpsLock() {
        if (!showGpsPin || !hasRealGpsFix) return
        navAnchor.reset(pos.lat, pos.lon)
        navMapLat = navAnchor.lat
        navMapLon = navAnchor.lon
        gpsLocked = true
        followGps = true
        val c = camera ?: return
        val w = if (mapSize.width > 0) mapSize.width.toFloat() else 720f
        var next = c.lockAnchor(navMapLat, navMapLon)
        if (next.visibleSpanM > MapCameraMath.SPAN_HEADING_MAX_M) {
            next = MapCameraMath.cameraWithSpan(
                next,
                MapCameraMath.SPAN_HEADING_MAX_M,
                w,
                availableZooms,
                heading = true,
            )
        }
        camera = next
    }

    fun toggleGpsLock() {
        if (gpsLocked) disableGpsLock() else enableGpsLock()
    }

    LaunchedEffect(headingModeActive, mapSize.width, camera?.visibleSpanM) {
        if (!headingModeActive || mapSize.width <= 0) return@LaunchedEffect
        val c = camera ?: return@LaunchedEffect
        if (c.visibleSpanM <= MapCameraMath.SPAN_HEADING_MAX_M) return@LaunchedEffect
        camera = MapCameraMath.cameraWithSpan(
            c,
            MapCameraMath.SPAN_HEADING_MAX_M,
            mapSize.width.toFloat(),
            availableZooms,
            heading = true,
        )
    }

    DisposableEffect(headingModeActive) {
        bearingProvider.compassOnly = headingModeActive
        if (headingModeActive) {
            bearingProvider.onBearing = { deg ->
                targetHeadingDeg = deg
                headingDeg = deg
            }
            bearingProvider.start()
        } else {
            bearingProvider.stop()
            bearingProvider.onBearing = null
            targetHeadingDeg = null
            headingDeg = null
        }
        onDispose { bearingProvider.stop() }
    }

    LaunchedEffect(headingModeActive) {
        if (!headingModeActive || !hasRealGpsFix) return@LaunchedEffect
        if (!navAnchor.isReady) {
            navAnchor.reset(pos.lat, pos.lon)
            navMapLat = navAnchor.lat
            navMapLon = navAnchor.lon
        }
        val c = camera ?: return@LaunchedEffect
        // pan 중이면 카메라 리셋 금지
        if (kotlin.math.abs(c.panX) <= 0.5f && kotlin.math.abs(c.panY) <= 0.5f) {
            camera = c.lockAnchor(navMapLat, navMapLon)
        }
    }

    DisposableEffect(hasLocationPermission, simulateGps, headingModeActive) {
        if (hasLocationPermission && !simulateGps) {
            locationHelper.startUpdates(
                onUpdate = { applyGpsFix(it) },
                bearingProvider = if (headingModeActive) bearingProvider else null,
                navigationLock = headingModeActive,
            )
        }
        onDispose { locationHelper.stopUpdates() }
    }

    LaunchedEffect(onSite, homeLive) {
        if (!onSite && !homeLive) {
            followGps = false
            gpsLocked = false
        }
    }

    LaunchedEffect(navMapLat, navMapLon, gpsLocked, headingModeActive, camera?.panX, camera?.panY) {
        if (!gpsLocked || !navAnchor.isReady) return@LaunchedEffect
        val c = camera ?: return@LaunchedEffect
        // pan 탐색 중 — GPS 앵커·카메라 갱신 금지 (핀은 화면 중앙 고정)
        if (kotlin.math.abs(c.panX) > 0.5f || kotlin.math.abs(c.panY) > 0.5f) return@LaunchedEffect
        if (PoiLogic.haversineM(c.centerLat, c.centerLon, navMapLat, navMapLon) < 5.0) return@LaunchedEffect
        camera = c.updateAnchor(navMapLat, navMapLon)
    }

    LaunchedEffect(navMapLat, navMapLon, followGps, onSite, homeLive, headingModeActive, gpsLocked) {
        if (!onSite && !homeLive) return@LaunchedEffect
        if (headingModeActive || gpsLocked) return@LaunchedEffect
        if (!followGps) return@LaunchedEffect
        camera = camera?.recenter(pos.lat, pos.lon) ?: return@LaunchedEffect
    }

    LaunchedEffect(homeLive, hasInternet, pos.lat, pos.lon) {
        if (!homeLive || !hasInternet) {
            livePois = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        val bb = PoiLogic.bboxAround(pos.lat, pos.lon, MapMath.POI_USER_RADIUS_KM)
        livePois = runCatching { overpass.fetchPois(bb, homeLang) }.getOrDefault(emptyList())
    }

    LaunchedEffect(homeLive, hasRealGpsFix, pos.lat, pos.lon) {
        if (!homeLive && !TripNavigation.isAtHomeCountry(config, pos.lat, pos.lon)) return@LaunchedEffect
        val n = homeCacheTiles.loadRegion(HomeProgressiveDownloader.REGION_ID, force = true)
        if (n > 0) {
            tilesReady = true
            availableZooms = (
                tileStore.availableZooms() + homeCacheTiles.availableZooms() + RegionDownloadManager.ONLINE_ZOOMS
                ).ifEmpty { RegionDownloadManager.ONLINE_ZOOMS }
            tileCacheGeneration++
        }
    }

    LaunchedEffect(homeLive, hasInternet, hasRealGpsFix, pos.lat, pos.lon) {
        if (!homeLive || !hasInternet || !hasRealGpsFix) return@LaunchedEffect
        suspend fun applyHomeRoutingGraph() {
            val homeGraph = RoutingGraph.reload(context, HomeProgressiveDownloader.REGION_ID)
            if (homeGraph.hasData) routingGraph = homeGraph
        }
        HomeProgressiveDownloader.runIfNeeded(
            context,
            pos.lat,
            pos.lon,
            config.homeCountry,
            homeLang,
            onPoiBatch = {
                homeCachePois = repo.loadRegionBundle(HomeProgressiveDownloader.REGION_ID).pois
                val n = homeCacheTiles.loadRegion(HomeProgressiveDownloader.REGION_ID, force = true)
                availableZooms = (
                    tileStore.availableZooms() + homeCacheTiles.availableZooms() + RegionDownloadManager.ONLINE_ZOOMS
                    )
                if (n > 0) tilesReady = true
                tileCacheGeneration++
                applyHomeRoutingGraph()
            },
            onRoutingGraphReady = { applyHomeRoutingGraph() },
        )
        applyHomeRoutingGraph()
    }

    LaunchedEffect(homeLive, selectedPoiId, hasInternet, routingGraph?.hasData) {
        if (!homeLive || selectedPoiId == null || !hasInternet) return@LaunchedEffect
        if (routingGraph?.hasData == true) return@LaunchedEffect
        val built = HomeProgressiveDownloader.ensureRoutingGraphAt(context, pos.lat, pos.lon)
        if (built) {
            val homeGraph = RoutingGraph.reload(context, HomeProgressiveDownloader.REGION_ID)
            if (homeGraph.hasData) routingGraph = homeGraph
        }
    }

    val allPois = remember(bundle.pois, livePois, homeCachePois) {
        (bundle.pois + livePois + homeCachePois).distinctBy { it.id }
    }

    val cam = camera ?: return

    val mapW = if (mapSize.width > 0) mapSize.width.toFloat() else 720f

    fun applyLocate() {
        when {
            followGps && (onSite || homeLive) -> {
                if (showGpsPin && gpsLocked) {
                    camera = cam.lockAnchor(navMapLat, navMapLon)
                } else {
                    camera = cam.recenter(pos.lat, pos.lon)
                }
            }
            !followGps -> {
                val anchor = upcomingAnchor
                camera = if (anchor != null) {
                    MapCameraMath.cameraAt(anchor.lat, anchor.lon, availableZooms, screenW = mapW)
                } else {
                    MapCameraMath.defaultCamera(null, regionBbox, availableZooms, screenW = mapW)
                }
            }
            else -> camera = MapCameraMath.defaultCamera(null, regionBbox, availableZooms, screenW = mapW)
        }
    }

    val useNavAnchor = headingModeActive && navAnchor.isReady
    val poiAnchorLat = when {
        showGpsPin -> if (useNavAnchor) navMapLat else pos.lat
        followGps && (onSite || homeLive) -> pos.lat
        onSite -> pos.lat
        else -> cam.centerLat
    }
    val poiAnchorLon = when {
        showGpsPin -> if (useNavAnchor) navMapLon else pos.lon
        followGps && (onSite || homeLive) -> pos.lon
        onSite -> pos.lon
        else -> cam.centerLon
    }
    val mapUser = if (showGpsPin) {
        val lat = if (useNavAnchor) navMapLat else pos.lat
        val lon = if (useNavAnchor) navMapLon else pos.lon
        UserPosition(lat, lon, bearingDeg = headingDeg, speedMps = pos.speedMps, simulated = pos.simulated)
    } else null

    fun cycleRadarRadius() {
        radarRadiusM = MapMath.nextRadarRadiusM(radarRadiusM)
    }

    LaunchedEffect(homeLive) {
        if (!homeLive) return@LaunchedEffect
        val homeGraph = RoutingGraph.load(context, HomeProgressiveDownloader.REGION_ID)
        if (homeGraph.hasData) routingGraph = homeGraph
    }

    val poiRadiusKm = if (showGpsPin) MapMath.radarRadiusKm(radarRadiusM) else MapMath.POI_VIEW_RADIUS_KM

    val radiusPois = remember(allPois, poiAnchorLat, poiAnchorLon, poiRadiusKm) {
        PoiLogic.withinRadiusKm(allPois, poiAnchorLat, poiAnchorLon, poiRadiusKm)
    }

    val renderCam = remember(cam, mapSize, availableZooms) {
        if (mapSize.width <= 0) {
            cam
        } else {
            val w = mapSize.width.toFloat()
            val z = MapCameraMath.pickRenderZoom(cam.centerLat, w, cam.visibleSpanM, availableZooms)
            if (z == cam.baseZoom) cam else cam.copy(baseZoom = z)
        }
    }

    val viewportAll = remember(
        renderCam, radiusPois, mapSize, poiAnchorLat, poiAnchorLon, gpsLocked, showGpsPin,
    ) {
        if (mapSize.width <= 0) return@remember emptyList()
        val w = mapSize.width.toFloat()
        val h = mapSize.height.toFloat()
        val list = if (gpsLocked && showGpsPin) {
            // GPS 고정 — pan·줌과 무관하게 반경 내 POI 전부 (geo 뷰포트 필터는 회전·pan과 불일치)
            radiusPois
        } else {
            PoiLogic.visibleInViewport(
                radiusPois, renderCam, w, h, poiAnchorLat, poiAnchorLon,
            )
        }
        list.map { p ->
            val dist = PoiLogic.haversineM(poiAnchorLat, poiAnchorLon, p.lat, p.lon).toInt()
            p.copy(distanceM = dist)
        }.sortedWith(compareBy<Poi> { it.distanceM ?: Int.MAX_VALUE }.thenBy { it.nameKo })
    }

    val kindFiltered = remember(viewportAll, showSightseeing, showRestaurant, showHotel) {
        viewportAll.filter { p -> matchesKindFilter(p, showRestaurant, showHotel, showSightseeing) }
    }

    val filteredPois = remember(kindFiltered, minStarFilter) {
        kindFiltered.filter { p -> PoiLogic.displayRating(p) >= minStarFilter }
    }

    LaunchedEffect(filteredPois, selectedPoiId) {
        if (selectedPoiId != null && filteredPois.none { it.id == selectedPoiId }) {
            selectedPoiId = null
        }
    }

    val emptyListMessage = when {
        showGpsPin && radarRadiusM == 0 -> "반경 OFF"
        !showRestaurant && !showHotel && !showSightseeing -> ui.emptyFilterKind
        kindFiltered.isNotEmpty() && filteredPois.isEmpty() -> ui.emptyStarFilter
        else -> ui.emptyNearby
    }

    fun localized(p: Poi): LocalizedPoi = PoiLocalization.forDisplay(p, homeLang, destLang)

    fun speakPoi(p: Poi) {
        scope.launch {
            val loc = localized(p)
            speech.speak(loc.ttsText, homeLocale) { ttsOk = false }
        }
    }

    LaunchedEffect(selectedPoiId, poiAnchorLat, poiAnchorLon, onSite, homeLive, routingGraph, filteredPois) {
        val target = selectedPoiId?.let { id -> filteredPois.find { it.id == id } }
        if (target == null) {
            routePoints = emptyList()
            routeDistanceM = null
            return@LaunchedEffect
        }
        if (!onSite && !homeLive) {
            routePoints = emptyList()
            routeDistanceM = null
            return@LaunchedEffect
        }
        val graph = routingGraph
        if (graph == null || !graph.hasData) {
            routePoints = listOf(poiAnchorLat to poiAnchorLon, target.lat to target.lon)
            routeDistanceM = PoiLogic.haversineM(poiAnchorLat, poiAnchorLon, target.lat, target.lon).toInt()
            return@LaunchedEffect
        }
        val pts = graph.route(poiAnchorLat, poiAnchorLon, target.lat, target.lon)
        routePoints = pts
        routeDistanceM = if (pts.size >= 2) graph.routeLengthM(pts) else null
    }

    LaunchedEffect(selectedPoiId, filteredPois) {
        val id = selectedPoiId ?: return@LaunchedEffect
        val idx = filteredPois.indexOfFirst { it.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(containerColor = Color(0xFFF4F6F8)) { padding ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer { clip = false },
        ) {
            Box(
                Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
                    .zIndex(0f)
                    .graphicsLayer { clip = false },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { clip = false }
                        .onSizeChanged { mapSize = it },
                ) {
                    OfflineTileMap(
                        camera = cam,
                        onCameraChange = { camera = it },
                        onResetCamera = { applyLocate() },
                        tileStore = tileStore,
                        secondaryTileStore = if (homeLive) homeCacheTiles else null,
                        tilesReady = tilesReady,
                        availableZooms = availableZooms,
                        onlineTiles = if (useOnlineMap) onlineTiles else null,
                        screenSize = mapSize,
                        user = mapUser,
                        headingUp = headingModeActive,
                        gpsLocked = gpsLocked,
                        targetBearingDeg = if (headingModeActive) targetHeadingDeg else null,
                        pois = filteredPois,
                        highlightedPoiId = selectedPoiId?.takeIf { id -> filteredPois.any { it.id == id } },
                        routePoints = routePoints,
                        routeDistanceM = routeDistanceM,
                        userRadiusKm = if (showGpsPin && radarRadiusM > 0) {
                            MapMath.radarRadiusKm(radarRadiusM)
                        } else {
                            null
                        },
                        onGpsLockOff = { disableGpsLock() },
                        onPoiClick = { poi ->
                            selectedPoiId = if (selectedPoiId == poi.id) null else poi.id
                        },
                        tileCacheGeneration = tileCacheGeneration,
                        regionClipBbox = distantTileClipBbox,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (regionLoaded && !tilesReady) {
                        Column(
                            Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                ui.tilesMissing,
                                color = Color(0xFFDC2626),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else if (!regionLoaded) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                    if (regionLoaded && !onSite && !homeLive) {
                        Text(
                            ui.previewMode,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 36.dp)
                                .padding(horizontal = 8.dp),
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    } else if (regionLoaded && homeLive && useOnlineMap) {
                        Text(
                            ui.homeLiveMode,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .padding(horizontal = 8.dp),
                            color = Color(0xFF15803D),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    IconButton(
                        onClick = onOpenOptions,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .mapChromeTop()
                            .padding(start = 2.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = ui.options, tint = Color(0xFF334155))
                    }
                    IconButton(
                        onClick = onMainHub,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .mapChromeTop()
                            .padding(start = 44.dp),
                    ) {
                        Text(ui.main, color = Color(0xFF2563EB), style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        mapHeaderTitle,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .mapChromeTop()
                            .padding(start = 52.dp, end = 52.dp),
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = Shadow(color = Color(0xCCFFFFFF), blurRadius = 6f),
                        ),
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .mapChromeTop()
                            .padding(end = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showGpsPin) {
                            IconButton(onClick = { toggleGpsLock() }) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = if (gpsLocked) "GPS 고정" else "GPS 고정 해제",
                                    tint = if (gpsLocked) Color(0xFF15803D) else Color(0xFF64748B),
                                )
                            }
                            RadarRadiusBadge(
                                radarRadiusM = radarRadiusM,
                                onCycle = { cycleRadarRadius() },
                            )
                        } else {
                            IconButton(onClick = { applyLocate() }) {
                                Icon(Icons.Default.Refresh, contentDescription = ui.reset, tint = Color(0xFF3D8B5E))
                            }
                            IconToggleButton(
                                checked = followGps,
                                enabled = gpsControlEnabled,
                                onCheckedChange = { enabled ->
                                    followGps = enabled
                                    applyLocate()
                                },
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = if (followGps) ui.followGps else ui.followTrip,
                                    tint = if (followGps) Color(0xFF15803D) else Color(0xFF64748B),
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
                    .zIndex(2f)
                    .background(MapUiColors.sidePanelBg)
                    .mapChromeTop()
                    .padding(start = 6.dp, end = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = showRestaurant,
                            onClick = { showRestaurant = !showRestaurant },
                            label = { Text(ui.restaurant, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PoiColors.restaurant.copy(alpha = 0.25f),
                            ),
                        )
                        FilterChip(
                            selected = showHotel,
                            onClick = { showHotel = !showHotel },
                            label = { Text(ui.hotel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PoiColors.hotel.copy(alpha = 0.25f),
                            ),
                        )
                        FilterChip(
                            selected = showSightseeing,
                            onClick = { showSightseeing = !showSightseeing },
                            label = { Text(ui.sight, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PoiColors.sight.copy(alpha = 0.25f),
                            ),
                        )
                    }
                    RecommendDegreeDropdown(
                        minStarFilter = minStarFilter,
                        onFilterChange = { minStarFilter = it },
                        ui = ui,
                    )
                }
                if (!onSite && !homeLive) {
                    Text(
                        ui.previewRouteHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    )
                }
                Text(
                    ui.routeHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (filteredPois.isEmpty()) {
                        item { Text(emptyListMessage, color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                    } else {
                        items(filteredPois, key = { it.id }) { p ->
                            val loc = localized(p)
                            PoiListItem(
                                poi = p,
                                displayName = loc.name,
                                description = loc.description,
                                accent = PoiColors.accent(p),
                                typeLabel = loc.typeLabel,
                                routePrefix = ui.routePrefix,
                                speakLabel = ui.speak,
                                selected = p.id == selectedPoiId,
                                routeDistanceM = if (p.id == selectedPoiId) routeDistanceM else null,
                                onClick = {
                                    selectedPoiId = if (selectedPoiId == p.id) null else p.id
                                },
                                onSpeak = { speakPoi(p) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 추천도 — 팝업만 아래로, 칩 오른쪽 정렬·메뉴는 왼쪽으로 확장 */
@Composable
private fun RecommendDegreeDropdown(
    minStarFilter: Float,
    onFilterChange: (Float) -> Unit,
    ui: MapUiStrings,
) {
    var expanded by remember { mutableStateOf(false) }
    var chipSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val menuWidth = 172.dp

    val label = when (minStarFilter) {
        0f -> ui.recommendFilter
        4.5f -> "${ui.recommendFilter} 4.5+"
        else -> "${ui.recommendFilter} ${minStarFilter.toInt()}+"
    }

    Box(
        modifier = Modifier.wrapContentSize(Alignment.TopEnd),
        contentAlignment = Alignment.TopEnd,
    ) {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            modifier = Modifier
                .onSizeChanged { chipSize = it }
                .widthIn(min = 76.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = if (minStarFilter > 0f) {
                    Color(0xFFD1FAE5)
                } else {
                    Color(0xFFE8F2FC)
                },
            ),
        )
        val menuOffsetX = with(density) {
            chipSize.width.toDp() - menuWidth
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth),
            offset = DpOffset(x = menuOffsetX, y = 0.dp),
        ) {
            listOf(
                0f to ui.starAll,
                3f to "${ui.recommendFilter} 3+",
                4f to "${ui.recommendFilter} 4+",
                4.5f to "${ui.recommendFilter} 4.5+",
            ).forEach { (value, itemLabel) ->
                DropdownMenuItem(
                    text = { Text(itemLabel, style = MaterialTheme.typography.labelSmall) },
                    onClick = {
                        onFilterChange(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
