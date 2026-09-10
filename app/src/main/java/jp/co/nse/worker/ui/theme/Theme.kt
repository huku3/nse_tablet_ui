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

/**
 * 色系統ごとに薄い→濃いの順で5色ずつ並べる（合計約50色）。
 * 単なる色名ではなく、日本の伝統色・世界の伝統色から名前を選んでいる。
 */
val AccentPresets = listOf(
    // レッド系
    AccentPreset("珊瑚色", "#FCA5A5", Color(0xFFFCA5A5)),
    AccentPreset("紅梅色", "#F87171", Color(0xFFF87171)),
    AccentPreset("紅色", "#EF4444", Color(0xFFEF4444)),
    AccentPreset("茜色", "#DC2626", Color(0xFFDC2626)),
    AccentPreset("臙脂色", "#B91C1C", Color(0xFFB91C1C)),
    // オレンジ系
    AccentPreset("杏色", "#FDBA74", Color(0xFFFDBA74)),
    AccentPreset("柿色", "#FB923C", Color(0xFFFB923C)),
    AccentPreset("蜜柑色", "#F97316", Color(0xFFF97316)),
    AccentPreset("橙色", "#EA580C", Color(0xFFEA580C)),
    AccentPreset("煉瓦色", "#C2410C", Color(0xFFC2410C)),
    // イエロー系
    AccentPreset("卵色", "#FDE68A", Color(0xFFFDE68A)),
    AccentPreset("山吹色", "#FCD34D", Color(0xFFFCD34D)),
    AccentPreset("レモンイエロー", "#FFF33F", Color(0xFFFFF33F)),
    AccentPreset("蒲公英色", "#F59E0B", Color(0xFFF59E0B)),
    AccentPreset("芥子色", "#B45309", Color(0xFFB45309)),
    // グリーン系
    AccentPreset("若草色", "#86EFAC", Color(0xFF86EFAC)),
    AccentPreset("萌黄色", "#4ADE80", Color(0xFF4ADE80)),
    AccentPreset("若緑", "#22C55E", Green500),
    AccentPreset("常磐色", "#16A34A", Green600),
    AccentPreset("千歳緑", "#15803D", Green700),
    // ティール系
    AccentPreset("水浅葱", "#5EEAD4", Color(0xFF5EEAD4)),
    AccentPreset("青緑", "#2DD4BF", Color(0xFF2DD4BF)),
    AccentPreset("青竹色", "#14B8A6", Color(0xFF14B8A6)),
    AccentPreset("錆浅葱", "#0D9488", Color(0xFF0D9488)),
    AccentPreset("鉄色", "#115E59", Color(0xFF115E59)),
    // スカイ系
    AccentPreset("水色", "#7DD3FC", Color(0xFF7DD3FC)),
    AccentPreset("瓶覗", "#38BDF8", Color(0xFF38BDF8)),
    AccentPreset("スカイブルー", "#0EA5E9", Color(0xFF0EA5E9)),
    AccentPreset("花浅葱", "#0284C7", Color(0xFF0284C7)),
    AccentPreset("納戸色", "#075985", Color(0xFF075985)),
    // ブルー系
    AccentPreset("勿忘草色", "#93C5FD", Color(0xFF93C5FD)),
    AccentPreset("露草色", "#60A5FA", Color(0xFF60A5FA)),
    AccentPreset("群青色", "#3B82F6", Color(0xFF3B82F6)),
    AccentPreset("瑠璃色", "#2563EB", Color(0xFF2563EB)),
    AccentPreset("紺碧", "#1D4ED8", Color(0xFF1D4ED8)),
    // インディゴ系
    AccentPreset("桔梗色", "#A5B4FC", Color(0xFFA5B4FC)),
    AccentPreset("菫色", "#818CF8", Color(0xFF818CF8)),
    AccentPreset("江戸紫", "#6366F1", Indigo600),
    AccentPreset("紺青色", "#4338CA", Indigo700),
    AccentPreset("濃紺", "#3730A3", Color(0xFF3730A3)),
    // パープル系
    AccentPreset("ラベンダー", "#C4B5FD", Color(0xFFC4B5FD)),
    AccentPreset("藤色", "#A78BFA", Color(0xFFA78BFA)),
    AccentPreset("菖蒲色", "#8B5CF6", Color(0xFF8B5CF6)),
    AccentPreset("紫色", "#7C3AED", Color(0xFF7C3AED)),
    AccentPreset("濃色", "#6D28D9", Color(0xFF6D28D9)),
    // ピンク系
    AccentPreset("桜色", "#F9A8D4", Color(0xFFF9A8D4)),
    AccentPreset("桃色", "#F472B6", Color(0xFFF472B6)),
    AccentPreset("牡丹色", "#EC4899", Color(0xFFEC4899)),
    AccentPreset("躑躅色", "#DB2777", Color(0xFFDB2777)),
    AccentPreset("韓紅", "#BE185D", Color(0xFFBE185D)),
    // ニュートラル系（グレー・黒）
    AccentPreset("銀鼠", "#9CA3AF", Color(0xFF9CA3AF)),
    AccentPreset("鈍色", "#6B7280", Color(0xFF6B7280)),
    AccentPreset("墨色", "#4B5563", Color(0xFF4B5563)),
    AccentPreset("アイボリーブラック", "#292421", Color(0xFF292421)),
    AccentPreset("漆黒", "#1C1917", Color(0xFF1C1917)),
)

/**
 * メイン色の上に乗せる文字・アイコンの色。選んだ色によって白／濃いグレーが入れ替わると
 * 見た目がアカウントごとにばらつくため、どの色を選んでも常に白で統一する。
 */
fun inkFor(@Suppress("UNUSED_PARAMETER") background: Color): Color = Color.White

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
