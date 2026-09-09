package jp.co.nse.worker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.R
import jp.co.nse.worker.ui.components.clipReveal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==== カラー定義 ====
private val PartBlue1 = Color(0xFF378ADD)
private val PartBlue2 = Color(0xFF0C447C)
private val PartBlue3 = Color(0xFF85B7EB)
private val PartGreen = Color(0xFF1D9E75)
private val SysNameColor = Color(0xFF0C2C4A)
private val UnderlineColor = Color(0xFF378ADD)
private val CatchphraseMarkerStart = Color(0xFF60A5FA).copy(alpha = 0.9f)
private val CatchphraseMarkerEnd = Color(0xFF1D4ED8).copy(alpha = 0.9f)

// シャープな動き用のイージング（バウンドなし、ease-out寄り）
private val SharpEasing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)

private data class PartDef(
    val shape: (Float) -> Path, // 30dp基準のPath（sizeスケール込み）
    val start: Offset,          // 開始位置（中心からのオフセット, dp）
    val end: Offset,            // 集合位置（中心からのオフセット, dp）
    val color: Color
)

private fun trianglePath(size: Float): Path = Path().apply {
    moveTo(0f, 0f); lineTo(size, 0f); lineTo(size, size); close()
}
private fun arrowPath(size: Float): Path = Path().apply {
    moveTo(0f, 0f); lineTo(size, 0f); lineTo(size / 2f, size * 0.87f); close()
}
private fun squarePath(size: Float): Path = Path().apply {
    moveTo(0f, 0f); lineTo(size, 0f); lineTo(size, size); lineTo(0f, size); close()
}
private fun diamondPath(size: Float): Path = Path().apply {
    moveTo(0f, size / 2f); lineTo(size / 2f, 0f); lineTo(size, size / 2f); lineTo(size / 2f, size); close()
}

// サイズ拡大率（形状のサイズ・移動距離・ロゴなど全体に適用）。当初の2倍からさらに2倍の4倍に
private const val SCALE = 4f

private val partDefs = listOf(
    PartDef(::trianglePath, Offset(-140f, -160f) * SCALE, Offset(-30f, -30f) * SCALE, PartBlue1),
    PartDef(::arrowPath,    Offset(170f, -150f) * SCALE,  Offset(0f, -30f) * SCALE,   PartBlue2),
    PartDef(::squarePath,   Offset(180f, 150f) * SCALE,   Offset(0f, 0f) * SCALE,     PartBlue3),
    PartDef(::diamondPath,  Offset(-150f, 160f) * SCALE,  Offset(-30f, 0f) * SCALE,   PartGreen)
)

private const val CATCHPHRASE = "一歩先を行く技術力"
private const val SYSTEM_NAME = "NSE生産管理システム"

