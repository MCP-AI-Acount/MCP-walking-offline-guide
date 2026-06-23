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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM
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
import com.mcpauto.walkingofflineguide.download.OnDemandRouting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.mcpauto.walkingofflineguide.download.RegionDownloadManager
import com.mcpauto.walkingofflineguide.network.LocalAdminGeocoder
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.OverpassClient
import com.mcpauto.walkingofflineguide.network.adminPlaceLabel
import com.mcpauto.walkingofflineguide.network.WifiGate
import com.mcpauto.walkingofflineguide.network.OnlineTileProvider
import com.mcpauto.walkingofflineguide.util.HomeLanguage
import com.mcpauto.walkingofflineguide.util.MapUiStrings
import com.mcpauto.walkingofflineguide.util.countryFlagEmoji
import kotlinx.coroutines.launch

private fun effectiveRegionBbox(bundle: PoiBundle, region: RegionRecord): Bbox =
    bundle.bbox.takeIf { it.north > it.south } ?: region.bbox

/** 상태바(시간·배터리) 바로 아래 — 지도는 edge-to-edge, 크롬만 inset */
private fun Modifier.mapChromeBelowStatusBar(): Modifier = statusBarsPadding()

/** 지도 상단 크롬 — 메뉴 톱니·뒤로와 같은 세로 위치 */
fun Modifier.mapTopChromeInset(): Modifier =
    statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp)

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

private fun loadHomeCachePois(repo: PoiRepository): List<Poi> = runCatching {
    repo.loadRegionBundle(HomeProgressiveDownloader.REGION_ID).pois
}.getOrDefault(emptyList())

private fun persistHomeCachePois(
    repo: PoiRepository,
    pois: List<Poi>,
    lat: Double,
    lon: Double,
) {
    if (pois.isEmpty()) return
    val bb = PoiLogic.bboxAround(lat, lon, MapMath.POI_VIEW_RADIUS_KM)
    repo.saveRegionBundle(
        PoiBundle(
            region = HomeProgressiveDownloader.REGION_ID,
            labelKo = "모국 주변",
            bbox = bb,
            count = pois.size,
            pois = pois,
        ),
    )
}

private fun persistTravelRegionPois(
    repo: PoiRepository,
    regionId: String,
    pois: List<Poi>,
    lat: Double,
    lon: Double,
) {
    if (pois.isEmpty() || regionId.isBlank() || regionId == MapPolicy.HOME_LIVE_ONLINE_ID) return
    val bb = PoiLogic.bboxAround(lat, lon, MapMath.POI_VIEW_RADIUS_KM)
    val existing = repo.loadRegionBundle(regionId)
    val merged = (existing.pois + pois).distinctBy { it.id }
    repo.saveRegionBundle(
        PoiBundle(
            region = regionId,
            labelKo = existing.labelKo.ifBlank { regionId },
            bbox = bb,
            count = merged.size,
            pois = merged,
        ),
    )
}

private suspend fun fetchNearbyPois(
    overpass: OverpassClient,
    bb: Bbox,
    homeLang: String,
    destLang: String,
): List<Poi> {
    val raw = overpass.fetchPois(bb, homeLang)
    if (raw.isEmpty()) return emptyList()
    return PoiLocalization.enrichForKoreanHome(raw, destLang, homeLang)
}

private suspend fun applyFetchedPois(
    repo: PoiRepository,
    regionId: String,
    pois: List<Poi>,
    anchorLat: Double,
    anchorLon: Double,
    homeCtx: Boolean,
): PoiBundle? {
    if (pois.isEmpty()) return null
    withContext(Dispatchers.IO) {
        if (homeCtx) {
            persistHomeCachePois(repo, pois, anchorLat, anchorLon)
        } else {
            persistTravelRegionPois(repo, regionId, pois, anchorLat, anchorLon)
        }
    }
    return if (homeCtx) {
        repo.loadRegionBundle(HomeProgressiveDownloader.REGION_ID)
    } else {
        repo.loadRegionBundle(regionId)
    }
}

