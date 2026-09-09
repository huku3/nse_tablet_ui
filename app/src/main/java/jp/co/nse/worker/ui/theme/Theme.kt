package jp.co.nse.worker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ブランドグリーン（アプリアイコンと統一）
val Green500 = Color(0xFF22C55E)
val Green600 = Color(0xFF16A34A)
val Green700 = Color(0xFF15803D)
val Green50 = Color(0xFFF0FDF4)

// Webのindigo系トーンに合わせたカラー
val Indigo600 = Color(0xFF4F46E5)
val Indigo700 = Color(0xFF4338CA)
val Indigo50 = Color(0xFFEEF2FF)
val Emerald500 = Color(0xFF10B981)
val Emerald50 = Color(0xFFECFDF5)
val Amber500 = Color(0xFFF59E0B)
val Orange400 = Color(0xFFFB923C)
val Red500 = Color(0xFFEF4444)
val Red50 = Color(0xFFFEF2F2)
val Slate700 = Color(0xFF334155)
val Gray100 = Color(0xFFF3F4F6)
val Gray500 = Color(0xFF6B7280)
val Gray800 = Color(0xFF1F2937)

/** 個人（アカウント）ごとに選べるメイン色。デフォルトは出荷カレンダー画面と同じブルー */
data class AccentPreset(val label: String, val hex: String, val color: Color)

val DefaultAccentHex = "#4338CA"

val AccentPresets = listOf(
    AccentPreset("ブルー", "#4338CA", Indigo700),
    AccentPreset("グリーン", "#16A34A", Green600),
    AccentPreset("レモンイエロー", "#FFF33F", Color(0xFFFFF33F)),
    AccentPreset("オレンジ", "#EA580C", Color(0xFFEA580C)),
    AccentPreset("レッド", "#DC2626", Color(0xFFDC2626)),
    AccentPreset("パープル", "#7C3AED", Color(0xFF7C3AED)),
    AccentPreset("ティール", "#0D9488", Color(0xFF0D9488)),
    AccentPreset("アイボリーブラック", "#292421", Color(0xFF292421)),
)

/**
 * メイン色の上に乗せる文字・アイコンの色。レモンイエローのような明るい色では白文字が
 * 読めなくなるため、背景の明度を見て白／濃いグレーのどちらか読みやすい方を選ぶ。
 */
fun inkFor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Gray800 else Color.White
}

private fun lightColorsFor(accent: Color): androidx.compose.material3.ColorScheme {
    val ink = inkFor(accent)
    return lightColorScheme(
        primary = accent,
        onPrimary = ink,
        primaryContainer = accent.copy(alpha = 0.12f),
        onPrimaryContainer = accent,
        secondary = Slate700,
        secondaryContainer = accent.copy(alpha = 0.14f),
        onSecondaryContainer = accent,
        background = Gray100,
        onBackground = Gray800,
        surface = Color.White,
        onSurface = Gray800,
        error = Red500,
    )
}

/** [accentColor] は現在ログイン中のアカウントのマイページで選んだメイン色（未選択時は出荷カレンダーと同じブルー） */
@Composable
fun NseWorkerTheme(accentColor: Color = Indigo700, content: @Composable () -> Unit) {
    // 工場利用のため常にライトテーマ
    MaterialTheme(
        colorScheme = lightColorsFor(accentColor),
        typography = Typography(),
        content = content,
    )
}