// 実ロゴ画像（nse_logo.webp）の実寸比率（幅600×高さ227px）
private const val LOGO_ASPECT = 227f / 600f

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val contentAlpha = remember { Animatable(1f) }

    suspend fun finishWithFadeOut() {
        if (finished) return
        finished = true
        contentAlpha.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
        onFinished()
    }

    // 各パーツのアニメーション進行度（0f=開始位置, 1f=集合位置）
    val partProgress = remember { partDefs.map { Animatable(0f) } }
    val partAlpha = remember { partDefs.map { Animatable(0f) } }

    var showLogo by remember { mutableStateOf(false) }
    val underlineWidth = remember { Animatable(0f) }
    val sysNameReveal = remember { Animatable(0f) }
    val catchphraseMarker = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 1. パーツが四隅からカチッと集合（130msずつずらして開始、650ms移動）
        partDefs.forEachIndexed { index, _ ->
            launch {
                delay(index * 130L)
                launch {
                    partAlpha[index].animateTo(1f, tween(300, easing = LinearEasing))
                }
                partProgress[index].animateTo(1f, tween(650, easing = SharpEasing))
            }
        }
        val assembleEnd = 650L + (partDefs.size - 1) * 130L
        delay(assembleEnd)

        // 2. パーツを消してロゴへ瞬時に切り替え（フェードではなく素早いopacity切替）
        partAlpha.forEach { it.animateTo(0f, tween(160, easing = LinearEasing)) }
        showLogo = true

        // 3. アンダーラインが伸びる
        delay(220)
        underlineWidth.animateTo(48f * SCALE, tween(320, easing = SharpEasing))

        // 4. システム名が左から右へ滑らかにワイプ表示（clip-pathのような出現）
        delay(260)
        sysNameReveal.animateTo(1f, tween(600, easing = FastOutSlowInEasing))

        // 5. マーカーが左から右へ伸び、その帯に合わせてキャッチコピーもワイプで滑らかに現れる
        delay(420)
        catchphraseMarker.animateTo(1f, tween(700, easing = FastOutSlowInEasing))

        // 6. 少し余韻を置いてフェードアウトしながらメイン画面へ
        delay(1300)
        finishWithFadeOut()
    }

    // 実ロゴ画像のサイズ（実寸比率を保ったまま表示）と、ロゴ下端からテキスト先頭までの間隔（2倍サイズ）
    val logoWidth = 560.dp
    val logoHeight = logoWidth * LOGO_ASPECT
    val logoTextGap = 56.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(onClick = { scope.launch { finishWithFadeOut() } })
    ) {
        val centerX = maxWidth / 2
        // ロゴ＋テキスト全体のかたまりが画面の縦中央に来るよう、ロゴ中心をやや上に置く
        val centerY = maxHeight / 2 - 180.dp

        // タップ/自動終了時、白背景は残したまま中身だけをフェードアウトさせる
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value }
        ) {

        // ---- パーツ ----
        partDefs.forEachIndexed { index, def ->
            val progress = partProgress[index].value
            val alpha = partAlpha[index].value
            val offsetX = centerX + ((def.start.x + (def.end.x - def.start.x) * progress)).dp
            val offsetY = centerY + ((def.start.y + (def.end.y - def.start.y) * progress)).dp
            val rotation = 45f * (1f - progress)
            val scale = 0.6f + 0.4f * progress
            val partSize = 30.dp * SCALE

            Canvas(
                modifier = Modifier
                    .offset(x = offsetX - partSize / 2, y = offsetY - partSize / 2)
                    .size(partSize)
            ) {
                if (alpha > 0f) {
                    rotate(rotation) {
                        scale(scale) {
                            drawPath(
                                path = def.shape(size.width),
                                color = def.color.copy(alpha = alpha)
                            )
                        }
                    }
                }
            }
        }

        // ---- ロゴ（実画像） ----
        if (showLogo) {
            Image(
                painter = painterResource(id = R.drawable.nse_logo),
                contentDescription = "NSエンジニアリング ロゴ",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .offset(x = centerX - logoWidth / 2, y = centerY - logoHeight / 2)
                    .size(width = logoWidth, height = logoHeight),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = centerY + logoHeight / 2 + logoTextGap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- アンダーライン ----
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(underlineWidth.value.dp)
                    .background(UnderlineColor)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(80.dp))

            // ---- システム名（左から右へワイプ表示） ----
            androidx.compose.material3.Text(
                text = SYSTEM_NAME,
                color = SysNameColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                modifier = Modifier.clipReveal(sysNameReveal.value)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(56.dp))

            // ---- キャッチコピー（マーカー帯＋その帯に合わせたワイプ表示） ----
            Box(
                modifier = Modifier
                    .drawBehind {
                        // マーカーで引いたような青い帯を、文字の少し後ろに左から右へ伸ばす（水平・直線）
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(CatchphraseMarkerStart, CatchphraseMarkerEnd)
                            ),
                            topLeft = Offset(0f, size.height * 0.18f),
                            size = Size(size.width * catchphraseMarker.value, size.height * 0.64f),
                            cornerRadius = CornerRadius(8.dp.toPx()),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    text = CATCHPHRASE,
                    color = Color.White,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clipReveal(catchphraseMarker.value),
                )
            }
        }
        }
    }
}
