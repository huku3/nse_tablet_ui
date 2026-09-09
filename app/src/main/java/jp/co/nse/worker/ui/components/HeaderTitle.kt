package jp.co.nse.worker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.util.DateUtil
import java.time.LocalDate

/**
 * 画面ヘッダー（TopAppBar）共通タイトル。
 * タイトルの下に本日の日付＋曜日（例: 9/4（金））を中央揃えで表示する。
 */
@Composable
fun HeaderTitle(title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            DateUtil.shortLabel(LocalDate.now()),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
        )
    }
}
