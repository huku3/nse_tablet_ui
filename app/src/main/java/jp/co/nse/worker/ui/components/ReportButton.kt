package jp.co.nse.worker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.util.rememberClickFeedback

/** ヘッダー右側に置く、不具合・UI改善要望を報告する画面への導線。全アカウントに表示する */
@Composable
fun ReportButton() {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    IconButton(onClick = { feedback(); context.appContainer.openReport?.invoke() }) {
        Icon(Icons.Filled.Feedback, contentDescription = "不具合・要望の報告", tint = MaterialTheme.colorScheme.onPrimary)
    }
}
