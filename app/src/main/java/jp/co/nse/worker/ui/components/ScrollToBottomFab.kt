package jp.co.nse.worker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * 画面下部に表示する「続きがある」ボタン。横向きなど画面が低くなり、
 * スクロールしないと見えない内容がある場合の目印として使う。
 * [visible] が true の間だけフェード表示する（呼び出し側は LazyListState.canScrollForward を渡す）。
 */
@Composable
fun ScrollToBottomFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "続きを見る")
        }
    }
}
