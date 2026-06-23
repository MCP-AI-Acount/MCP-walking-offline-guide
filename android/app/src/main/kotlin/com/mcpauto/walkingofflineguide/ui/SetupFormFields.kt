package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.RectangleShape
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

fun formatMonthDayDigitsInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(4)
    return when {
        digits.length <= 2 -> digits
        else -> "${digits.take(2)}-${digits.drop(2)}"
    }
}

fun formatMonthDay(epochDay: Long): String {
    if (epochDay <= 0) return ""
    val d = LocalDate.ofEpochDay(epochDay)
    return "%02d-%02d".format(d.monthValue, d.dayOfMonth)
}

fun parseMonthDay(text: String, year: Int): LocalDate? {
    val cleaned = text.trim().replace('.', '-').replace('/', '-')
    val parts = cleaned.split("-").filter { it.isNotBlank() }
    if (parts.size != 2) return null
    val month = parts[0].toIntOrNull() ?: return null
    val day = parts[1].toIntOrNull() ?: return null
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

fun tripYearFromEpoch(tripStartEpochDay: Long): Int =
    if (tripStartEpochDay > 0) LocalDate.ofEpochDay(tripStartEpochDay).year else LocalDate.now().year

@Composable
private fun SetupLayerFrame(
    title: String,
    borderColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RectangleShape)
            .background(backgroundColor, RectangleShape)
            .padding(12.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = borderColor)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RectDateCell(
    label: String,
    display: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF334155),
) {
    Column(
        modifier
            .border(1.dp, accent.copy(alpha = 0.35f), RectangleShape)
            .background(Color.White, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
        Text(display, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TripDateRangeRow(
    startEpochDay: Long,
    endEpochDay: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun showPicker(current: Long, onPick: (Long) -> Unit) {
        val base = if (current > 0) LocalDate.ofEpochDay(current) else LocalDate.now()
        DatePickerDialog(
            context,
            { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d).toEpochDay()) },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth,
        ).apply {
            datePicker.calendarViewShown = false
            @Suppress("DEPRECATION")
            datePicker.spinnersShown = true
        }.show()
    }
    val startDisplay = if (startEpochDay > 0) PoiLogic.formatDate(startEpochDay) else "선택"
    val endDisplay = if (endEpochDay > 0) PoiLogic.formatDate(endEpochDay) else "선택"
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RectDateCell(
            label = "출발일",
            display = startDisplay,
            onClick = {
                showPicker(startEpochDay) { start ->
                    onStartChange(start)
                    if (endEpochDay > 0 && endEpochDay < start) onEndChange(start)
                }
            },
            modifier = Modifier.weight(1f),
            accent = Color(0xFF4F46E5),
        )
        RectDateCell(
            label = "귀국일",
            display = endDisplay,
            onClick = {
                showPicker(endEpochDay.coerceAtLeast(startEpochDay.takeIf { it > 0 } ?: endEpochDay)) { end ->
                    val start = startEpochDay.takeIf { it > 0 } ?: LocalDate.now().toEpochDay()
                    onEndChange(end.coerceAtLeast(start))
                }
            },
            modifier = Modifier.weight(1f),
            accent = Color(0xFF4F46E5),
        )
    }
}

@Composable
fun LegMonthDayRangeRow(
    tripYear: Int,
    tripStartBound: Long,
    tripEndBound: Long,
    startEpochDay: Long,
    endEpochDay: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun showPicker(current: Long, onPick: (Long) -> Unit) {
        val base = if (current > 0) LocalDate.ofEpochDay(current) else LocalDate.of(tripYear, 1, 1)
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val picked = LocalDate.of(tripYear, m + 1, d).toEpochDay()
                onPick(picked)
            },
            tripYear,
            base.monthValue - 1,
            base.dayOfMonth,
        ).apply {
            datePicker.calendarViewShown = false
            @Suppress("DEPRECATION")
            datePicker.spinnersShown = true
        }.show()
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthDayField(
            epochDay = startEpochDay,
            tripYear = tripYear,
            label = "시작 (월-일)",
            onEpochDayChange = { start ->
                onStartChange(start)
                if (endEpochDay > 0 && endEpochDay < start) onEndChange(start)
            },
            onOpenPicker = { showPicker(startEpochDay, onStartChange) },
            modifier = Modifier.weight(1f),
            accent = Color(0xFF0F766E),
        )
        MonthDayField(
            epochDay = endEpochDay.coerceAtLeast(startEpochDay.takeIf { it > 0 } ?: endEpochDay),
            tripYear = tripYear,
            label = "종료 (월-일)",
            onEpochDayChange = { end ->
                val start = startEpochDay.takeIf { it > 0 } ?: LocalDate.of(tripYear, 1, 1).toEpochDay()
                onEndChange(end.coerceAtLeast(start))
            },
            onOpenPicker = {
                showPicker(endEpochDay.coerceAtLeast(startEpochDay.takeIf { it > 0 } ?: endEpochDay), onEndChange)
            },
            modifier = Modifier.weight(1f),
            accent = Color(0xFF0F766E),
        )
    }
    if (tripStartBound > 0 && tripEndBound >= tripStartBound) {
        Text(
            "여행 ${formatMonthDay(tripStartBound)}~${formatMonthDay(tripEndBound)} (${tripYear}년) 안에서만 선택",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MonthDayField(
    epochDay: Long,
    tripYear: Int,
    label: String,
    onEpochDayChange: (Long) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF0F766E),
) {
    var text by remember(epochDay, tripYear) { mutableStateOf(formatMonthDay(epochDay)) }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val formatted = formatMonthDayDigitsInput(raw)
                text = formatted
                parseMonthDay(formatted, tripYear)?.let { onEpochDayChange(it.toEpochDay()) }
            },
            label = { Text(label) },
            placeholder = { Text("06-25") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RectangleShape,
        )
        Text(
            "달력",
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(top = 2.dp)
                .clickable(onClick = onOpenPicker),
        )
    }
}

