package jp.co.nse.worker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.util.rememberClickFeedback

/** ヘッダー右側に置く、マイページ（メイン色の変更等）への導線 */
@Composable
fun MyPageButton() {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    IconButton(onClick = { feedback(); context.appContainer.openMyPage?.invoke() }) {
        Icon(Icons.Filled.AccountCircle, contentDescription = "マイページ", tint = Color.White)
    }
}
