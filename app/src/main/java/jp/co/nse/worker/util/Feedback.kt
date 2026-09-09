package jp.co.nse.worker.util

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 効果音（ピッ）再生用の ToneGenerator はアプリ全体で1個だけ生成し使い回す。
 * ToneGenerator の生成はAudioFlinker経由でネイティブ音声リソースを確保する重い処理のため、
 * 一覧の行（カード）ごとに毎回 remember { ToneGenerator(...) } していると、
 * LazyColumn がスクロール中に行を作り直すたびに生成・破棄が発生し、スクロールが
 * カクつく原因になる。
 */
private val sharedTone: ToneGenerator? by lazy {
    runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
}

/**
 * ボタン押下時に「ピッ」という音＋振動でフィードバックを返すためのヘルパー。
 *
 * 返り値のラムダを onClick の先頭で呼ぶと、押した実感（音＋バイブ）が出る。
 * 音はメディア音量、振動は端末の触覚設定に依存する。どちらかが無効でも例外で
 * 落ちないよう保護している。
 */
@Composable
fun rememberClickFeedback(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) {
        {
            runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            runCatching { sharedTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        }
    }
}
