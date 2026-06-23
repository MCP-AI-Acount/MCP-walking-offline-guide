package com.mcpauto.walkingofflineguide.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mcpauto.walkingofflineguide.data.RegionRecord
import com.mcpauto.walkingofflineguide.data.TripStore
import com.mcpauto.walkingofflineguide.download.RegionImportManager
import kotlinx.coroutines.launch

@Composable
fun RegionImportButton(
    store: TripStore,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onImported: (RegionRecord) -> Unit,
    onError: (String) -> Unit,
    onSuccessMessage: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        scope.launch {
            importing = true
            runCatching {
                val result = RegionImportManager(context, store).importFromUri(uri)
                onImported(result.region)
                onSuccessMessage(result.summary)
            }.onFailure { e ->
                onError(e.message ?: "가져오기에 실패했습니다.")
            }
            importing = false
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("파일에서 가져오기") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("① PC에서 export_region_bundle.py 로 지역 zip을 만듭니다.", style = MaterialTheme.typography.bodySmall)
                    Text("② 휴대폰 다운로드·문서 폴더 등으로 zip을 옮깁니다.", style = MaterialTheme.typography.bodySmall)
                    Text("③ 아래 「확인」 후 파일 선택 창에서 zip을 고릅니다.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "지도·명소 데이터는 앱 전용 저장소에만 저장됩니다. 다운로드 폴더에 zip만 넣어두면 읽지 않습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppMenuStyle.muted,
                    )
                    Text(
                        "PC에서 export_region_bundle.py 로 만든 zip → 반드시 이 버튼으로 선택하세요.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppMenuStyle.muted,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuide = false }) { Text("취소") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGuide = false
                        launcher.launch(
                            arrayOf(
                                RegionImportManager.MIME_ZIP,
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ),
                        )
                    },
                ) { Text("확인") }
            },
        )
    }

    OutlinedButton(
        onClick = { showGuide = true },
        enabled = enabled && !importing,
        modifier = modifier.fillMaxWidth(),
    ) {
        if (importing) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(if (importing) "가져오는 중…" else "파일에서 가져오기")
    }
}
