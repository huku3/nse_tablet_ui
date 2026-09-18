package jp.co.nse.worker.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.data.OrderBriefDto
import jp.co.nse.worker.data.ShippingCalendarOrderDto
import jp.co.nse.worker.ui.components.OrderStatusBadge
import jp.co.nse.worker.ui.theme.inkFor

/** 材料入荷状況・出荷予定のカードで、選択した日の受注を表示する際に共通で使う1件分の表示用データ */
data class OrderTableRow(
    val id: Int,
    val poNumber: String?,
    val partName: String?,
    val quantity: Int?,
    val status: String?,
    val customerName: String?,
    // 材料入荷状況カードでのみ使う材料情報。出荷予定側（ShippingCalendarOrderDto）には
    // 対応する情報が無いため常にnull
    val materialName: String? = null,
    val materialSize: String? = null,
    val materialSupplier: String? = null,
    // 「本日の担当作業」「工程納期」カードでのみ使う、同じ受注内での担当工程名。
    // 1つの受注に複数工程を担当していることがあるため、どの工程の分か分かるようにする
    val processName: String? = null,
)

fun OrderAssignDto.toTableRow() = OrderTableRow(
    id = id,
    poNumber = po_number,
    partName = part_name,
    quantity = quantity,
    status = status,
    customerName = customer_name,
    materialName = material_name,
    materialSize = material_size,
    materialSupplier = material_supplier,
)

fun ShippingCalendarOrderDto.toTableRow() = OrderTableRow(id, po_number, part_name, quantity, status, customer_name)

/** 「本日の担当作業」カードの各件が持つ受注情報（客先名は含まれないため常にnull） */
fun OrderBriefDto.toTableRow() = OrderTableRow(id, po_number, part_name, quantity, status, customerName = null)

// 材料入荷状況カードで材料名・材料サイズ・材料商社をカラム表示する際の列幅
private val MaterialNameColumnWidth = 100.dp
private val MaterialSizeColumnWidth = 90.dp
private val MaterialSupplierColumnWidth = 100.dp
private val PartColumnWidth = 150.dp
private val CustomerColumnWidth = 96.dp
private val QuantityColumnWidth = 48.dp
private val StatusColumnWidth = 92.dp
private val ChevronColumnWidth = 20.dp

/**
 * ダッシュボードの「材料入荷状況」「出荷予定」カードで日付を選択したときに、
 * 該当する受注をカード内にそのまま一覧表示するテーブル。
 * ダッシュボード全体がLazyColumnのため、ネストしたLazyColumnにはせずColumnで組む
 * （選択日1日分の件数は少数のため、これで十分表示できる）。
 * 行をタップするとその受注の工程管理チェックシートへ遷移できるようにしている。
 *
 * [showMaterialColumn]がtrueの場合（材料入荷状況カード）は材料名・材料サイズ・材料商社も
 * 客先・数量・状態と同じ並びのカラムとして表示する。列数が増え画面幅に収まらないことがあるため、
 * この場合はheaderと各行をまとめて横スクロールできるようにし、列は等分（weight）ではなく
 * 固定幅にしてheaderと行のカラム位置がずれないようにしている。
 */
@Composable
fun OrderInlineTable(
    rows: List<OrderTableRow>,
    modifier: Modifier = Modifier,
    onRowClick: (orderId: Int) -> Unit = {},
    showMaterialColumn: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Text(
                "該当する受注はありません",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                fontSize = 13.sp,
                color = Color(0xFF9CA3AF),
            )
            return@Column
        }
        if (showMaterialColumn) {
            MaterialOrderTable(rows, onRowClick)
        } else {
            SimpleOrderTable(rows, onRowClick)
        }
    }
}

@Composable
private fun SimpleOrderTable(rows: List<OrderTableRow>, onRowClick: (orderId: Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) {
        TableHeaderCell("品番・品名", Modifier.weight(1f))
        TableHeaderCell("客先", Modifier.weight(0.8f))
        TableHeaderCell("数量", Modifier.width(QuantityColumnWidth))
        TableHeaderCell("状態", Modifier.width(StatusColumnWidth))
        Spacer(Modifier.width(ChevronColumnWidth))
    }
    HorizontalDivider(color = Color(0xFFE5E7EB))
    rows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRowClick(row.id) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                // 1つの受注に複数工程を担当していることがあるため、どの工程の分か分かるようにする
                row.processName?.let {
                    Text(it, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Text(row.partName ?: "—", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                row.poNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                }
            }
            Text(
                row.customerName ?: "—",
                modifier = Modifier.weight(0.8f),
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
            )
            Text(row.quantity?.let { "$it" } ?: "—", modifier = Modifier.width(QuantityColumnWidth), fontSize = 13.sp)
            OrderStatusBadge(row.status, modifier = Modifier.width(StatusColumnWidth))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(ChevronColumnWidth),
            )
        }
        HorizontalDivider(color = Color(0xFFF3F4F6))
    }
}

