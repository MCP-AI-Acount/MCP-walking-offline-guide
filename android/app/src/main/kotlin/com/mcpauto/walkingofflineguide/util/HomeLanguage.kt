package com.mcpauto.walkingofflineguide.util

import java.util.Locale

/** 1단계 모국 선택 → 지도 패널 UI·TTS 언어 */
data class MapUiStrings(
    val restaurant: String,
    val hotel: String,
    val sight: String,
    val routeHint: String,
    val emptyNearby: String,
    val emptyFilterKind: String,
    val emptyStarFilter: String,
    val starAll: String,
    val recommendFilter: String,
    val routePrefix: String,
    val speak: String,
    val main: String,
    val reset: String,
    val options: String,
    val followGps: String,
    val followTrip: String,
    val previewStartHold: String,
    val tilesMissing: String,
    val previewMode: String,
    val previewRouteHint: String,
    val homeLiveMode: String,
)

object HomeLanguage {
    private val codeToLang = mapOf(
        "KR" to "ko", "KP" to "ko",
        "JP" to "ja",
        "CN" to "zh", "TW" to "zh-TW", "HK" to "zh-TW", "MO" to "zh-TW",
        "US" to "en", "GB" to "en", "AU" to "en", "CA" to "en", "NZ" to "en", "IE" to "en",
        "FR" to "fr", "BE" to "fr", "CH" to "de",
        "DE" to "de", "AT" to "de",
        "IT" to "it",
        "ES" to "es", "MX" to "es", "AR" to "es", "CO" to "es", "CL" to "es", "PE" to "es",
        "PT" to "pt", "BR" to "pt",
        "RU" to "ru", "UA" to "uk",
        "TH" to "th", "VN" to "vi", "ID" to "id",
        "TR" to "tr", "PL" to "pl", "NL" to "nl", "SE" to "sv", "NO" to "no", "DK" to "da",
        "FI" to "fi", "CZ" to "cs", "HU" to "hu", "RO" to "ro", "GR" to "el",
        "IN" to "hi", "SA" to "ar", "AE" to "ar", "EG" to "ar",
        "IL" to "he", "PH" to "en",
    )

    fun langTag(countryCode: String): String =
        codeToLang[countryCode.uppercase()] ?: "en"

    fun langTagFromCountryName(name: String): String {
        val n = name.trim().lowercase()
        return when {
            n.contains("korea") || n.contains("한국") -> "ko"
            n.contains("japan") || n.contains("일본") -> "ja"
            n.contains("china") || n.contains("중국") -> "zh"
            n.contains("taiwan") || n.contains("대만") -> "zh-TW"
            n.contains("france") || n.contains("프랑스") -> "fr"
            n.contains("germany") || n.contains("독일") -> "de"
            n.contains("italy") || n.contains("이탈리") -> "it"
            n.contains("spain") || n.contains("스페인") -> "es"
            n.contains("portugal") || n.contains("포르투갈") -> "pt"
            n.contains("russia") || n.contains("러시아") -> "ru"
            n.contains("thailand") || n.contains("태국") -> "th"
            n.contains("vietnam") || n.contains("베트남") -> "vi"
            n.contains("indonesia") || n.contains("인도네시아") -> "id"
            else -> "en"
        }
    }

    fun locale(countryCode: String): Locale {
        val tag = langTag(countryCode)
        return Locale.forLanguageTag(tag)
    }

    fun mapUi(countryCode: String): MapUiStrings = uiByLang[langTag(countryCode)] ?: uiEn

    fun poiTypeLabel(kind: String, tourism: String?, lang: String): String {
        val key = tourism?.takeIf { it.isNotBlank() } ?: kind
        return poiTypesByLang[lang]?.get(key)
            ?: poiTypesByLang[lang]?.get(kind)
            ?: poiTypesEn[key]
            ?: poiTypesEn[kind]
            ?: key
    }

