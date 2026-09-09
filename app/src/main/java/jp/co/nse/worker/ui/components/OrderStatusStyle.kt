package jp.co.nse.worker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 受注ステータスの表示スタイル（背景色・文字色・枠線色・日本語ラベル） */
data class OrderStatusStyle(val bg: Color, val text: Color, val border: Color, val label: String)

/** 受注ステータス（App\Models\Order::$statuses）の表示定義。工程管理チェックシート・作業一覧・作業詳細で共用する */
object OrderStatus {
    val styles = mapOf(
        "needs_review" to OrderStatusStyle(Color(0xFFF3E8FF), Color(0xFF7C3D99), Color(0xFFDDD6FE), "受注確認中"),
        "material_confirming" to OrderStatusStyle(Color(0xFFE0F7FA), Color(0xFF0E7490), Color(0xFFB2EBF2), "材料確認中"),
        "material_waiting" to OrderStatusStyle(Color(0xFFFEF3C7), Color(0xFFB45309), Color(0xFFFDE68A), "材料待ち"),
        "material_arrived_date" to OrderStatusStyle(Color(0xFFFFEDD5), Color(0xFF92400E), Color(0xFFFDBA74), "材料到着日"),
        "material_arrived" to OrderStatusStyle(Color(0xFFDCFCE7), Color(0xFF007B43), Color(0xFFBBF7D0), "材料到着済み"),
        "waiting" to OrderStatusStyle(Color(0xFFF5F5F5), Color(0xFF595959), Color(0xFFE5E5E5), "待機"),
        "in_progress" to OrderStatusStyle(Color(0xFFDCFCE7), Color(0xFF007B43), Color(0xFFBBF7D0), "製作中"),
        "shipping_wait" to OrderStatusStyle(Color(0xFFEBF0FA), Color(0xFF1351B4), Color(0xFFAFCAF4), "出荷待ち"),
        "shipped" to OrderStatusStyle(Color(0xFFF3F4F6), Color(0xFF767676), Color(0xFFE5E7EB), "出荷済み"),
        "billed" to OrderStatusStyle(Color(0xFFE0E7FF), Color(0xFF4338CA), Color(0xFFC7D2FE), "請求済み"),
        "stocked" to OrderStatusStyle(Color(0xFFFEF9C3), Color(0xFF854D0E), Color(0xFFFEF08A), "在庫済み"),
    )

    fun style(status: String?): OrderStatusStyle =
        styles[status] ?: OrderStatusStyle(Color(0xFFF5F5F5), Color(0xFF525252), Color(0xFFE5E5E5), status ?: "―")
}

/** 受注ステータスバッジ（丸みのある枠線付きラベル） */
@Composable
fun OrderStatusBadge(status: String?, modifier: Modifier = Modifier) {
    val style = OrderStatus.style(status)
    Text(
        style.label,
        color = style.text,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.bg)
            .border(1.dp, style.border, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
