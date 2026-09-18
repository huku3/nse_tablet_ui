package jp.co.nse.worker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import jp.co.nse.worker.ui.AppNav
import jp.co.nse.worker.ui.theme.DefaultAccentHex
import jp.co.nse.worker.ui.theme.NseWorkerTheme
import jp.co.nse.worker.ui.theme.fontFamilyForKey
import jp.co.nse.worker.ui.update.UpdateChecker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // マイページで選んだメイン色（現在ログイン中のアカウントの分。未選択時は既定のブルー）
            val accentHex by appContainer.settings.accentColorFlow.collectAsState(initial = DefaultAccentHex)
            val accentColor = remember(accentHex) { parseHexColor(accentHex) ?: parseHexColor(DefaultAccentHex)!! }
            // マイページで選んだ文字の大きさの倍率。アプリ全体でfontSize（.sp）はすべて
            // LocalDensity.fontScaleに連動するため、ここで上書きするだけで個々の画面を
            // 直す必要なくアプリ全体の文字サイズを一括で変更できる
            val fontScale by appContainer.settings.fontScaleFlow.collectAsState(initial = 1.0f)
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity, fontScale) {
                Density(density = baseDensity.density, fontScale = fontScale)
            }
            // マイページで選んだ書体。端末標準を選んでいる場合はnull（これまで通りの見た目）
            val fontFamilyKey by appContainer.settings.fontFamilyFlow.collectAsState(initial = "system")
            val fontFamily = remember(fontFamilyKey) { fontFamilyForKey(fontFamilyKey) }
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                NseWorkerTheme(accentColor = accentColor, fontFamily = fontFamily) {
                    AppNav()
                    UpdateChecker()
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
