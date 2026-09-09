package jp.co.nse.worker.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.R
import jp.co.nse.worker.ui.components.clipReveal
import jp.co.nse.worker.util.FlowerOfDay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GreetingColor = Color(0xFF5F5E5A)
private val NameColor = Color(0xFF0C2C4A)
private val AccentBlue = Color(0xFF378ADD)

// Google Fonts経由でダウンロードするNoto Serif JP（CJKフルセットは数十MBあるためアプリに同梱せず取得する）
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)
private val notoSerifJp = GoogleFont("Noto Serif JP")
private val NotoSerifJpFamily = FontFamily(
    Font(googleFont = notoSerifJp, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = notoSerifJp, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = notoSerifJp, fontProvider = fontProvider, weight = FontWeight.Bold),
)

// シャープな動き用のイージング（バウンドなし、ease-out寄り。SplashScreenと共通の質感）
private val SharpEasing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)

/**
 * ログイン成功時のウェルカム演出。
 * 「Hello, {userName}さん！」を表示し、演出完了から2秒後に自動でフェードアウトしてから [onFinished] を呼ぶ。
 * 画面をタップすると、その時点ですぐにフェードアウトして [onFinished] を呼ぶ。
 */
@Composable
fun WelcomeScreen(userName: String, onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val contentAlpha = remember { Animatable(1f) }

    suspend fun finishWithFadeOut() {
        if (finished) return
        finished = true
        contentAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
        onFinished()
    }

    val context = LocalContext.current
    val flower = remember { FlowerOfDay.today(context) }

    val greetingAlpha = remember { Animatable(0f) }
    val greetingOffsetY = remember { Animatable(16f) }
    val nameAlpha = remember { Animatable(0f) }
    val nameOffsetY = remember { Animatable(20f) }
    val underlineWidth = remember { Animatable(0f) }
    val flowerAlpha = remember { Animatable(0f) }
    val tapHintAlpha = remember { Animatable(0f) }

    LaunchedEffect(userName) {
        delay(150)

        // 1. "Hello," がフェード＋スライドイン
        launch { greetingAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        greetingOffsetY.animateTo(0f, tween(380, easing = SharpEasing))

        // 2. "{userName}さん！" が少し遅れて大きくフェード＋スライドイン
        delay(120)
        launch { nameAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
        nameOffsetY.animateTo(0f, tween(420, easing = SharpEasing))

        // 3. アンダーラインが伸びる
        delay(180)
        underlineWidth.animateTo(140f, tween(280, easing = SharpEasing))

        // 4. 今日の花言葉がフェードイン
        delay(200)
        flowerAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))

        // 5. "Tap to start" がフェードイン（以後、上下バウンドを繰り返す）
        delay(250)
        tapHintAlpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))

        // 6. タップしなくても2秒後に自動でフェードアウトしてメイン画面へ
        delay(2000)
        finishWithFadeOut()
    }

    // "Tap to start" の上下バウンド（タップされるまで無限ループ）
    val tapHintTransition = rememberInfiniteTransition(label = "tapHint")
    val tapHintOffsetY by tapHintTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = SharpEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tapHintOffsetY",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClick = { scope.launch { finishWithFadeOut() } }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value },
        ) {
            androidx.compose.material3.Text(
                text = "Hello,",
                fontSize = 56.sp,
                fontFamily = NotoSerifJpFamily,
                fontWeight = FontWeight.Medium,
                color = GreetingColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = greetingOffsetY.value.dp)
                    .clipReveal(greetingAlpha.value),
            )

            Spacer(Modifier.height(12.dp))

            androidx.compose.material3.Text(
                text = "${userName}さん！",
                fontSize = 88.sp,
                fontFamily = NotoSerifJpFamily,
                fontWeight = FontWeight.Bold,
                color = NameColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = nameOffsetY.value.dp)
                    .clipReveal(nameAlpha.value),
            )

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .height(4.dp)
                    .width(underlineWidth.value.dp)
                    .background(AccentBlue),
            )

            if (flower != null) {
                Spacer(Modifier.height(28.dp))

                androidx.compose.material3.Text(
                    text = "今日の花: ${flower.name}「${flower.meaning}」",
                    fontSize = 26.sp,
                    fontFamily = NotoSerifJpFamily,
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Italic,
                    color = GreetingColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = flowerAlpha.value },
                )
            }

            Spacer(Modifier.height(48.dp))

            androidx.compose.material3.Text(
                text = "Tap to start",
                fontSize = 22.sp,
                fontFamily = NotoSerifJpFamily,
                fontWeight = FontWeight.Medium,
                color = GreetingColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer { alpha = tapHintAlpha.value }
                    .offset(y = tapHintOffsetY.dp),
            )
        }
    }
}
