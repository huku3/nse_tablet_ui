package jp.co.nse.worker.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SysNameColor = Color(0xFF0C2C4A)
private val UnderlineColor = Color(0xFF378ADD)
private val CatchphraseColor = Color(0xFF6B7280)

private const val CATCHPHRASE = "一歩先を行く技術力"
private const val SYSTEM_NAME = "NSE生産管理システム"

// 実ロゴ画像（nse_logo.webp）の実寸比率（幅600×高さ227px）
private const val LOGO_ASPECT = 227f / 600f

/**
 * 起動直後の画面。以前は演出アニメーションだったが廃止し、ロゴ・キャッチコピーを
 * 静止表示するだけにした。タップ、または3秒経過で自動的にログイン画面へ進む。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }

    LaunchedEffect(Unit) {
        delay(3000)
        finish()
    }

    val logoWidth = 220.dp
    val logoHeight = logoWidth * LOGO_ASPECT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClick = { scope.launch { finish() } }),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.nse_logo),
                contentDescription = "NSエンジニアリング ロゴ",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(logoWidth).height(logoHeight),
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .width(48.dp)
                    .background(UnderlineColor),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                SYSTEM_NAME,
                color = SysNameColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                CATCHPHRASE,
                color = CatchphraseColor,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(64.dp))
            Text(
                "Tap Start",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
