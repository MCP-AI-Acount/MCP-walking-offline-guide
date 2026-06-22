package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.data.CityPoint
import com.mcpauto.walkingofflineguide.data.GeoCatalog
import com.mcpauto.walkingofflineguide.data.ScheduleLeg
import com.mcpauto.walkingofflineguide.network.NominatimGeocoder
import kotlinx.coroutines.launch

fun formatDateDigitsInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(8)
    return when {
        digits.length <= 4 -> digits
        digits.length <= 6 -> "${digits.take(4)}-${digits.drop(4)}"
        else -> "${digits.take(4)}-${digits.substring(4, 6)}-${digits.drop(6)}"
    }
}

@Composable
fun DateMaskField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(formatDateDigitsInput(it)) },
        label = { Text("시작일") },
        placeholder = { Text("2026-06-25") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
fun CountryAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    catalog: GeoCatalog,
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value) { catalog.searchCountries(value) }

    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.isNotBlank()
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (expanded && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 160.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    suggestions.forEach { c ->
                        Text(
                            "${c.nameKo} (${c.nameEn})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(c.nameKo.ifBlank { c.nameEn })
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CityConfirmField(
    point: CityPoint,
    onDraftChange: (String) -> Unit,
    onConfirm: (CityPoint) -> Unit,
    catalog: GeoCatalog,
    geocoder: NominatimGeocoder,
    countryHint: String,
    label: String,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(point.name, point.confirmed) { mutableStateOf(point.name) }
    var confirming by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val suggestions = remember(draft, countryHint, point.confirmed) {
        if (point.confirmed || draft.length < 1) emptyList() else catalog.searchCities(draft, countryHint)
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        OutlinedTextField(
            value = if (point.confirmed) point.name else draft,
            onValueChange = {
                if (!point.confirmed) {
                    draft = it
                    onDraftChange(it)
                }
            },
            label = { Text(if (point.confirmed) "✓ $label" else label) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = point.confirmed,
            singleLine = true,
        )

        if (!point.confirmed && suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 120.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                suggestions.take(8).forEach { c ->
                    Text(
                        "${c.name} · ${c.country}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = c.name
                                onDraftChange(c.name)
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2563EB),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!point.confirmed) {
                Button(
                    onClick = {
                        scope.launch {
                            confirming = true
                            err = ""
                            val name = draft.trim()
                            if (name.isBlank()) {
                                err = "도시명을 입력해 주세요"
                                confirming = false
                                return@launch
                            }
                            val local = catalog.resolveCity(name, countryHint)
                            val resolved = if (local != null) {
                                CityPoint(name = local.name, lat = local.lat, lon = local.lon, confirmed = true)
                            } else {
                                val geo = geocoder.search(name, countryHint)
                                if (geo == null) {
                                    err = "도시를 찾을 수 없습니다"
                                    confirming = false
                                    return@launch
                                }
                                CityPoint(name = geo.name, lat = geo.lat, lon = geo.lon, confirmed = true)
                            }
                            onConfirm(resolved)
                            confirming = false
                        }
                    },
                    enabled = !confirming,
                    modifier = Modifier.weight(1f),
                ) { Text(if (confirming) "확인 중…" else "확인") }
            } else {
                OutlinedButton(
                    onClick = { onConfirm(CityPoint()) },
                    modifier = Modifier.weight(1f),
                ) { Text("수정") }
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

            CityConfirmField(
                point = leg.startPoint,
                onDraftChange = {},
                onConfirm = { p -> onLegChange(leg.copy(startPoint = p, legConfirmed = false)) },
                catalog = catalog,
                geocoder = geocoder,
                countryHint = countryHint,
                label = "도보 출발지",
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
                            label = "경유지",
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
                label = "도보 도착지",
            )
        }
    }
}

fun isLegReady(leg: ScheduleLeg): Boolean =
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