@Composable
fun MapGuideScreen(
    region: RegionRecord,
    config: TripConfig,
    regions: List<RegionRecord>,
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
    var hasRealGpsFix by remember(region.id) { mutableStateOf(false) }
    var pos by remember(region.id) {
        mutableStateOf(UserPosition(region.lat, region.lon, simulated = true))
    }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var showSightseeing by remember { mutableStateOf(config.showSight) }
    var showRestaurant by remember { mutableStateOf(config.showRestaurant) }
    var showHotel by remember { mutableStateOf(config.showHotel) }
    var minStarFilter by remember { mutableStateOf(0f) }
    var followGps by remember(region.id) { mutableStateOf(false) }
    /** 오른쪽 위 크로스헤어 — GPS 현재 위치 화면 중앙 고정 */
    var gpsLocked by remember(region.id) { mutableStateOf(true) }
    /** 유저가 GPS 고정을 직접 해제한 경우 — 재진입 시 자동 ON 금지 */
    var userGpsLockOff by remember(region.id) { mutableStateOf(false) }
    var selectedPoiId by remember { mutableStateOf<String?>(null) }
    var routingGraph by remember { mutableStateOf<RoutingGraph?>(null) }
    var routePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var routeDistanceM by remember { mutableStateOf<Int?>(null) }
    var straightLineDistanceM by remember { mutableStateOf<Int?>(null) }
    var ttsOk by remember { mutableStateOf(true) }
    var hasInternet by remember { mutableStateOf(WifiGate.hasInternet(context)) }
    var wasHomeLive by remember(region.id) { mutableStateOf(false) }
    var policyFallbackSent by remember(region.id) { mutableStateOf(false) }
    var livePois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var homeCachePois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var poiFetchError by remember { mutableStateOf<String?>(null) }
    var headingDeg by remember { mutableStateOf<Float?>(null) }
    var targetHeadingDeg by remember { mutableStateOf<Float?>(null) }
    var tileCacheGeneration by remember { mutableIntStateOf(0) }
    var radarRadiusM by remember { mutableIntStateOf(1000) }
    var gpsPlaceLabel by remember { mutableStateOf<String?>(null) }
    var lastReverseLat by remember { mutableStateOf<Double?>(null) }
    var lastReverseLon by remember { mutableStateOf<Double?>(null) }
    val navAnchor = remember { NavigationAnchor() }
    var navMapLat by remember { mutableStateOf(0.0) }
    var navMapLon by remember { mutableStateOf(0.0) }

    val previewAnchor = remember(config, region) {
        TripNavigation.scheduleAnchorForCity(config, region)
    }
    var previewAnchorLocked by remember(region.id) { mutableStateOf(true) }

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

    LaunchedEffect(hasLocationPermission, region.id) {
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
        livePois = emptyList()
        poiFetchError = null
        try {
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
            homeCachePois = runCatching {
                repo.loadRegionBundle(HomeProgressiveDownloader.REGION_ID).pois
            }.getOrDefault(emptyList())
            tilesReady = count > 0 || homeCount > 0 ||
                (hasInternet && (TripNavigation.isHomeRegion(config, region) || atHomeGps))
            if (homeCount > 0 || count > 0) tileCacheGeneration++
            routingGraph = RoutingGraph.load(context, region.id)
            if (TripNavigation.isHomeRegion(config, region) || region.id == MapPolicy.HOME_LIVE_ONLINE_ID) {
                val homeGraph = RoutingGraph.load(context, HomeProgressiveDownloader.REGION_ID)
                if (homeGraph.hasData) routingGraph = homeGraph
            }
            val bb = effectiveRegionBbox(bundle, region)
            val homeLiveInit = TripNavigation.isHomeLiveMode(config, pos, region, hasRealGpsFix, hasInternet)
            val onSiteNow = TripNavigation.isOnSite(pos, region, bb, hasRealGpsFix)
            val atHomeForPoiNow = TripNavigation.isAtHomeForPoi(config, pos, region, hasRealGpsFix)
            val gpsAnchorNow = hasRealGpsFix && !pos.simulated && (onSiteNow || homeLiveInit || atHomeForPoiNow)
            if (gpsAnchorNow) {
                followGps = true
                if (!userGpsLockOff) gpsLocked = true
            } else {
                followGps = false
                gpsLocked = false
                previewAnchorLocked = true
            }
            val w = if (mapSize.width > 0) mapSize.width.toFloat() else 720f
            camera = if (gpsAnchorNow) {
                MapCameraMath.defaultCamera(
                    pos,
                    bb,
                    availableZooms,
                    centerOnPos = true,
                    heading = true,
                    screenW = w,
                )
            } else {
                MapCameraMath.cameraAt(previewAnchor.lat, previewAnchor.lon, availableZooms, screenW = w)
            }
        } finally {
            regionLoaded = true
        }
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
    val atHomeForPoi = TripNavigation.isAtHomeForPoi(config, pos, region, hasRealGpsFix)
    val onSite = TripNavigation.isOnSite(pos, region, regionBbox, hasRealGpsFix)
    val useOnlineMap = homeLive
    /** true = 실제 GPS · false = 일정 시작 위치 (데이터 소스만 다름, 지도 기능 동일) */
    val useGpsAnchor = hasRealGpsFix && !pos.simulated && (onSite || homeLive || atHomeForPoi)
    val showGpsPin = useGpsAnchor
    val headingModeActive = useGpsAnchor && gpsLocked && hasLocationPermission
    val locationLocked = if (useGpsAnchor) gpsLocked else previewAnchorLocked
    val lockContentDescription = if (useGpsAnchor) ui.followGps else ui.previewStartHold

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
        suspend fun resolvePlaceLabel(): String? {
            if (hasInternet) {
                delay(350)
                runCatching { geocoder.reverse(pos.lat, pos.lon)?.adminPlaceLabel() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
            return LocalAdminGeocoder.reverseLabel(context, pos.lat, pos.lon)
        }
        resolvePlaceLabel()?.let { label ->
            gpsPlaceLabel = label
            lastReverseLat = pos.lat
            lastReverseLon = pos.lon
        }
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
        useGpsAnchor && (homeLive || onSite) -> {
            val place = gpsPlaceLabel?.takeIf { it.isNotBlank() } ?: "위치 확인 중…"
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

    LaunchedEffect(showGpsPin, pos.lat, pos.lon) {
        if (!showGpsPin || !hasRealGpsFix) return@LaunchedEffect
        if (!navAnchor.isReady) {
            navAnchor.reset(pos.lat, pos.lon)
            navMapLat = navAnchor.lat
            navMapLon = navAnchor.lon
        }
    }

    fun centerOnScheduleAnchor() {
        previewAnchorLocked = true
        followGps = false
        val w = if (mapSize.width > 0) mapSize.width.toFloat() else 720f
        camera = MapCameraMath.cameraAt(previewAnchor.lat, previewAnchor.lon, availableZooms, screenW = w)
        pos = pos.copy(lat = previewAnchor.lat, lon = previewAnchor.lon, simulated = true)
    }

    fun toggleScheduleAnchorHold() {
        if (previewAnchorLocked) {
            previewAnchorLocked = false
        } else {
            centerOnScheduleAnchor()
        }
    }

    LaunchedEffect(useGpsAnchor, previewAnchorLocked, previewAnchor.lat, previewAnchor.lon) {
        if (useGpsAnchor || !previewAnchorLocked) return@LaunchedEffect
        camera = camera?.recenter(previewAnchor.lat, previewAnchor.lon)
            ?: MapCameraMath.cameraAt(previewAnchor.lat, previewAnchor.lon, availableZooms)
    }

    fun disableGpsLock() {
        if (!gpsLocked) return
        userGpsLockOff = true
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
        userGpsLockOff = false
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

    fun disableLocationLock() {
        if (useGpsAnchor) disableGpsLock() else previewAnchorLocked = false
    }

    fun toggleLocationLock() {
        if (useGpsAnchor) toggleGpsLock() else toggleScheduleAnchorHold()
    }

    LaunchedEffect(useGpsAnchor, hasRealGpsFix, userGpsLockOff) {
        if (useGpsAnchor && hasRealGpsFix && !userGpsLockOff) {
            followGps = true
            enableGpsLock()
        } else if (!useGpsAnchor) {
            followGps = false
            gpsLocked = false
            if (pos.lat != previewAnchor.lat || pos.lon != previewAnchor.lon) {
                pos = pos.copy(lat = previewAnchor.lat, lon = previewAnchor.lon, simulated = true)
            }
        }
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

    DisposableEffect(hasLocationPermission, headingModeActive) {
        if (hasLocationPermission) {
            locationHelper.startUpdates(
                onUpdate = { applyGpsFix(it) },
                bearingProvider = if (headingModeActive) bearingProvider else null,
                navigationLock = headingModeActive,
            )
        }
        onDispose { locationHelper.stopUpdates() }
    }

    LaunchedEffect(onSite, homeLive, useGpsAnchor) {
        if (!useGpsAnchor) {
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

    LaunchedEffect(atHomeForPoi, onSite, homeLive, useGpsAnchor, hasInternet, pos.lat, pos.lon, radarRadiusM) {
        val homeCtx = atHomeForPoi || homeLive
        val anchorLat = if (useGpsAnchor) pos.lat else previewAnchor.lat
        val anchorLon = if (useGpsAnchor) pos.lon else previewAnchor.lon
        if (useGpsAnchor && (!hasRealGpsFix || pos.simulated)) return@LaunchedEffect

        homeCachePois = loadHomeCachePois(repo)
        if (homeCachePois.isNotEmpty() || bundle.pois.isNotEmpty()) {
            poiFetchError = null
        }

        if (!hasInternet) return@LaunchedEffect

        val radiiKm = listOf(4.0, 2.0, 8.0)
        for (rKm in radiiKm) {
            val bb = PoiLogic.bboxAround(anchorLat, anchorLon, rKm)
            val result = runCatching { fetchNearbyPois(overpass, bb, homeLang, destLang) }
            result.onSuccess { fetched ->
                if (fetched.isNotEmpty()) {
                    livePois = fetched
                    poiFetchError = null
                    val updated = applyFetchedPois(repo, region.id, fetched, anchorLat, anchorLon, homeCtx)
                    if (homeCtx) {
                        homeCachePois = updated?.pois ?: fetched
                    } else if (updated != null) {
                        bundle = updated
                    }
                    return@LaunchedEffect
                }
            }.onFailure { err ->
                poiFetchError = err.message?.take(120)
            }
            delay(300)
        }
    }

    // POI 없으면 8초마다 재시도 (GPS·시작 위치·미리보기 공통)
    LaunchedEffect(
        region.id,
        atHomeForPoi,
        homeLive,
        onSite,
        useGpsAnchor,
        hasInternet,
        hasRealGpsFix,
        pos.lat,
        pos.lon,
        previewAnchor.lat,
        previewAnchor.lon,
    ) {
        val homeCtx = atHomeForPoi || homeLive
        val anchorLat = if (useGpsAnchor) pos.lat else previewAnchor.lat
        val anchorLon = if (useGpsAnchor) pos.lon else previewAnchor.lon
        while (true) {
            delay(8000)
            if (useGpsAnchor && (!hasRealGpsFix || pos.simulated)) continue
            homeCachePois = loadHomeCachePois(repo)
            val total = (bundle.pois + livePois + homeCachePois).distinctBy { it.id }
            if (total.isNotEmpty()) {
                poiFetchError = null
                continue
            }
            if (!hasInternet) continue
            val bb = PoiLogic.bboxAround(anchorLat, anchorLon, MapMath.POI_VIEW_RADIUS_KM)
            runCatching { fetchNearbyPois(overpass, bb, homeLang, destLang) }.onSuccess { fetched ->
                if (fetched.isNotEmpty()) {
                    livePois = fetched
                    poiFetchError = null
                    val updated = applyFetchedPois(repo, region.id, fetched, anchorLat, anchorLon, homeCtx)
                    if (homeCtx) {
                        homeCachePois = updated?.pois ?: fetched
                    } else if (updated != null) {
                        bundle = updated
                    }
                }
            }.onFailure { err ->
                poiFetchError = err.message?.take(120)
            }
        }
    }

    // WiFi 보충 — 여행 미리보기(시작 위치) 즉시 1회
    LaunchedEffect(region.id, useGpsAnchor, atHomeForPoi, homeLive, hasInternet, previewAnchor.lat, previewAnchor.lon) {
        if (useGpsAnchor || atHomeForPoi || homeLive || !hasInternet) return@LaunchedEffect
        delay(350)
        val bb = PoiLogic.bboxAround(previewAnchor.lat, previewAnchor.lon, MapMath.POI_VIEW_RADIUS_KM)
        val fetched = runCatching { fetchNearbyPois(overpass, bb, homeLang, destLang) }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            livePois = fetched
            poiFetchError = null
            applyFetchedPois(repo, region.id, fetched, previewAnchor.lat, previewAnchor.lon, homeCtx = false)
                ?.let { bundle = it }
        }
    }

    LaunchedEffect(atHomeForPoi, hasRealGpsFix, pos.lat, pos.lon) {
        if (!atHomeForPoi) return@LaunchedEffect
        homeCachePois = loadHomeCachePois(repo)
    }

    LaunchedEffect(region.id, region.downloadComplete) {
        while (true) {
            delay(6000)
            val fresh = withContext(Dispatchers.IO) { repo.loadRegionBundle(region.id) }
            if (fresh.pois.size != bundle.pois.size) bundle = fresh
            if (region.downloadComplete) break
        }
    }

    LaunchedEffect(atHomeForPoi, homeLive, hasRealGpsFix, pos.lat, pos.lon) {
        if (!atHomeForPoi && !homeLive) return@LaunchedEffect
        if (!TripNavigation.isAtHomeCountry(config, pos.lat, pos.lon)) return@LaunchedEffect
        val n = homeCacheTiles.loadRegion(HomeProgressiveDownloader.REGION_ID, force = true)
        if (n > 0) {
            tilesReady = true
            availableZooms = (
                tileStore.availableZooms() + homeCacheTiles.availableZooms() + RegionDownloadManager.ONLINE_ZOOMS
                ).ifEmpty { RegionDownloadManager.ONLINE_ZOOMS }
            tileCacheGeneration++
        }
        homeCachePois = loadHomeCachePois(repo)
    }

    LaunchedEffect(atHomeForPoi, homeLive, hasInternet, hasRealGpsFix, pos.lat, pos.lon) {
        if ((!atHomeForPoi && !homeLive) || !hasInternet || !hasRealGpsFix) return@LaunchedEffect
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

    val allPois = remember(bundle.pois, livePois, homeCachePois, atHomeForPoi) {
        val homePart = if (atHomeForPoi) homeCachePois else emptyList()
        (bundle.pois + livePois + homePart).distinctBy { it.id }
    }

    val cam = camera ?: MapCameraMath.defaultCamera(
        if (hasRealGpsFix && (onSite || homeLive)) pos else null,
        regionBbox,
        availableZooms,
        centerOnPos = hasRealGpsFix && (onSite || homeLive),
        heading = hasRealGpsFix && (onSite || homeLive),
    )

    val mapW = if (mapSize.width > 0) mapSize.width.toFloat() else 720f

    fun applyLocate() {
        when {
            useGpsAnchor && followGps && (onSite || homeLive) -> {
                if (gpsLocked) {
                    camera = cam.lockAnchor(navMapLat, navMapLon)
                } else {
                    camera = cam.recenter(pos.lat, pos.lon)
                }
            }
            useGpsAnchor -> camera = cam.recenter(pos.lat, pos.lon)
            previewAnchorLocked -> centerOnScheduleAnchor()
            else -> {
                val w = if (mapSize.width > 0) mapSize.width.toFloat() else 720f
                camera = MapCameraMath.cameraAt(previewAnchor.lat, previewAnchor.lon, availableZooms, screenW = w)
            }
        }
    }

    val useNavAnchor = headingModeActive && navAnchor.isReady
    val mapAnchorLat = when {
        useNavAnchor -> navMapLat
        useGpsAnchor -> pos.lat
        else -> previewAnchor.lat
    }
    val mapAnchorLon = when {
        useNavAnchor -> navMapLon
        useGpsAnchor -> pos.lon
        else -> previewAnchor.lon
    }
    val mapUser = UserPosition(
        mapAnchorLat,
        mapAnchorLon,
        bearingDeg = if (useGpsAnchor) headingDeg else null,
        speedMps = if (useGpsAnchor) pos.speedMps else 0f,
        simulated = !useGpsAnchor,
    )

    fun cycleRadarRadius() {
        radarRadiusM = MapMath.nextRadarRadiusM(radarRadiusM)
    }

    LaunchedEffect(homeLive) {
        if (!homeLive) return@LaunchedEffect
        val homeGraph = RoutingGraph.load(context, HomeProgressiveDownloader.REGION_ID)
        if (homeGraph.hasData) routingGraph = homeGraph
    }

    val radiusPois = remember(allPois, mapAnchorLat, mapAnchorLon, radarRadiusM) {
        if (radarRadiusM <= 0) {
            emptyList()
        } else {
            PoiLogic.withinRadiusKm(
                allPois,
                mapAnchorLat,
                mapAnchorLon,
                MapMath.radarRadiusKm(radarRadiusM),
            )
        }
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
        renderCam, radiusPois, mapSize, mapAnchorLat, mapAnchorLon, locationLocked,
    ) {
        if (mapSize.width <= 0) return@remember emptyList()
        val w = mapSize.width.toFloat()
        val h = mapSize.height.toFloat()
        val list = if (locationLocked) {
            // 위치 고정 — pan·줌과 무관하게 반경 내 POI 전부
            radiusPois
        } else {
            PoiLogic.visibleInViewport(
                radiusPois, renderCam, w, h, mapAnchorLat, mapAnchorLon,
            )
        }
        list.map { p ->
            val dist = PoiLogic.haversineM(mapAnchorLat, mapAnchorLon, p.lat, p.lon).toInt()
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
        radarRadiusM == 0 -> "반경 OFF — 원형 배지를 탭해 반경을 켜 주세요"
        !showRestaurant && !showHotel && !showSightseeing -> ui.emptyFilterKind
        allPois.isEmpty() && poiFetchError != null ->
            "명소 불러오기 실패 — WiFi 확인 후 잠시 기다려 주세요"
        allPois.isEmpty() && !hasInternet ->
            "WiFi·데이터 필요 — 연결 후 주변 명소가 표시됩니다"
        allPois.isEmpty() && hasInternet ->
            "주변 명소 불러오는 중…"
        kindFiltered.isNotEmpty() && filteredPois.isEmpty() -> ui.emptyStarFilter
        filteredPois.isEmpty() && radarRadiusM in 1..199 ->
            "반경 ${radarRadiusM}m — 배지를 탭해 범위를 넓혀 보세요"
        else -> ui.emptyNearby
    }

    fun localized(p: Poi): LocalizedPoi = PoiLocalization.forDisplay(p, homeLang, destLang)

    fun speakPoi(p: Poi) {
        scope.launch {
            val loc = localized(p)
            speech.speak(loc.ttsText, homeLocale) { ttsOk = false }
        }
    }

    LaunchedEffect(
        region.id,
        homeLive,
        atHomeForPoi,
        hasInternet,
        hasRealGpsFix,
        region.downloadComplete,
        region.lat,
        region.lon,
        pos.lat,
        pos.lon,
        config.homeLat,
        config.homeLon,
    ) {
        val homeRouting = region.id == MapPolicy.HOME_LIVE_ONLINE_ID ||
            homeLive ||
            atHomeForPoi ||
            TripNavigation.isHomeMapRegion(config, region)

        if (homeRouting) {
            val atHomeGps = hasRealGpsFix &&
                TripNavigation.isAtHomeCountry(config, pos.lat, pos.lon)
            val lat = when {
                atHomeGps -> pos.lat
                config.homeLat != 0.0 -> config.homeLat
                else -> region.lat
            }
            val lon = when {
                atHomeGps -> pos.lon
                config.homeLon != 0.0 -> config.homeLon
                else -> region.lon
            }
            val loaded = RoutingGraph.load(context, HomeProgressiveDownloader.REGION_ID)
            if (loaded.hasData) routingGraph = loaded
            if (hasInternet) {
                val ok = OnDemandRouting.ensureGraph(
                    context,
                    MapPolicy.HOME_LIVE_ONLINE_ID,
                    lat,
                    lon,
                    HomeProgressiveDownloader.HOME_RADIUS_KM,
                )
                if (ok) {
                    routingGraph = OnDemandRouting.reloadGraph(context, MapPolicy.HOME_LIVE_ONLINE_ID)
                }
            }
            return@LaunchedEffect
        }

        val graph = RoutingGraph.load(context, region.id)
        if (graph.hasData) {
            routingGraph = graph
            return@LaunchedEffect
        }
        val radiusKm = if (region.downloadComplete) STOP_DOWNLOAD_RADIUS_KM else MapMath.POI_VIEW_RADIUS_KM
        if (region.downloadComplete || hasInternet) {
            val built = OnDemandRouting.ensureGraph(context, region.id, region.lat, region.lon, radiusKm)
            if (built) {
                routingGraph = OnDemandRouting.reloadGraph(context, region.id)
            }
        }
    }

    LaunchedEffect(
        selectedPoiId,
        mapAnchorLat,
        mapAnchorLon,
        routingGraph,
        filteredPois,
        hasInternet,
        region.id,
        region.downloadComplete,
        homeLive,
        atHomeForPoi,
    ) {
        val target = selectedPoiId?.let { id -> filteredPois.find { it.id == id } }
        if (target == null) {
            routePoints = emptyList()
            routeDistanceM = null
            straightLineDistanceM = null
            return@LaunchedEffect
        }

        straightLineDistanceM = PoiLogic.haversineM(
            mapAnchorLat, mapAnchorLon, target.lat, target.lon,
        ).toInt()
        routePoints = emptyList()
        routeDistanceM = null

        val homeRouting = region.id == MapPolicy.HOME_LIVE_ONLINE_ID ||
            homeLive ||
            atHomeForPoi ||
            TripNavigation.isHomeMapRegion(config, region)
        val routingKey = if (homeRouting) MapPolicy.HOME_LIVE_ONLINE_ID else region.id
        val radiusKm = when {
            homeRouting -> HomeProgressiveDownloader.HOME_RADIUS_KM
            region.downloadComplete -> STOP_DOWNLOAD_RADIUS_KM
            else -> MapMath.POI_VIEW_RADIUS_KM
        }

        suspend fun loadGraph(): RoutingGraph? {
            var graph = routingGraph?.takeIf { it.hasData }
            if (graph == null) {
                graph = RoutingGraph.load(context, OnDemandRouting.regionKeyFor(routingKey))
                if (graph.hasData) routingGraph = graph
            }
            if (!graph.hasData && (hasInternet || region.downloadComplete || homeRouting)) {
                val built = OnDemandRouting.ensureGraph(
                    context, routingKey, mapAnchorLat, mapAnchorLon, radiusKm,
                )
                if (built) {
                    graph = OnDemandRouting.reloadGraph(context, routingKey)
                    routingGraph = graph
                }
            }
            return graph?.takeIf { it.hasData }
        }

        var g = loadGraph() ?: return@LaunchedEffect

        fun applyWalkRoute(graph: RoutingGraph): Boolean {
            val pts = graph.route(mapAnchorLat, mapAnchorLon, target.lat, target.lon)
            if (pts.size <= 2) return false
            routePoints = pts
            routeDistanceM = graph.routeLengthM(pts)
            return true
        }

        if (!applyWalkRoute(g)) {
            if (hasInternet && homeRouting) {
                if (OnDemandRouting.forceRebuildHomeGraph(context, mapAnchorLat, mapAnchorLon)) {
                    g = OnDemandRouting.reloadGraph(context, routingKey)
                    routingGraph = g
                    applyWalkRoute(g)
                }
            } else if (hasInternet) {
                val rebuilt = OnDemandRouting.ensureGraph(
                    context, routingKey, mapAnchorLat, mapAnchorLon, radiusKm,
                )
                if (rebuilt) {
                    g = OnDemandRouting.reloadGraph(context, routingKey)
                    routingGraph = g
                    applyWalkRoute(g)
                }
            }
        }
    }

    LaunchedEffect(selectedPoiId, filteredPois) {
        val id = selectedPoiId ?: return@LaunchedEffect
        val idx = filteredPois.indexOfFirst { it.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(
        containerColor = Color(0xFFF4F6F8),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false },
            ) {
            Box(
                Modifier
                    .weight(0.55f)
                    .fillMaxWidth()
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
                        gpsLocked = locationLocked,
                        targetBearingDeg = if (headingModeActive) targetHeadingDeg else null,
                        pois = filteredPois,
                        highlightedPoiId = selectedPoiId?.takeIf { id -> filteredPois.any { it.id == id } },
                        routePoints = routePoints,
                        routeDistanceM = routeDistanceM,
                        straightLinePoints = if (selectedPoiId != null && straightLineDistanceM != null) {
                            filteredPois.find { it.id == selectedPoiId }?.let { t ->
                                listOf(mapAnchorLat to mapAnchorLon, t.lat to t.lon)
                            } ?: emptyList()
                        } else {
                            emptyList()
                        },
                        straightLineDistanceM = straightLineDistanceM,
                        userRadiusKm = if (radarRadiusM > 0) {
                            MapMath.radarRadiusKm(radarRadiusM)
                        } else {
                            null
                        },
                        onGpsLockOff = { disableLocationLock() },
                        onPoiClick = { poi ->
                            selectedPoiId = if (selectedPoiId == poi.id) null else poi.id
                        },
                        tileCacheGeneration = tileCacheGeneration,
                        regionClipBbox = distantTileClipBbox,
                        scaleBarBottomInset = MapContentOverlapDp,
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
                    if (regionLoaded && homeLive && useOnlineMap) {
                        Text(
                            ui.homeLiveMode,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .padding(horizontal = 6.dp),
                            color = Color(0xFF15803D),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    } else if (regionLoaded && !useGpsAnchor) {
                        Text(
                            ui.previewMode,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = MapContentOverlapDp + 8.dp)
                                .padding(horizontal = 8.dp),
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    MapFullTopChrome(
                        title = mapHeaderTitle,
                        ui = ui,
                        radarRadiusM = radarRadiusM,
                        locationLocked = locationLocked,
                        lockContentDescription = lockContentDescription,
                        onOpenOptions = onOpenOptions,
                        onMainHub = onMainHub,
                        onToggleLocationLock = { toggleLocationLock() },
                        onCycleRadar = { cycleRadarRadius() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .mapTopChromeInset()
                            .zIndex(10f),
                    )
                }
            }

            Column(
                Modifier
                    .weight(0.45f)
                    .fillMaxWidth()
                    .offset(y = -MapContentOverlapDp)
                    .zIndex(2f)
                    .background(MapUiColors.sidePanelBg)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                            label = { Text(ui.restaurant, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PoiColors.restaurant.copy(alpha = 0.25f),
                            ),
                        )
                        FilterChip(
                            selected = showHotel,
                            onClick = { showHotel = !showHotel },
                            label = { Text(ui.hotel, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PoiColors.hotel.copy(alpha = 0.25f),
                            ),
                        )
                        FilterChip(
                            selected = showSightseeing,
                            onClick = { showSightseeing = !showSightseeing },
                            label = { Text(ui.sight, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp),
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
                Text(
                    ui.routeHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    if (filteredPois.isEmpty()) {
                        item { Text(emptyListMessage, color = Color.Gray, modifier = Modifier.padding(8.dp)) }
                    } else {
                        items(filteredPois, key = { it.id }) { p ->
                            val loc = localized(p)
                            PoiListItem(
                                poi = p,
                                displayName = loc.localName,
                                readingName = loc.readingName,
                                description = loc.description,
                                accent = PoiColors.accent(p),
                                typeLabel = loc.typeLabel,
                                routePrefix = ui.routePrefix,
                                speakLabel = ui.speak,
                                selected = p.id == selectedPoiId,
                                routeDistanceM = if (p.id == selectedPoiId) routeDistanceM else null,
                                straightDistanceM = if (p.id == selectedPoiId) straightLineDistanceM else null,
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
}

private val MapContentOverlapDp = 36.dp
private val MapChromeBtn = 36.dp
private val MapChromeBarH = 40.dp
private val MapChromeIcon = 22.dp

/** 지도 상단 한 줄 — 톱니·뒤로·지역명·반경·위치 고정 */
@Composable
private fun MapFullTopChrome(
    title: String,
    ui: MapUiStrings,
    radarRadiusM: Int,
    locationLocked: Boolean,
    lockContentDescription: String,
    onOpenOptions: () -> Unit,
    onMainHub: () -> Unit,
    onToggleLocationLock: () -> Unit,
    onCycleRadar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.heightIn(min = MapChromeBarH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenOptions, modifier = Modifier.size(MapChromeBtn)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = ui.options,
                tint = Color(0xFF334155),
                modifier = Modifier.size(MapChromeIcon),
            )
        }
        IconButton(onClick = onMainHub, modifier = Modifier.size(MapChromeBtn)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = ui.main,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(MapChromeIcon),
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(color = Color(0xCCFFFFFF), blurRadius = 4f),
            ),
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MapGpsRadiusChromeRow(
            radarRadiusM = radarRadiusM,
            onCycleRadar = onCycleRadar,
            gpsLocked = locationLocked,
            gpsInteractive = true,
            onToggleGpsLock = onToggleLocationLock,
            lockContentDescription = lockContentDescription,
        )
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
                Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, maxLines = 1)
            },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            },
            modifier = Modifier
                .height(28.dp)
                .onSizeChanged { chipSize = it }
                .widthIn(min = 72.dp),
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
