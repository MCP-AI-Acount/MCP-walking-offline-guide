package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import com.mcpauto.walkingofflineguide.data.defaultScheduleLeg
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import java.time.LocalDate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpauto.walkingofflineguide.data.CityPoint
import com.mcpauto.walkingofflineguide.data.CountryEntry
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.network.GeoResult
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.util.countryFlagEmoji
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SuggestionRowHeight = 48.dp
private const val SuggestionMaxVisibleRows = 3

/** U3-Autocomplete — 입력·드롭다운·스크롤 한 세트. @see UX_CONVENIENCE_CANON.md § U3-Autocomplete */
@Composable
private fun <T> GeoSuggestionDropdownList(
    items: List<T>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    label: (T) -> String,
    onItemClick: (T) -> Unit,
) {
    if (items.isEmpty()) return
    val visibleRows = minOf(items.size, SuggestionMaxVisibleRows).coerceAtLeast(1)
    val listHeight = SuggestionRowHeight * visibleRows
    val listState = rememberLazyListState()
    val canScroll = items.size > SuggestionMaxVisibleRows

    Column(modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight),
            ) {
                itemsIndexed(items) { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    }
                    GeoSuggestionRow(
                        text = label(item),
                        onClick = { onItemClick(item) },
                        textColor = textColor,
                    )
                }
            }
        }
        if (canScroll) {
            Text(
                "${items.size}개 — 아래로 스크롤",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun GeoSuggestionRow(
    text: String,
    onClick: () -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SuggestionRowHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun formatDateDigitsInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(8)
    return when {
        digits.length <= 4 -> digits
        digits.length <= 6 -> "${digits.take(4)}-${digits.drop(4)}"
        else -> "${digits.take(4)}-${digits.substring(4, 6)}-${digits.drop(6)}"
    }
}

@Composable
fun DateWheelField(
    epochDay: Long,
    onEpochDayChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val display = if (epochDay > 0) PoiLogic.formatDate(epochDay) else PoiLogic.formatDate(LocalDate.now().toEpochDay())
    OutlinedButton(
        onClick = {
            val base = if (epochDay > 0) LocalDate.ofEpochDay(epochDay) else LocalDate.now()
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    onEpochDayChange(LocalDate.of(y, m + 1, d).toEpochDay())
                },
                base.year,
                base.monthValue - 1,
                base.dayOfMonth,
            ).apply {
                datePicker.calendarViewShown = false
                @Suppress("DEPRECATION")
                datePicker.spinnersShown = true
            }.show()
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text("$label: $display")
    }
}

@Composable
fun LegDateRangeRow(
    startEpochDay: Long,
    endEpochDay: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        DateWheelField(
            epochDay = startEpochDay,
            onEpochDayChange = { start ->
                onStartChange(start)
                if (endEpochDay > 0 && endEpochDay < start) onEndChange(start)
            },
            label = "시작일",
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        DateWheelField(
            epochDay = endEpochDay.coerceAtLeast(startEpochDay.takeIf { it > 0 } ?: endEpochDay),
            onEpochDayChange = { end ->
                val start = startEpochDay.takeIf { it > 0 } ?: LocalDate.now().toEpochDay()
                onEndChange(end.coerceAtLeast(start))
            },
            label = "종료일",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DateMaskField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(formatDateDigitsInput(it)) },
        label = { Text(label) },
        placeholder = { Text("2026-06-25") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
/** U3-Autocomplete — 터치 후 수정 + 드롭다운·스크롤 한 세트. @see UX_CONVENIENCE_CANON.md § U3-Autocomplete */
fun CountryAutocompleteField(
    value: String,
    confirmedCode: String?,
    onValueChange: (String) -> Unit,
    onCountryConfirmed: (CountryEntry?) -> Unit,
    catalog: GeoCatalog,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value) { catalog.searchCountries(value) }
    val flag = confirmedCode?.let { countryFlagEmoji(it) }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                onCountryConfirmed(null)
                expanded = it.isNotBlank()
            },
            label = { Text(label) },
            placeholder = placeholder?.let { hint ->
                { Text(hint, color = Color(0xFF94A3B8)) }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (flag != null) {
                    Text(flag, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                }
            },
        )
        if (expanded && suggestions.isNotEmpty()) {
            GeoSuggestionDropdownList(
                items = suggestions,
                label = { c -> "${c.nameKo} (${c.nameEn})" },
                onItemClick = { c ->
                    onValueChange(c.nameKo.ifBlank { c.nameEn })
                    onCountryConfirmed(c)
                    expanded = false
                },
            )
        }
    }
}

@Composable
/** U3-Autocomplete — 확정 후 입력창 탭으로 재편집 · 내장/온라인 목록은 GeoSuggestionDropdownList 한 세트. */
fun CityConfirmField(
    point: CityPoint,
    onDraftChange: (String) -> Unit,
    onConfirm: (CityPoint) -> Unit,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    countryHint: String,
    label: String,
    placeholder: String? = null,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var pendingEditFocus by remember { mutableStateOf(false) }
    var draft by remember(point.name, point.confirmed) { mutableStateOf(point.name) }
    var confirming by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var onlineSuggestions by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var searchingOnline by remember { mutableStateOf(false) }
    val localSuggestions = remember(draft, countryHint, point.confirmed) {
        if (point.confirmed || draft.length < 1) emptyList() else catalog.searchCities(draft, countryHint)
    }

    LaunchedEffect(pendingEditFocus, point.confirmed) {
        if (pendingEditFocus && !point.confirmed) {
            pendingEditFocus = false
            delay(80)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    fun beginEdit() {
        draft = point.name
        err = ""
        pendingEditFocus = true
        onConfirm(point.copy(confirmed = false))
    }

    LaunchedEffect(draft, countryHint, point.confirmed) {
        onlineSuggestions = emptyList()
        if (point.confirmed || draft.trim().length < 2) return@LaunchedEffect
        delay(450)
        searchingOnline = true
        onlineSuggestions = runCatching {
            geocoder.searchSuggestions(draft.trim(), countryHint, limit = 8)
        }.getOrDefault(emptyList())
        searchingOnline = false
    }

    fun applyGeoResult(geo: GeoResult) {
        onConfirm(
            CityPoint(
                name = geo.displayName.split(",").take(2).joinToString(", ").ifBlank { geo.name },
                lat = geo.lat,
                lon = geo.lon,
                confirmed = true,
            ),
        )
        draft = geo.name
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = if (point.confirmed) point.name else draft,
                onValueChange = {
                    if (!point.confirmed) {
                        draft = it
                        onDraftChange(it)
                        err = ""
                    }
                },
                label = {
                    Text(if (point.confirmed) "✓ $label" else label)
                },
                placeholder = {
                    Text(
                        placeholder ?: "2글자 이상 — 도시·거리·장소 검색",
                        color = Color(0xFF94A3B8),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                readOnly = point.confirmed,
                singleLine = true,
            )
            if (point.confirmed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { beginEdit() },
                )
            }
        }

        if (!point.confirmed && localSuggestions.isNotEmpty()) {
            Text(
                "내장 목록",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp),
            )
            GeoSuggestionDropdownList(
                items = localSuggestions,
                textColor = Color(0xFF2563EB),
                label = { c -> "${c.name} · ${c.country}" },
                onItemClick = { c ->
                    draft = c.name
                    onDraftChange(c.name)
                    onConfirm(CityPoint(name = c.name, lat = c.lat, lon = c.lon, confirmed = true))
                },
            )
        }

        if (!point.confirmed && (searchingOnline || onlineSuggestions.isNotEmpty())) {
            Text(
                if (searchingOnline) "인터넷 검색 중…" else "인터넷 검색",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (onlineSuggestions.isNotEmpty()) {
                GeoSuggestionDropdownList(
                    items = onlineSuggestions,
                    textColor = Color(0xFF0F766E),
                    label = { geo -> geo.displayName },
                    onItemClick = { geo -> applyGeoResult(geo) },
                )
            }
        }

        if (!point.confirmed) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            confirming = true
                            err = ""
                            val name = draft.trim()
                            if (name.isBlank()) {
                                err = "장소명을 입력해 주세요"
                                confirming = false
                                return@launch
                            }
                            val local = catalog.resolveCity(name, countryHint)
                            val resolved = if (local != null) {
                                CityPoint(name = local.name, lat = local.lat, lon = local.lon, confirmed = true)
                            } else {
                                val geo = geocoder.search(name, countryHint)
                                if (geo == null) {
                                    err = "장소를 찾을 수 없습니다. 목록에서 선택해 주세요."
                                    confirming = false
                                    return@launch
                                }
                                CityPoint(
                                    name = geo.displayName.split(",").take(2).joinToString(", ").ifBlank { geo.name },
                                    lat = geo.lat,
                                    lon = geo.lon,
                                    confirmed = true,
                                )
                            }
                            onConfirm(resolved)
                            confirming = false
                        }
                    },
                    enabled = !confirming,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (confirming) "확인 중…" else "확인") }
            }
        }
        if (err.isNotBlank()) {
            Text(err, color = Color(0xFFDC2626), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun LegRouteEditor(
    leg: ScheduleLeg,
    legIndex: Int,
    countryHint: String,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    onLegChange: (ScheduleLeg) -> Unit,
    onDeleteLeg: (() -> Unit)? = null,
    canDelete: Boolean = true,
    cityPlaceholder: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (leg.legConfirmed) Color(0xFFEFF6FF) else Color(0xFFFAFAFA),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("일정 ${legIndex + 1}", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leg.legConfirmed) {
                        Text(
                            "확정됨",
                            color = Color(0xFF15803D),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (canDelete && onDeleteLeg != null) {
                        IconButton(onClick = onDeleteLeg) {
                            Icon(Icons.Default.Close, contentDescription = "일정 삭제", tint = Color(0xFFDC2626))
                        }
                    }
                }
            }

            LegDateRangeRow(
                startEpochDay = leg.startEpochDay,
                endEpochDay = leg.endEpochDay,
                onStartChange = { d -> onLegChange(leg.copy(startEpochDay = d, legConfirmed = false)) },
                onEndChange = { d -> onLegChange(leg.copy(endEpochDay = d, legConfirmed = false)) },
                modifier = Modifier.padding(bottom = 8.dp),
            )

            CityConfirmField(
                point = leg.startPoint,
                onDraftChange = {},
                onConfirm = { p -> onLegChange(leg.copy(startPoint = p, legConfirmed = false)) },
                catalog = catalog,
                geocoder = geocoder,
                countryHint = countryHint,
                label = "도보 출발지 (도시·거리·장소)",
                placeholder = cityPlaceholder,
            )

            leg.waypoints.forEachIndexed { wi, wp ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "경유 ${wi + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        CityConfirmField(
                            point = wp,
                            onDraftChange = {},
                            onConfirm = { p ->
                                val list = leg.waypoints.toMutableList()
                                list[wi] = p
                                onLegChange(leg.copy(waypoints = list, legConfirmed = false))
                            },
                            catalog = catalog,
                            geocoder = geocoder,
                            countryHint = countryHint,
                            label = "경유지 (도시·거리·장소)",
                            placeholder = cityPlaceholder,
                        )
                    }
                    IconButton(
                        onClick = {
                            onLegChange(
                                leg.copy(
                                    waypoints = leg.waypoints.filterIndexed { i, _ -> i != wi },
                                    legConfirmed = false,
                                ),
                            )
                        },
                        modifier = Modifier.padding(top = 24.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "경유지 삭제")
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    onLegChange(leg.copy(waypoints = leg.waypoints + CityPoint(), legConfirmed = false))
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("경유지 추가", modifier = Modifier.padding(start = 6.dp))
            }

            CityConfirmField(
                point = leg.endPoint,
                onDraftChange = {},
                onConfirm = { p -> onLegChange(leg.copy(endPoint = p, legConfirmed = false)) },
                catalog = catalog,
                geocoder = geocoder,
                countryHint = countryHint,
                label = "도보 도착지 (도시·거리·장소)",
                placeholder = cityPlaceholder,
            )
        }
    }
}

