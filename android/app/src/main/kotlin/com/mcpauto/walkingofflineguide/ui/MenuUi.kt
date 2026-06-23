package com.mcpauto.walkingofflineguide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppMenuStyle {
    /** 페이지 바탕 — 따뜻한 크림 */
    val bg = Color(0xFFF3EEE3)
    /** 패널·카드 — 연한 노랑 */
    val card = Color(0xFFFFF6DC)
    val panel = card
    val panelBorder = Color(0xFFE8DCB8)
    /** 스크롤 가능한 안쪽 — 연한 올리브 */
    val scroll = Color(0xFFE9EFE0)
    val scrollBorder = Color(0xFFD5DFC8)
    /** 여행설정 1 상단 — 입국·출국·귀국 레이어 */
    val tripOverviewBg = Color(0xFFFFF0E0)
    val tripOverviewBorder = Color(0xFFE8A855)
    val text = Color(0xFF3D3929)
    val muted = Color(0xFF6B6560)
    val accent = Color(0xFF2563EB)
    val purple = Color(0xFF6366F1)
    val danger = Color(0xFFDC2626)
    val warnBg = Color(0xFFFFF3D6)
    val warnText = Color(0xFF9A3412)
    /** 상태바 뒤 짙은 올리브 50% */
    val statusBarScrim = Color(0x804A5240)
}

/** 시스템 상태바(시간·배터리) 뒤 반투명 바 — 모든 화면 공통 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(AppMenuStyle.statusBarScrim),
    )
}

@Composable
fun AppMenuScaffold(
    title: String,
    titleSubtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onOptions: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = AppMenuStyle.bg,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOptions != null) {
                    IconButton(onClick = onOptions) {
                        Icon(Icons.Default.Settings, contentDescription = "옵션", tint = AppMenuStyle.muted)
                    }
                }
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                } else if (onOptions == null) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                }
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppMenuStyle.text,
                        maxLines = 1,
                    )
                    if (titleSubtitle != null) {
                        Text(
                            " · $titleSubtitle",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                            ),
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                        )
                    }
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) { Text(actionLabel, color = AppMenuStyle.accent) }
                } else if (trailingContent != null) {
                    trailingContent()
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(48.dp))
                }
            }
        },
        content = content,
    )
}

@Composable
fun MenuCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, AppMenuStyle.panelBorder, RoundedCornerShape(12.dp))
            .background(AppMenuStyle.card, RoundedCornerShape(12.dp))
            .padding(14.dp),
        content = content,
    )
}

/** 스크롤·목록이 들어가는 안쪽 면 */
@Composable
fun MenuScrollSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .border(1.dp, AppMenuStyle.scrollBorder, RoundedCornerShape(8.dp))
            .background(AppMenuStyle.scroll, RoundedCornerShape(8.dp)),
        content = content,
    )
}

@Composable
fun MenuPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = AppMenuStyle.accent),
    ) { Text(text) }
}

@Composable
fun MenuSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(text) }
}

@Composable
fun MenuPurpleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = AppMenuStyle.purple),
    ) { Text(text) }
}

@Composable
fun MenuWarnBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        modifier = modifier
            .fillMaxWidth()
            .background(AppMenuStyle.warnBg, RoundedCornerShape(10.dp))
            .padding(12.dp),
        color = AppMenuStyle.warnText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun LoadingMenuShell(
    subtitle: String,
    detail: String,
    showActions: Boolean,
    onMain: () -> Unit,
    onOptions: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppMenuStyle.bg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "도보 여행",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AppMenuStyle.text,
        )
        Text(
            "오프라인",
            style = MaterialTheme.typography.titleMedium,
            color = AppMenuStyle.muted,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        androidx.compose.material3.CircularProgressIndicator(
            color = AppMenuStyle.accent,
            strokeWidth = 2.dp,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        Text(subtitle, color = AppMenuStyle.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (detail.isNotBlank()) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
            Text(detail, color = AppMenuStyle.text, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        if (showActions) {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(0.85f)) {
                MenuSecondaryButton("메인", onMain, Modifier.weight(1f))
                MenuSecondaryButton("옵션", onOptions, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HubTopBar(onOptions: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOptions) {
            Icon(Icons.Default.Settings, contentDescription = "옵션", tint = AppMenuStyle.muted)
        }
        Text(
            "메인",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AppMenuStyle.text,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(48.dp))
    }
}
