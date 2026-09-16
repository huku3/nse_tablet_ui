package jp.co.nse.worker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import jp.co.nse.worker.R
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.util.rememberClickFeedback

// 実ロゴ画像（nse_logo.webp）の実寸比率（幅600×高さ227px）
private const val LOGO_ASPECT = 227f / 600f
private val LOGO_HEIGHT = 24.dp

/**
 * ヘッダー左側に置く会社ロゴ。タップすると作業一覧（ホーム）へ戻る。
 * ロゴ画像自体が白背景のため、どのヘッダー色（メイン色はマイページで変更可能）でも
 * 馴染むよう白い角丸チップの上に載せる。
 *
 * [onClick] を渡さない場合は、どの画面からでも作業一覧まで戻れるよう
 * [jp.co.nse.worker.data.AppContainer.openHome] を呼ぶ。ホーム画面内の各タブ
 * （作業一覧・割り当て・受注一覧・在庫・出荷カレンダー）は、画面遷移ではなく
 * タブ切替（pagerState）で作業一覧タブへ移動したいため、明示的に渡すこと。
 */
@Composable
fun HeaderLogo(onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    val action = onClick ?: { context.appContainer.openHome?.invoke() }

    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .clickable { feedback(); action() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.nse_logo),
            contentDescription = "作業一覧へ",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(LOGO_HEIGHT)
                .width(LOGO_HEIGHT * (1f / LOGO_ASPECT)),
        )
    }
}
