package jp.co.nse.worker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import jp.co.nse.worker.ui.AppNav
import jp.co.nse.worker.ui.theme.DefaultAccentHex
import jp.co.nse.worker.ui.theme.NseWorkerTheme
import jp.co.nse.worker.ui.update.UpdateChecker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // マイページで選んだメイン色（現在ログイン中のアカウントの分。未選択時は既定のブルー）
            val accentHex by appContainer.settings.accentColorFlow.collectAsState(initial = DefaultAccentHex)
            val accentColor = remember(accentHex) { parseHexColor(accentHex) ?: parseHexColor(DefaultAccentHex)!! }
            NseWorkerTheme(accentColor = accentColor) {
                AppNav()
                UpdateChecker()
            }
        }
    }
}

private fun parseHexColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
