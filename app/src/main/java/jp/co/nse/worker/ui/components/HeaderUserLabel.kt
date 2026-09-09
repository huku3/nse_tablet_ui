package jp.co.nse.worker.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
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

/** ヘッダー右側（actions）に表示する、現在ログイン中のアカウント名ラベル */
@Composable
fun HeaderUserLabel(name: String) {
    if (name.isBlank()) return
    Text(
        text = name,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.padding(end = 4.dp),
    )
}