    private val uiKo = MapUiStrings(
        restaurant = "식당", hotel = "숙소", sight = "명소",
        routeHint = "명소를 탭하면 도보 최단경로(파란 선)와 거리가 표시됩니다.",
        emptyNearby = "주변 장소 없음",
        emptyFilterKind = "표시할 종류를 하나 이상 켜 주세요.",
        emptyStarFilter = "선택한 추천도 이상 장소가 없습니다.",
        starAll = "전체",
        recommendFilter = "추천도",
        routePrefix = "경로",
        speak = "읽기", main = "메인", reset = "위치",
        options = "옵션",
        followGps = "현재 위치 고정", followTrip = "다음 여행지",
        previewStartHold = "시작 위치 고정",
        tilesMissing = "지도 타일 없음 — 여행 설정에서 다시 다운로드해 주세요.",
        previewMode = "시작 위치 기준 · 오프라인 지도",
        previewRouteHint = "현장 도착 후 도보 경로가 표시됩니다.",
        homeLiveMode = "모국 실시간 지도 (WiFi · GPS)",
    )
    private val uiEn = MapUiStrings(
        restaurant = "Restaurants", hotel = "Hotels", sight = "Sights",
        routeHint = "Tap a place to show the walking route (blue line) and distance.",
        emptyNearby = "No places nearby",
        emptyFilterKind = "Turn on at least one category.",
        emptyStarFilter = "No places match the rating filter.",
        starAll = "All",
        recommendFilter = "Rating",
        routePrefix = "Route",
        speak = "Listen", main = "Home", reset = "Locate",
        options = "Options",
        followGps = "Lock to GPS", followTrip = "Next trip stop",
        previewStartHold = "Lock to trip start",
        tilesMissing = "Map tiles missing — re-download from trip setup.",
        previewMode = "Downloaded area preview (not on-site)",
        previewRouteHint = "Walking routes appear when you arrive on-site.",
        homeLiveMode = "Home live map (WiFi · GPS)",
    )
    private val uiJa = uiEn.copy(
        restaurant = "レストラン", hotel = "宿泊", sight = "名所",
        routeHint = "場所をタップすると徒歩ルート（青線）と距離が表示されます。",
        emptyNearby = "周辺に場所がありません", routePrefix = "ルート",
        speak = "読み上げ", main = "ホーム", reset = "位置", options = "設定",
        followGps = "現在地固定", followTrip = "次の旅行先",
        previewStartHold = "出発地点固定",
    )
    private val uiZh = uiEn.copy(
        restaurant = "餐厅", hotel = "住宿", sight = "景点",
        routeHint = "点击地点可在地图上显示步行路线（蓝线）和距离。",
        emptyNearby = "附近没有地点", routePrefix = "路线",
        speak = "朗读", main = "主页", reset = "定位", options = "选项",
        followGps = "锁定GPS", followTrip = "下一行程",
        previewStartHold = "锁定出发位置",
    )
    private val uiFr = uiEn.copy(
        restaurant = "Restaurants", hotel = "Hôtels", sight = "Sites",
        routeHint = "Appuyez sur un lieu pour afficher l'itinéraire piéton (ligne bleue).",
        emptyNearby = "Aucun lieu à proximité", routePrefix = "Itinéraire",
        speak = "Écouter", main = "Accueil", reset = "Position", options = "Options",
        followGps = "GPS actuel", followTrip = "Prochaine étape",
        previewStartHold = "Fixer le départ",
    )
    private val uiDe = uiEn.copy(
        restaurant = "Restaurants", hotel = "Unterkünfte", sight = "Sehenswürdigkeiten",
        routeHint = "Tippen Sie auf einen Ort, um die Fußroute (blaue Linie) anzuzeigen.",
        emptyNearby = "Keine Orte in der Nähe", routePrefix = "Route",
        speak = "Vorlesen", main = "Start", reset = "Position", options = "Optionen",
        followGps = "GPS fixieren", followTrip = "Nächster Stopp",
        previewStartHold = "Startpunkt fixieren",
    )
    private val uiEs = uiEn.copy(
        restaurant = "Restaurantes", hotel = "Alojamientos", sight = "Lugares",
        routeHint = "Toque un lugar para ver la ruta a pie (línea azul) y la distancia.",
        emptyNearby = "No hay lugares cercanos", routePrefix = "Ruta",
        speak = "Escuchar", main = "Inicio", reset = "Ubicación", options = "Opciones",
        followGps = "Fijar GPS", followTrip = "Próxima parada",
        previewStartHold = "Fijar inicio del viaje",
    )
    private val uiIt = uiEn.copy(
        restaurant = "Ristoranti", hotel = "Alloggi", sight = "Attrazioni",
        routeHint = "Tocca un luogo per vedere il percorso a piedi (linea blu) e la distanza.",
        emptyNearby = "Nessun luogo nelle vicinanze", routePrefix = "Percorso",
        speak = "Ascolta", main = "Home", reset = "Posizione", options = "Opzioni",
        followGps = "Blocca GPS", followTrip = "Prossima tappa",
        previewStartHold = "Fissa punto di partenza",
    )

    private val uiByLang = mapOf(
        "ko" to uiKo, "en" to uiEn, "ja" to uiJa,
        "zh" to uiZh, "zh-TW" to uiZh,
        "fr" to uiFr, "de" to uiDe, "es" to uiEs, "it" to uiIt,
        "pt" to uiEs.copy(restaurant = "Restaurantes", hotel = "Hotéis", sight = "Locais",
            followGps = "Fixar GPS", followTrip = "Próxima parada"),
        "ru" to uiEn.copy(routeHint = "Нажмите на место, чтобы показать пеший маршрут (синяя линия)."),
        "th" to uiEn.copy(restaurant = "ร้านอาหาร", hotel = "ที่พัก", sight = "สถานที่"),
        "vi" to uiEn.copy(restaurant = "Nhà hàng", hotel = "Chỗ ở", sight = "Điểm tham quan"),
    )

    private val poiTypesEn = mapOf(
        "attraction" to "Sight", "viewpoint" to "Viewpoint", "museum" to "Museum",
        "gallery" to "Gallery", "monument" to "Monument", "castle" to "Castle",
        "ruins" to "Ruins", "hotel" to "Hotel", "guest_house" to "Guest house",
        "hostel" to "Hostel", "restaurant" to "Restaurant", "cafe" to "Café",
        "fast_food" to "Fast food", "bar" to "Bar",
    )

    private val poiTypesKo = mapOf(
        "attraction" to "관광명소", "viewpoint" to "전망대", "museum" to "박물관",
        "gallery" to "미술관", "hotel" to "숙소", "restaurant" to "식당", "cafe" to "카페",
    )

    private val poiTypesJa = mapOf(
        "attraction" to "観光地", "museum" to "博物館", "hotel" to "宿泊", "restaurant" to "レストラン",
    )

    private val poiTypesZh = mapOf(
        "attraction" to "景点", "museum" to "博物馆", "hotel" to "住宿", "restaurant" to "餐厅",
    )

    private val poiTypesByLang = mapOf(
        "ko" to poiTypesKo, "en" to poiTypesEn, "ja" to poiTypesJa, "zh" to poiTypesZh, "zh-TW" to poiTypesZh,
        "fr" to poiTypesEn, "de" to poiTypesEn, "es" to poiTypesEn, "it" to poiTypesEn,
    )
}
