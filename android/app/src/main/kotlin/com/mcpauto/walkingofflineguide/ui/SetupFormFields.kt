package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import android.content.Context
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.mcpauto.walkingofflineguide.data.defaultScheduleLeg
import com.mcpauto.walkingofflineguide.logic.PoiLogic
import java.time.LocalDate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcpauto.walkingofflineguide.data.CityPoint
import com.mcpauto.walkingofflineguide.data.CountryEntry
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.network.GeoResult
import com.mcpauto.walkingofflineguide.network.LocalAdminGeocoder
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import com.mcpauto.walkingofflineguide.network.adminPlaceLabel
import com.mcpauto.walkingofflineguide.util.HomeLanguage
import com.mcpauto.walkingofflineguide.util.countryFlagEmoji
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SuggestionRowHeight = 48.dp
private const val SuggestionMaxVisibleRows = 3
private val CompactDateLabelSp = 9.sp
private val CompactDateTextSp = 11.sp
private val CompactFieldLabelSp = 9.sp
private val CompactFieldTextSp = 12.sp

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
            colors = CardDefaults.cardColors(containerColor = AppMenuStyle.scroll),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, AppMenuStyle.scrollBorder),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight),
            ) {
                itemsIndexed(items) { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = AppMenuStyle.scrollBorder, thickness = 1.dp)
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
    title: String?,
    borderColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    titleTrailing: (@Composable () -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RectangleShape)
            .background(backgroundColor, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (title != null || titleTrailing != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = borderColor)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                titleTrailing?.invoke()
            }
            Spacer(Modifier.height(4.dp))
        }
        content()
    }
}