/** 1단계 — 일정(leg) 카드 세로 목록: 전체 너비, 경유지는 카드 안 세로 */
@Composable
fun ScheduleLegsVerticalList(
    legs: List<ScheduleLeg>,
    destCountry: String,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    onLegChange: (Int, ScheduleLeg) -> Unit,
    onAddLeg: () -> Unit,
    onDeleteLeg: (Int) -> Unit,
    modifier: Modifier = Modifier,
    cityPlaceholder: String? = null,
) {
    val listScroll = rememberScrollState()
    LaunchedEffect(legs.size) {
        if (legs.size > 1) {
            listScroll.animateScrollTo(listScroll.maxValue)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "도시 일정 · ${legs.size}개",
                fontWeight = FontWeight.SemiBold,
                color = AppMenuStyle.text,
            )
            OutlinedButton(onClick = onAddLeg) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("일정 추가", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Text(
            "일정마다 출발→경유→도착. 경유지는 아래로 추가됩니다.",
            style = MaterialTheme.typography.labelSmall,
            color = AppMenuStyle.muted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(listScroll)
                .graphicsLayer { clip = false },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            legs.forEachIndexed { idx, leg ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false }
                        .background(AppMenuStyle.card, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                ) {
                    LegRouteEditor(
                        leg = leg,
                        legIndex = idx,
                        countryHint = destCountry,
                        catalog = catalog,
                        geocoder = geocoder,
                        onLegChange = { updated -> onLegChange(idx, updated) },
                        onDeleteLeg = { onDeleteLeg(idx) },
                        canDelete = legs.size > 1 || leg.startPoint.name.isNotBlank() ||
                            leg.endPoint.name.isNotBlank() || leg.waypoints.any { it.name.isNotBlank() },
                        cityPlaceholder = cityPlaceholder,
                    )
                    if (isLegReady(leg) && !leg.legConfirmed) {
                        Button(
                            onClick = { onLegChange(idx, leg.copy(legConfirmed = true)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        ) { Text("이 일정 확정") }
                    }
                }
            }
        }
    }
}

fun isLegReady(leg: ScheduleLeg): Boolean =
    leg.startEpochDay > 0 && leg.endEpochDay >= leg.startEpochDay &&
        leg.startPoint.confirmed && leg.endPoint.confirmed &&
        leg.waypoints.all { it.name.isBlank() || it.confirmed }

fun buildStopsFromLegs(legs: List<ScheduleLeg>): List<com.mcpauto.walkingofflineguide.data.CityStop> {
    val out = linkedMapOf<String, com.mcpauto.walkingofflineguide.data.CityStop>()
    legs.filter { it.legConfirmed }.forEach { leg ->
        listOf(leg.startPoint).plus(leg.waypoints).plus(leg.endPoint)
            .filter { it.confirmed && it.name.isNotBlank() }
            .forEach { p ->
                out[p.name] = com.mcpauto.walkingofflineguide.data.CityStop(
                    name = p.name,
                    lat = p.lat,
                    lon = p.lon,
                    radiusKm = com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM,
                )
            }
    }
    return out.values.toList()
}

fun scheduleSummary(legs: List<ScheduleLeg>, destCountry: String): String =
    legs.filter { it.legConfirmed }.mapIndexed { i, leg ->
        val via = leg.waypoints.filter { it.confirmed }.joinToString(" → ") { it.name }
        val route = buildString {
            append(leg.startPoint.name)
            if (via.isNotBlank()) append(" → $via")
            append(" → ${leg.endPoint.name}")
        }
        "일정 ${i + 1} [$destCountry]: $route"
    }.joinToString("\n")