@Composable
private fun MaterialOrderTable(rows: List<OrderTableRow>, onRowClick: (orderId: Int) -> Unit) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
            TableHeaderCell("品番・品名", Modifier.width(PartColumnWidth))
            TableHeaderCell("材料名", Modifier.width(MaterialNameColumnWidth))
            TableHeaderCell("材料サイズ", Modifier.width(MaterialSizeColumnWidth))
            TableHeaderCell("材料商社", Modifier.width(MaterialSupplierColumnWidth))
            TableHeaderCell("客先", Modifier.width(CustomerColumnWidth))
            TableHeaderCell("数量", Modifier.width(QuantityColumnWidth))
            TableHeaderCell("状態", Modifier.width(StatusColumnWidth))
            Spacer(Modifier.width(ChevronColumnWidth))
        }
        HorizontalDivider(color = Color(0xFFE5E7EB))
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .clickable { onRowClick(row.id) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(PartColumnWidth)) {
                    Text(row.partName ?: "—", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    row.poNumber?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    }
                }
                TableCell(row.materialName, Modifier.width(MaterialNameColumnWidth))
                TableCell(row.materialSize, Modifier.width(MaterialSizeColumnWidth))
                TableCell(row.materialSupplier, Modifier.width(MaterialSupplierColumnWidth))
                TableCell(row.customerName, Modifier.width(CustomerColumnWidth))
                Text(row.quantity?.let { "$it" } ?: "—", modifier = Modifier.width(QuantityColumnWidth), fontSize = 13.sp)
                OrderStatusBadge(row.status, modifier = Modifier.width(StatusColumnWidth))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(ChevronColumnWidth),
                )
            }
            HorizontalDivider(color = Color(0xFFF3F4F6))
        }
    }
}

@Composable
private fun TableCell(text: String?, modifier: Modifier = Modifier) {
    Text(
        text?.takeIf { it.isNotBlank() } ?: "—",
        modifier = modifier.padding(end = 4.dp),
        fontSize = 12.sp,
        color = Color(0xFF6B7280),
        maxLines = 2,
    )
}

@Composable
private fun TableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF9CA3AF),
    )
}

/** 「担当工程状況」カードで使う、1工程分のチップ表示データ。[isMine]の工程だけメインカラーで塗りつぶす */
data class ProcessChip(val name: String, val isMine: Boolean)

/**
 * 「担当工程状況」カードの1受注分の表示データ。同じ受注で複数工程を担当していても
 * 受注ごとに1行にまとめ、その受注の全工程を横並びのチップで見せる（[ProcessChip.isMine]で強調）。
 * 客先名はこのカードの目的（自分がどの工程を担当しているか）には不要なので持たない。
 */
data class OrderPipelineRow(
    val orderId: Int,
    val partName: String?,
    val poNumber: String?,
    val quantity: Int?,
    val status: String?,
    val processes: List<ProcessChip>,
)

// 担当工程状況カードの列幅（品番・品名は工程チップと同じ行に並べるため固定幅にする）
private val PipelinePartColumnWidth = 130.dp

/**
 * 「担当工程状況」カードで使う、受注ごとに全工程を横並びのチップで見せるテーブル。
 * 列名が無いと数量などの数字だけが並んで分かりにくいため、他の受注一覧と同じヘッダー行を付ける。
 * 品名・数量・状態・工程チップはすべて同じ行に並べ、ヘッダーごとテーブル全体を横スクロールできる
 * ようにする（[MaterialOrderTable]と同じ考え方）。
 */
@Composable
fun OrderPipelineTable(
    rows: List<OrderPipelineRow>,
    modifier: Modifier = Modifier,
    onRowClick: (orderId: Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        if (rows.isEmpty()) {
            Text(
                "該当する受注はありません",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                fontSize = 13.sp,
                color = Color(0xFF9CA3AF),
            )
            return@Column
        }
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                TableHeaderCell("品番・品名", Modifier.width(PipelinePartColumnWidth))
                TableHeaderCell("数量", Modifier.width(QuantityColumnWidth))
                TableHeaderCell("状態", Modifier.width(StatusColumnWidth))
                TableHeaderCell("工程（担当のみ色付き）", Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(color = Color(0xFFE5E7EB))
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = Color(0xFFF3F4F6))
                Row(
                    modifier = Modifier
                        .clickable { onRowClick(row.orderId) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(PipelinePartColumnWidth)) {
                        Text(row.partName ?: "—", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        row.poNumber?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                    Text(row.quantity?.let { "$it" } ?: "—", modifier = Modifier.width(QuantityColumnWidth), fontSize = 13.sp)
                    OrderStatusBadge(row.status, modifier = Modifier.width(StatusColumnWidth))
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.processes.forEach { chip -> ProcessChipView(chip) }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(ChevronColumnWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessChipView(chip: ProcessChip) {
    val bg = if (chip.isMine) MaterialTheme.colorScheme.primary else Color(0xFFF3F4F6)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            chip.name,
            fontSize = 12.sp,
            fontWeight = if (chip.isMine) FontWeight.Bold else FontWeight.Normal,
            color = if (chip.isMine) inkFor(bg) else Color(0xFF6B7280),
        )
    }
}
