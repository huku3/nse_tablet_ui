package jp.co.nse.worker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
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
 * ヘッダー右側に置く、届いた報告の一覧・対応管理画面への導線。
 * reports.manage 権限（または管理者）を持つアカウントにのみ表示する。
 */
@Composable
fun ReportListButton() {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    val canManage by context.appContainer.settings.canManageReportsFlow.collectAsState(initial = false)
    if (!canManage) return

    IconButton(onClick = { feedback(); context.appContainer.openReportList?.invoke() }) {
        Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = "報告一覧", tint = MaterialTheme.colorScheme.onPrimary)
    }
}
