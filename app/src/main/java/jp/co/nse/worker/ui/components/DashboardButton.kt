package jp.co.nse.worker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.util.rememberClickFeedback

/** ヘッダー右側に置く、ダッシュボードへの導線 */
@Composable
fun DashboardButton() {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    IconButton(onClick = { feedback(); context.appContainer.openDashboard?.invoke() }) {
        Icon(Icons.Filled.Dashboard, contentDescription = "ダッシュボード", tint = MaterialTheme.colorScheme.onPrimary)
    }
}
