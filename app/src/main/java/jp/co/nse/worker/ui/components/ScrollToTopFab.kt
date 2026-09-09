package jp.co.nse.worker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * 画面下部に表示する「先頭に戻る」ボタン。[visible] が true の間だけフェード表示する。
 * 呼び出し側でスクロール量を監視して [visible] を渡し、[onClick] でリスト/スクロールを先頭へ戻す。
 */
@Composable
fun ScrollToTopFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val feedback = rememberClickFeedback()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = { feedback(); onClick() },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "先頭に戻る")
        }
    }
}
