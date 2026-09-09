package jp.co.nse.worker.ui.assignment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import java.time.LocalDate
import java.time.YearMonth

private val WeekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")
private val CellSize = 40.dp

/**
 * 工程納期をカレンダーで選ぶダイアログ。
 * 客先納期（[maxDate]）を赤で強調し、前工程の工程納期（[minDate]）より前・客先納期より後・
 * 非稼働日（[holidayDates] / [overrideDates] から判定）はタップ時にエラーメッセージで知らせる。
 * バリデーションの最終判断はサーバー側（生産管理システムと同一ロジック）で行われるため、
 * ここでのチェックは入力ミスを事前に防ぐためのものという位置づけ。
 */
@Composable
fun DeadlineCalendarDialog(
    order: OrderAssignDto,
    processName: String,
    initialDate: LocalDate?,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    holidayDates: Set<String>,
    overrideDates: Set<String>,
    saving: Boolean,
    serverError: String?,
    onVisibleMonthChanged: (YearMonth) -> Unit,
    onConfirm: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    var visibleMonth by remember {
        mutableStateOf(YearMonth.from(initialDate ?: maxDate ?: LocalDate.now()))
    }
    var selected by remember { mutableStateOf(initialDate) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visibleMonth) { onVisibleMonthChanged(visibleMonth) }

    fun tryPick(date: LocalDate) {
        localError = when {
            minDate != null && date < minDate ->
                "工程納期は前工程の工程納期（${DateUtil.shortLabel(minDate)}）より前に設定できません"
            maxDate != null && date > maxDate ->
                "工程納期は客先納期（${DateUtil.shortLabel(maxDate)}）より後に設定できません"
            !DateUtil.isWorkingDay(date, holidayDates, overrideDates) ->
                "この日は休日（日曜・休業日）のため選択できません"
            else -> null
        }
        if (localError == null) {
            feedback()
            selected = date
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("「$processName」の工程納期", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OrderContextCard(order)
                Spacer(Modifier.height(12.dp))

                // ---- 月移動 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { feedback(); visibleMonth = visibleMonth.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "前の月")
                    }
                    Text(
                        "${visibleMonth.year}年${visibleMonth.monthValue}月",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    IconButton(onClick = { feedback(); visibleMonth = visibleMonth.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "次の月")
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---- 客先納期の凡例 ----
                if (maxDate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .border(2.dp, Color(0xFFEF4444), CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "客先納期（${DateUtil.shortLabel(maxDate)}）",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // ---- 曜日ヘッダー ----
                Row(modifier = Modifier.width(CellSize * 7)) {
                    WeekdayLabels.forEachIndexed { index, label ->
                        Box(Modifier.width(CellSize), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (index) {
                                    0 -> Color(0xFFDC2626)
                                    6 -> Color(0xFF2563EB)
                                    else -> Color(0xFF6B7280)
                                },
                            )
                        }
                    }
                }

                // ---- 日付グリッド ----
                val firstDay = visibleMonth.atDay(1)
                val leadingBlanks = firstDay.dayOfWeek.value % 7
                val cells: List<LocalDate?> = List(leadingBlanks) { null } +
                    (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }

                cells.chunked(7).forEach { week ->
                    Row {
                        week.forEach { date ->
                            Box(
                                modifier = Modifier.size(CellSize).padding(2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (date != null) {
                                    DayCell(
                                        date = date,
                                        isSelected = date == selected,
                                        isToday = date == LocalDate.now(),
                                        isCustomerDate = date == maxDate,
                                        isOutOfRange = (minDate != null && date < minDate) ||
                                            (maxDate != null && date > maxDate),
                                        isHoliday = !DateUtil.isWorkingDay(date, holidayDates, overrideDates),
                                        onClick = { tryPick(date) },
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.size(CellSize)) }
                    }
                }

                Spacer(Modifier.height(8.dp))

                (localError ?: serverError)?.let {
                    Text(
                        it,
                        color = Color(0xFFB91C1C),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }

                selected?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "選択中: ${DateUtil.shortLabel(it)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                TextButton(onClick = { feedback(); onConfirm(selected) }, enabled = selected != null) {
                    Text("この日に設定", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (initialDate != null) {
                    TextButton(onClick = { feedback(); onConfirm(null) }, enabled = !saving) {
                        Text("未設定に戻す", color = Color(0xFF9CA3AF))
                    }
                }
                TextButton(onClick = { feedback(); onDismiss() }, enabled = !saving) {
                    Text("キャンセル", color = Color(0xFF6B7280))
                }
            }
        },
    )
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isCustomerDate: Boolean,
    isOutOfRange: Boolean,
    isHoliday: Boolean,
    onClick: () -> Unit,
) {
    val textColor = when {
        isSelected -> Color.White
        isOutOfRange -> Color(0xFFD1D5DB)
        isCustomerDate -> Color(0xFFDC2626)
        isHoliday -> Color(0xFFF59E0B)
        date.dayOfWeek == java.time.DayOfWeek.SUNDAY -> Color(0xFFDC2626)
        date.dayOfWeek == java.time.DayOfWeek.SATURDAY -> Color(0xFF2563EB)
        else -> Color(0xFF1F2937)
    }
    Box(
        modifier = Modifier
            .size(CellSize - 4.dp)
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(
                if (isCustomerDate && !isSelected) {
                    Modifier.border(2.dp, Color(0xFFEF4444), CircleShape)
                } else if (isToday && !isSelected) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isCustomerDate) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
