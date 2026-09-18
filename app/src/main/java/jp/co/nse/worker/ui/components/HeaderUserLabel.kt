package jp.co.nse.worker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.ui.theme.avatarDrawableForKey
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.flow.firstOrNull

/** 現在ログイン中のユーザー名を取得する（未取得の間は空文字） */
@Composable
fun rememberCurrentUserName(): String {
    val context = LocalContext.current
    var userName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        userName = context.appContainer.settings.userNameFlow.firstOrNull().orEmpty()
    }
    return userName
}

/**
 * ヘッダー右側（actions）に表示する、現在ログイン中のアカウント名ラベル。
 * タップするとマイページへ遷移する（[onClick]を渡すとその動作に差し替えられる）。
 */
@Composable
fun HeaderUserLabel(name: String, onClick: (() -> Unit)? = null) {
    if (name.isBlank()) return
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    val avatarKey by context.appContainer.settings.avatarKeyFlow.collectAsState(initial = null)
    val avatarDrawable = avatarDrawableForKey(avatarKey)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable {
                feedback()
                if (onClick != null) onClick() else context.appContainer.openMyPage?.invoke()
            }
            .padding(end = 4.dp),
    ) {
        if (avatarDrawable != null) {
            Image(
                painter = painterResource(avatarDrawable),
                contentDescription = null,
                modifier = Modifier.size(34.dp).padding(end = 4.dp),
            )
        }
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