@Composable
private fun OutlineDateField(
    label: String,
    display: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        label = { Text(label, fontSize = CompactDateLabelSp) },
        placeholder = { Text("선택", color = Color(0xFFCBD5E1), fontSize = CompactDateTextSp) },
        textStyle = TextStyle(fontSize = CompactDateTextSp, lineHeight = 14.sp),
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .fillMaxWidth()
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            },
        singleLine = true,
        shape = RectangleShape,
        trailingIcon = {
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "달력",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
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
            datePicker.calendarViewShown = true
        }.show()
    }
    val startDisplay = if (startEpochDay > 0) PoiLogic.formatDate(startEpochDay) else "선택"
    val endDisplay = if (endEpochDay > 0) PoiLogic.formatDate(endEpochDay) else "선택"
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlineDateField(
            label = "출국일",
            display = startDisplay,
            onClick = {
                showPicker(startEpochDay) { start ->
                    onStartChange(start)
                    if (endEpochDay > 0 && endEpochDay < start) onEndChange(start)
                }
            },
            modifier = Modifier.weight(1f),
        )
        OutlineDateField(
            label = "귀국일",
            display = endDisplay,
            onClick = {
                showPicker(endEpochDay.coerceAtLeast(startEpochDay.takeIf { it > 0 } ?: endEpochDay)) { end ->
                    val start = startEpochDay.takeIf { it > 0 } ?: LocalDate.now().toEpochDay()
                    onEndChange(end.coerceAtLeast(start))
                }
            },
            modifier = Modifier.weight(1f),
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
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
) {
    var text by remember(epochDay, tripYear) { mutableStateOf(formatMonthDay(epochDay)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val formatted = formatMonthDayDigitsInput(raw)
            text = formatted
            parseMonthDay(formatted, tripYear)?.let { onEpochDayChange(it.toEpochDay()) }
        },
        label = { Text(label) },
        placeholder = { Text("06-25", color = Color(0xFFCBD5E1)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RectangleShape,
        trailingIcon = {
            IconButton(onClick = onOpenPicker) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "달력",
                    tint = Color(0xFF64748B),
                )
            }
        },
    )
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
        title = null,
        borderColor = AppMenuStyle.tripOverviewBorder,
        backgroundColor = AppMenuStyle.tripOverviewBg,
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
            fieldPaddingVertical = 2.dp,
        )
        TripDateRangeRow(
            startEpochDay = tripStartEpochDay,
            endEpochDay = tripEndEpochDay,
            onStartChange = onTripStartChange,
            onEndChange = onTripEndChange,
            modifier = Modifier.padding(top = 4.dp),
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
    fieldPaddingVertical: androidx.compose.ui.unit.Dp = 2.dp,
    onRemove: (() -> Unit)? = null,
    onGlobeClick: (() -> Unit)? = null,
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

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = fieldPaddingVertical),
    ) {
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
                label = { Text(label, fontSize = CompactFieldLabelSp) },
                placeholder = {
                    Text(
                        placeholder ?: "도시·거리·장소",
                        color = Color(0xFFCBD5E1),
                        fontSize = CompactFieldTextSp,
                    )
                },
                textStyle = TextStyle(fontSize = CompactFieldTextSp, lineHeight = 15.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .focusRequester(focusRequester),
                readOnly = point.confirmed,
                singleLine = true,
                shape = RectangleShape,
                trailingIcon = {
                    if (onGlobeClick != null || onRemove != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onGlobeClick != null) {
                                IconButton(
                                    onClick = onGlobeClick,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Text("🌍", fontSize = 16.sp)
                                }
                            }
                            if (onRemove != null) {
                                IconButton(
                                    onClick = onRemove,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "$label 삭제",
                                        tint = Color(0xFF1E293B),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                },
            )
            if (point.confirmed) {
                val trailingPad = when {
                    onGlobeClick != null && onRemove != null -> 72.dp
                    onGlobeClick != null || onRemove != null -> 40.dp
                    else -> 0.dp
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(end = trailingPad)
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
    arrivalAirport: CityPoint,
    homeCountryCode: String,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    onLegChange: (ScheduleLeg) -> Unit,
    onDeleteLeg: (() -> Unit)? = null,
    canDelete: Boolean = true,
    cityPlaceholder: String? = null,
) {
    val legBorder = if (leg.legConfirmed) Color(0xFF15803D) else Color(0xFFCA8A04)
    val legBg = if (leg.legConfirmed) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
    var mapPickSlot by remember { mutableStateOf<LegMapPickSlot?>(null) }

    fun airportCenter(): Pair<Double, Double> =
        if (arrivalAirport.confirmed && arrivalAirport.lat != 0.0) {
            arrivalAirport.lat to arrivalAirport.lon
        } else {
            catalog.resolveCountry(countryHint)?.let { it.lat to it.lon } ?: (45.0 to 12.0)
        }

    val (mapCenterLat, mapCenterLon) = airportCenter()

    mapPickSlot?.let { slot ->
        val ctx = legMapPickContext(
            slot = slot,
            leg = leg,
            arrivalAirport = arrivalAirport,
            fallbackCenter = mapCenterLat to mapCenterLon,
        )
        SetupMapPointPickerDialog(
            visible = true,
            centerLat = ctx.centerLat,
            centerLon = ctx.centerLon,
            initialPickLat = ctx.initialPickLat,
            initialPickLon = ctx.initialPickLon,
            referenceLabel = ctx.referenceLabel,
            referenceIsAirport = ctx.referenceIsAirport,
            routeMarkers = legRouteMarkers(leg, arrivalAirport),
            countryHint = countryHint,
            homeCountryCode = homeCountryCode,
            geocoder = geocoder,
            onDismiss = { mapPickSlot = null },
            onPicked = { cp ->
                mapPickSlot = null
                when (slot) {
                    LegMapPickSlot.Start -> onLegChange(leg.copy(startPoint = cp, legConfirmed = false))
                    LegMapPickSlot.End -> onLegChange(leg.copy(endPoint = cp, legConfirmed = false))
                    is LegMapPickSlot.Waypoint -> {
                        val list = leg.waypoints.toMutableList()
                        if (slot.index in list.indices) {
                            list[slot.index] = cp
                            onLegChange(leg.copy(waypoints = list, legConfirmed = false))
                        }
                    }
                }
            },
        )
    }
    SetupLayerFrame(
        title = "지역 ${legIndex + 1}",
        borderColor = legBorder,
        backgroundColor = legBg,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        titleTrailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leg.legConfirmed) {
                    Text(
                        "확정",
                        color = Color(0xFF15803D),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                if (canDelete && onDeleteLeg != null) {
                    IconButton(
                        onClick = onDeleteLeg,
                        modifier = Modifier.height(32.dp).width(32.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "지역 삭제", tint = Color(0xFFDC2626))
                    }
                }
            }
        },
    ) {
        LegMonthDayRangeRow(
            tripYear = tripYear,
            tripStartBound = tripStartBound,
            tripEndBound = tripEndBound,
            startEpochDay = leg.startEpochDay,
            endEpochDay = leg.endEpochDay,
            onStartChange = { d -> onLegChange(leg.copy(startEpochDay = d, legConfirmed = false)) },
            onEndChange = { d -> onLegChange(leg.copy(endEpochDay = d, legConfirmed = false)) },
            modifier = Modifier.padding(bottom = 4.dp),
        )

        CityConfirmField(
            point = leg.startPoint,
            onDraftChange = {},
            onConfirm = { p -> onLegChange(leg.copy(startPoint = p, legConfirmed = false)) },
            catalog = catalog,
            geocoder = geocoder,
            countryHint = countryHint,
            label = "출발지",
            placeholder = "도시·거리·장소",
            fieldPaddingVertical = 1.dp,
            onGlobeClick = { mapPickSlot = LegMapPickSlot.Start },
        )

        leg.waypoints.forEachIndexed { wi, wp ->
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
                label = "경유지",
                placeholder = "도시·거리·장소",
                fieldPaddingVertical = 1.dp,
                onRemove = {
                    onLegChange(
                        leg.copy(
                            waypoints = leg.waypoints.filterIndexed { i, _ -> i != wi },
                            legConfirmed = false,
                        ),
                    )
                },
                onGlobeClick = { mapPickSlot = LegMapPickSlot.Waypoint(wi) },
            )
        }

        OutlinedButton(
            onClick = {
                onLegChange(leg.copy(waypoints = leg.waypoints + CityPoint(), legConfirmed = false))
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            shape = RectangleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.height(16.dp))
            Text("경유지 추가", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
        }

        CityConfirmField(
            point = leg.endPoint,
            onDraftChange = {},
            onConfirm = { p -> onLegChange(leg.copy(endPoint = p, legConfirmed = false)) },
            catalog = catalog,
            geocoder = geocoder,
            countryHint = countryHint,
            label = "도착지",
            placeholder = "도시·거리·장소",
            fieldPaddingVertical = 1.dp,
            onGlobeClick = { mapPickSlot = LegMapPickSlot.End },
        )
    }
}

private sealed class LegMapPickSlot {
    data object Start : LegMapPickSlot()
    data class Waypoint(val index: Int) : LegMapPickSlot()
    data object End : LegMapPickSlot()
}

private fun CityPoint.isConfigured(): Boolean =
    confirmed && lat != 0.0 && lon != 0.0

private data class LegMapPickContext(
    val centerLat: Double,
    val centerLon: Double,
    val referenceLabel: String,
    val referenceIsAirport: Boolean,
    val initialPickLat: Double?,
    val initialPickLon: Double?,
)

/** 출발→경유→도착 순서. 해당 칸 이전 중 가장 가까운 확정 지점을 기준·포인터로 사용. */
private fun legMapPickContext(
    slot: LegMapPickSlot,
    leg: ScheduleLeg,
    arrivalAirport: CityPoint,
    fallbackCenter: Pair<Double, Double>,
): LegMapPickContext {
    val predecessors: List<Pair<CityPoint, Boolean>> = when (slot) {
        LegMapPickSlot.Start -> listOf(arrivalAirport to true)
        is LegMapPickSlot.Waypoint -> buildList {
            for (j in slot.index - 1 downTo 0) {
                leg.waypoints.getOrNull(j)?.let { add(it to false) }
            }
            add(leg.startPoint to false)
            add(arrivalAirport to true)
        }
        LegMapPickSlot.End -> buildList {
            for (j in leg.waypoints.lastIndex downTo 0) {
                add(leg.waypoints[j] to false)
            }
            add(leg.startPoint to false)
            add(arrivalAirport to true)
        }
    }

    val configuredRef = predecessors.firstOrNull { (pt, _) -> pt.isConfigured() }
    val refLat: Double
    val refLon: Double
    val refLabel: String
    val refIsAirport: Boolean
    if (configuredRef != null) {
        val (pt, isAirport) = configuredRef
        refLat = pt.lat
        refLon = pt.lon
        refLabel = pt.name.ifBlank { if (isAirport) "입국 공항" else "이전 지점" }
        refIsAirport = isAirport
    } else {
        val (lat, lon) = fallbackCenter
        refLat = lat
        refLon = lon
        refLabel = "기준 위치"
        refIsAirport = false
    }

    val current: CityPoint? = when (slot) {
        LegMapPickSlot.Start -> leg.startPoint
        is LegMapPickSlot.Waypoint -> leg.waypoints.getOrNull(slot.index)
        LegMapPickSlot.End -> leg.endPoint
    }

    val (pickLat, pickLon) = if (current?.isConfigured() == true) {
        current.lat to current.lon
    } else {
        refLat to refLon
    }

    return LegMapPickContext(
        centerLat = refLat,
        centerLon = refLon,
        referenceLabel = refLabel,
        referenceIsAirport = refIsAirport,
        initialPickLat = pickLat,
        initialPickLon = pickLon,
    )
}

data class MapRouteMarker(
    val lat: Double,
    val lon: Double,
    val label: String,
    val kind: String,
)

private fun legRouteMarkers(leg: ScheduleLeg, arrivalAirport: CityPoint): List<MapRouteMarker> =
    buildList {
        if (arrivalAirport.isConfigured()) {
            add(
                MapRouteMarker(
                    lat = arrivalAirport.lat,
                    lon = arrivalAirport.lon,
                    label = arrivalAirport.name.ifBlank { "입국 공항" },
                    kind = "airport",
                ),
            )
        }
        if (leg.startPoint.isConfigured()) {
            add(
                MapRouteMarker(
                    lat = leg.startPoint.lat,
                    lon = leg.startPoint.lon,
                    label = leg.startPoint.name.ifBlank { "출발지" },
                    kind = "start",
                ),
            )
        }
        leg.waypoints.forEachIndexed { i, wp ->
            if (wp.isConfigured()) {
                add(
                    MapRouteMarker(
                        lat = wp.lat,
                        lon = wp.lon,
                        label = wp.name.ifBlank { "경유 ${i + 1}" },
                        kind = "waypoint",
                    ),
                )
            }
        }
        if (leg.endPoint.isConfigured()) {
            add(
                MapRouteMarker(
                    lat = leg.endPoint.lat,
                    lon = leg.endPoint.lon,
                    label = leg.endPoint.name.ifBlank { "도착지" },
                    kind = "end",
                ),
            )
        }
    }

private fun escapeJsonString(raw: String): String =
    raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

internal fun routeMarkersJson(markers: List<MapRouteMarker>): String {
    if (markers.isEmpty()) return "[]"
    return markers.joinToString(prefix = "[", postfix = "]") { m ->
        """{"lat":${m.lat},"lon":${m.lon},"label":"${escapeJsonString(m.label)}","kind":"${m.kind}"}"""
    }
}

private fun looksLikeCoordinates(name: String): Boolean =
    name.matches(Regex("""^-?\d+\.\d+,\s*-?\d+\.\d+$"""))

private suspend fun resolvePlaceLabel(
    context: Context,
    geocoder: NominatimGeocoder,
    lat: Double,
    lon: Double,
    language: String,
): String {
    runCatching { geocoder.reverse(lat, lon, language = language) }.getOrNull()?.let { geo ->
        labelFromGeo(geo)?.let { return it }
    }
    LocalAdminGeocoder.reverseLabel(context, lat, lon)?.takeIf { it.isNotBlank() }?.let { return it }
    return "%.4f, %.4f".format(lat, lon)
}

private fun labelFromGeo(geo: GeoResult): String? {
    geo.adminPlaceLabel().takeIf { it.isNotBlank() }?.let { return it }
    geo.name.takeIf { it.isNotBlank() && !looksLikeCoordinates(it) }?.let { return it }
    geo.description.takeIf { it.isNotBlank() }?.let { return it }
    return geo.displayName.split(",").take(3).joinToString(" · ").trim().takeIf { it.isNotBlank() }
}

@Composable
fun SetupMapPointPickerDialog(
    visible: Boolean,
    centerLat: Double,
    centerLon: Double,
    initialPickLat: Double? = null,
    initialPickLon: Double? = null,
    referenceLabel: String = "입국 공항",
    referenceIsAirport: Boolean = true,
    routeMarkers: List<MapRouteMarker> = emptyList(),
    countryHint: String,
    homeCountryCode: String,
    geocoder: NominatimGeocoder,
    onDismiss: () -> Unit,
    onPicked: (CityPoint) -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val homeLang = HomeLanguage.langTag(homeCountryCode.ifBlank { "KR" })
    val markersJson = remember(routeMarkers) { routeMarkersJson(routeMarkers) }
    var pickLatLon by remember(centerLat, centerLon, initialPickLat, initialPickLon) {
        mutableStateOf(
            if (initialPickLat != null && initialPickLon != null) initialPickLat to initialPickLon else null,
        )
    }
    var pickLabel by remember { mutableStateOf("") }
    var resolvingLabel by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }

    LaunchedEffect(pickLatLon) {
        val (lat, lon) = pickLatLon ?: run {
            resolvingLabel = false
            return@LaunchedEffect
        }
        resolvingLabel = true
        delay(320)
        pickLabel = resolvePlaceLabel(context, geocoder, lat, lon, homeLang)
        resolvingLabel = false
    }

    LaunchedEffect(initialPickLat, initialPickLon) {
        if (initialPickLat != null && initialPickLon != null && pickLatLon == null) {
            pickLatLon = initialPickLat to initialPickLon
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.setWindowAnimations(0)
            onDispose { }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppMenuStyle.card,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("지도에서 선택", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (referenceIsAirport) {
                        "지도를 탭해 위치를 지정하세요 · ✈ = $referenceLabel"
                    } else {
                        "지도를 탭해 위치를 지정하세요 · 녹색 = $referenceLabel"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (routeMarkers.isNotEmpty()) {
                    Text(
                        "✈ 공항 · 출 출발 · 숫자 경유 · 도 도착 — 한 화면에 표시",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    when {
                        pickLabel.isNotBlank() -> {
                            Text(
                                pickLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppMenuStyle.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        resolvingLabel && pickLatLon != null -> {
                            Text(
                                "지명 찾는 중…",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppMenuStyle.muted,
                            )
                        }
                        pickLatLon != null -> {
                            val (lat, lon) = pickLatLon!!
                            Text(
                                "%.5f, %.5f".format(lat, lon),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppMenuStyle.muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                LeafletMapPickerView(
                    centerLat = centerLat,
                    centerLon = centerLon,
                    initialPickLat = initialPickLat,
                    initialPickLon = initialPickLon,
                    referenceLabel = referenceLabel,
                    referenceIsAirport = referenceIsAirport,
                    routeMarkersJson = markersJson,
                    onCoordinatesPicked = { lat, lon -> pickLatLon = lat to lon },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    TextButton(
                        enabled = pickLatLon != null && !resolving && !resolvingLabel,
                        onClick = {
                            val (lat, lon) = pickLatLon ?: return@TextButton
                            scope.launch {
                                resolving = true
                                val label = pickLabel.ifBlank {
                                    resolvePlaceLabel(context, geocoder, lat, lon, homeLang)
                                }
                                onPicked(CityPoint(name = label, lat = lat, lon = lon, confirmed = true))
                                resolving = false
                            }
                        },
                    ) { Text(if (resolving) "확인 중…" else "확인") }
                }
            }
        }
    }
}

/** 1단계 — 하이킹 지역(leg) 목록 */
@Composable
fun ScheduleLegsVerticalList(
    legs: List<ScheduleLeg>,
    tripYear: Int,
    tripStartBound: Long,
    tripEndBound: Long,
    destCountry: String,
    arrivalAirport: CityPoint,
    homeCountryCode: String,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    onLegChange: (Int, ScheduleLeg) -> Unit,
    onAddLeg: () -> Unit,
    onDeleteLeg: (Int) -> Unit,
    modifier: Modifier = Modifier,
    cityPlaceholder: String? = null,
) {
    val listScroll = rememberScrollState()
    var lastLegCount by remember { mutableIntStateOf(legs.size) }
    LaunchedEffect(legs.size) {
        if (legs.size > lastLegCount) {
            lastLegCount = legs.size
            delay(80)
            listScroll.animateScrollTo(listScroll.maxValue)
        } else {
            lastLegCount = legs.size
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (legs.isEmpty()) "하이킹 지역" else "하이킹 지역 · ${legs.size}개",
                fontWeight = FontWeight.SemiBold,
                color = AppMenuStyle.text,
            )
            OutlinedButton(
                onClick = onAddLeg,
                shape = RectangleShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.height(16.dp))
                Text("지역 추가", modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(listScroll)
                    .background(AppMenuStyle.scroll, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .graphicsLayer { clip = false },
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            arrivalAirport = arrivalAirport,
                            homeCountryCode = homeCountryCode,
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                                shape = RectangleShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                            ) { Text("이 지역 확정", style = MaterialTheme.typography.labelLarge) }
                        }
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
    val radius = com.mcpauto.walkingofflineguide.data.STOP_DOWNLOAD_RADIUS_KM
    return legs.filter { it.legConfirmed }.mapIndexedNotNull { idx, leg ->
        val path = buildList {
            if (leg.startPoint.confirmed && leg.startPoint.lat != 0.0) {
                add(leg.startPoint.lat to leg.startPoint.lon)
            }
            leg.waypoints.filter { it.confirmed && it.lat != 0.0 }.forEach { add(it.lat to it.lon) }
            if (leg.endPoint.confirmed && leg.endPoint.lat != 0.0) {
                add(leg.endPoint.lat to leg.endPoint.lon)
            }
        }
        if (path.isEmpty()) return@mapIndexedNotNull null
        val bbox = PoiLogic.corridorBboxForPath(path, radius)
        val centerLat = path.map { it.first }.average()
        val centerLon = path.map { it.second }.average()
        val via = leg.waypoints.filter { it.confirmed }.joinToString("→") { it.name.take(8) }
        val label = buildString {
            append("지역${idx + 1} ")
            append(leg.startPoint.name.take(12))
            if (via.isNotBlank()) append("→$via")
            append("→")
            append(leg.endPoint.name.take(12))
        }
        com.mcpauto.walkingofflineguide.data.CityStop(
            name = label.trim(),
            lat = centerLat,
            lon = centerLon,
            radiusKm = radius,
            customBbox = bbox,
        )
    }
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
