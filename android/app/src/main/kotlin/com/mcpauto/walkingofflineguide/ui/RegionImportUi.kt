package com.mcpauto.walkingofflineguide.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
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

    OutlinedButton(
        onClick = { launcher.launch(arrayOf(RegionImportManager.MIME_ZIP, "application/x-zip-compressed")) },
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

    Text(
        "PC에서 받은 wog-도시.zip 을 선택하세요 (다운로드·문서 폴더)",
        style = MaterialTheme.typography.labelSmall,
        color = AppMenuStyle.muted,
        modifier = Modifier.padding(top = 4.dp),
    )
}
