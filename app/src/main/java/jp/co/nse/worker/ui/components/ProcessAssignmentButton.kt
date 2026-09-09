package jp.co.nse.worker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * ヘッダー右側に置く、担当工程マスタへの導線（歯車アイコン）。
 * worker_process_assignments.view 権限（または管理者）を持つアカウントにのみ表示する。
 */
@Composable
fun ProcessAssignmentButton() {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    val canManage by context.appContainer.settings.canManageProcessAssignmentsFlow.collectAsState(initial = false)
    if (!canManage) return

    IconButton(onClick = { feedback(); context.appContainer.openProcessAssignments?.invoke() }) {
        Icon(Icons.Filled.Settings, contentDescription = "担当工程マスタ", tint = MaterialTheme.colorScheme.onPrimary)
    }
}
