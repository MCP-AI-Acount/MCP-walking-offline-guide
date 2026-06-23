package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.sp
import com.mcpauto.walkingofflineguide.map.MapUiColors

private val MapChromeBtn = 36.dp
private val MapChromeIcon = 22.dp
/** GPS 홀드 토글(MapChromeBtn)과 동일 — 반경 배지 (지도 원보다 작게) */
private val MapRadarBadge = 28.dp
private val RadarBlue = Color(0xFF2563EB)

/** 지도 상단 — 반경 배지 (지름 m · 숫자+m 한 덩어리 중앙) */
@Composable
fun RadarRadiusBadge(
    radarRadiusM: Int,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = radarRadiusM > 0
    Box(
        modifier = modifier
            .border(
                width = 0.33.dp,
                color = if (active) RadarBlue else Color(0xFF94A3B8),
                shape = CircleShape,
            )
            .clickable(onClick = onCycle),
        contentAlignment = Alignment.Center,
    ) {
        if (!active) {
            Text(
                text = "OFF",
                fontSize = 5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = 5.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.offset(y = 0.3.dp),
            )
        } else {
            Text(
                text = "${radarRadiusM}m",
                fontSize = 5.2.sp,
                fontWeight = FontWeight.Bold,
                color = RadarBlue,
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = 5.2.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

/** 지도 우상단 — 반경(왼쪽) + GPS(오른쪽), 크기 통일 */
@Composable
fun MapGpsRadiusChromeRow(
    radarRadiusM: Int,
    onCycleRadar: () -> Unit,
    gpsLocked: Boolean,
    gpsInteractive: Boolean = true,
    onToggleGpsLock: (() -> Unit)? = null,
    lockContentDescription: String = if (gpsLocked) "위치 고정" else "위치",
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RadarRadiusBadge(
            radarRadiusM = radarRadiusM,
            onCycle = onCycleRadar,
            modifier = Modifier.size(MapRadarBadge),
        )
        IconButton(
            onClick = { onToggleGpsLock?.invoke() },
            enabled = gpsInteractive && onToggleGpsLock != null,
            modifier = Modifier.size(MapChromeBtn),
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = lockContentDescription,
                tint = when {
                    !gpsInteractive -> Color(0xFF64748B)
                    gpsLocked -> Color(0xFF15803D)
                    else -> Color(0xFF64748B)
                },
                modifier = Modifier.size(MapChromeIcon),
            )
        }
    }
}

/** 지도 열과 POI 필터 열 사이 — GPS 고정 등 (반경은 지도 상단 원형 배지) */
@Composable
fun MapSideControlPanel(
    showGpsPin: Boolean,
    gpsLocked: Boolean,
    onToggleGpsLock: () -> Unit,
    followGps: Boolean,
    gpsControlEnabled: Boolean,
    onFollowGpsChange: (Boolean) -> Unit,
    onLocate: () -> Unit,
    resetLabel: String,
    followGpsLabel: String,
    followTripLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(MapUiColors.panelBg)
            .statusBarsPadding()
            .padding(top = 1.dp, bottom = 8.dp)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
    ) {
        if (showGpsPin) {
            IconButton(onClick = onToggleGpsLock) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = if (gpsLocked) "GPS 고정" else "GPS 고정 해제",
                    tint = if (gpsLocked) Color(0xFF15803D) else Color(0xFF64748B),
                )
            }
        } else {
            IconButton(onClick = onLocate) {
                Icon(Icons.Default.Refresh, contentDescription = resetLabel, tint = Color(0xFF3D8B5E))
            }
            IconToggleButton(
                checked = followGps,
                enabled = gpsControlEnabled,
                onCheckedChange = onFollowGpsChange,
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = if (followGps) followGpsLabel else followTripLabel,
                    tint = if (followGps) Color(0xFF15803D) else Color(0xFF64748B),
                )
            }
        }
    }
}