@Composable
fun TripOverviewPanel(
    arrivalAirport: CityPoint,
    onAirportChange: (CityPoint) -> Unit,
    tripStartEpochDay: Long,
    tripEndEpochDay: Long,
    onTripStartChange: (Long) -> Unit,
    onTripEndChange: (Long) -> Unit,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    modifier: Modifier = Modifier,
) {
    SetupLayerFrame(
        title = "여행 개요",
        borderColor = Color(0xFF4F46E5),
        backgroundColor = Color(0xFFEEF2FF),
        modifier = modifier,
    ) {
        CityConfirmField(
            point = arrivalAirport,
            onDraftChange = {},
            onConfirm = onAirportChange,
            catalog = catalog,
            geocoder = geocoder,
            countryHint = "",
            label = "입국 공항",
            placeholder = "공항명·IATA (예: Marco Polo, VCE)",
        )
        Text(
            "여행 기간 (연도 포함)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        TripDateRangeRow(
            startEpochDay = tripStartEpochDay,
            endEpochDay = tripEndEpochDay,
            onStartChange = onTripStartChange,
            onEndChange = onTripEndChange,
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
    tripYear: Int,
    tripStartBound: Long,
    tripEndBound: Long,
    countryHint: String,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    onLegChange: (ScheduleLeg) -> Unit,
    onDeleteLeg: (() -> Unit)? = null,
    canDelete: Boolean = true,
    cityPlaceholder: String? = null,
) {
    val legBorder = if (leg.legConfirmed) Color(0xFF15803D) else Color(0xFFCA8A04)
    val legBg = if (leg.legConfirmed) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
    SetupLayerFrame(
        title = "구간 ${legIndex + 1}",
        borderColor = legBorder,
        backgroundColor = legBg,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    Icon(Icons.Default.Close, contentDescription = "구간 삭제", tint = Color(0xFFDC2626))
                }
            }
        }

        LegMonthDayRangeRow(
            tripYear = tripYear,
            tripStartBound = tripStartBound,
            tripEndBound = tripEndBound,
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

/** 1단계 — 도보 구간(leg) 목록 */
@Composable
fun ScheduleLegsVerticalList(
    legs: List<ScheduleLeg>,
    tripYear: Int,
    tripStartBound: Long,
    tripEndBound: Long,
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
        if (legs.size > 0) {
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
                if (legs.isEmpty()) "도보 구간" else "도보 구간 · ${legs.size}개",
                fontWeight = FontWeight.SemiBold,
                color = AppMenuStyle.text,
            )
            OutlinedButton(onClick = onAddLeg, shape = RectangleShape) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("구간 추가", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Text(
            if (legs.isEmpty()) {
                "「구간 추가」로 출발→경유→도착 도보 코스를 넣어 주세요."
            } else {
                "구간마다 출발→경유→도착. 날짜는 월-일만 입력 (연도는 여행 개요 기준)."
            },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            legs.forEachIndexed { idx, leg ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { clip = false },
                ) {
                    LegRouteEditor(
                        leg = leg,
                        legIndex = idx,
                        tripYear = tripYear,
                        tripStartBound = tripStartBound,
                        tripEndBound = tripEndBound,
                        countryHint = destCountry,
                        catalog = catalog,
                        geocoder = geocoder,
                        onLegChange = { updated -> onLegChange(idx, updated) },
                        onDeleteLeg = { onDeleteLeg(idx) },
                        canDelete = true,
                        cityPlaceholder = cityPlaceholder,
                    )
                    if (isLegReady(leg) && !leg.legConfirmed) {
                        Button(
                            onClick = { onLegChange(idx, leg.copy(legConfirmed = true)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            shape = RectangleShape,
                        ) { Text("이 구간 확정") }
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
