package jp.co.nse.worker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.HistoryItemDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.tasklist.StatusChip
import jp.co.nse.worker.ui.theme.Emerald500
import jp.co.nse.worker.ui.theme.Green700
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 作業実績（マイページに埋め込み表示）で使う部品。
 * 以前は専用画面（HistoryScreen）だったが、マイページに直接表示する形に統合したため、
 * ここにはViewModelと再利用する部品だけを残している。
 */

/** 対象日の実績集計 */
data class DayStat(val count: Int, val minutes: Int)

class HistoryViewModel(private val repo: WorkerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var items by mutableStateOf<List<HistoryItemDto>>(emptyList())
        private set
    var holidayDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var overrideDates by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 稼働日計算のため、当日＋直近7稼働日分に十分な余裕を持って30日分取得する */
    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.history(30)) {
                is ApiResult.Success -> items = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
            loadHolidays()
        }
    }

    private suspend fun loadHolidays() {
        val today = LocalDate.now()
        val fiscalYears = setOf(
            DateUtil.fiscalYearOf(today),
            DateUtil.fiscalYearOf(today.minusDays(14)),
        )
        val holidays = mutableSetOf<String>()
        val overrides = mutableSetOf<String>()
        fiscalYears.forEach { fy ->
            when (val result = repo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidays += result.data.holidays
                    overrides += result.data.overrides
                }
                is ApiResult.Failure -> { /* 取得失敗時は土日のみで稼働日判定する */ }
            }
        }
        holidayDates = holidays
        overrideDates = overrides
    }
}

/** 本日＋稼働日計算での直近7稼働日（計8日）を新しい順で返す */
fun recentTabDates(holidays: Set<String>, overrides: Set<String>): List<LocalDate> {
    val today = LocalDate.now()
    val pastWorkingDays = mutableListOf<LocalDate>()
    var cursor = today.minusDays(1)
    // 長い連休があっても確実に7日分集まるよう、上限を設けて安全に走査する
    var guard = 0
    while (pastWorkingDays.size < 7 && guard < 60) {
        if (DateUtil.isWorkingDay(cursor, holidays, overrides)) {
            pastWorkingDays.add(cursor)
        }
        cursor = cursor.minusDays(1)
        guard++
    }
    return listOf(today) + pastWorkingDays
}

@Composable
fun DayTabRow(
    dates: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val feedback = rememberClickFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dates.forEach { date ->
            val isSelected = date == selectedDate
            val isToday = date == today
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
                    .clickable { feedback(); onSelect(date) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = if (isToday) "本日" else DateUtil.shortLabel(date),
                    color = if (isSelected) Color.White else Color(0xFF374151),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
fun DaySummaryCard(
    date: LocalDate,
    today: LocalDate,
    stat: DayStat,
    previousStat: DayStat?,
) {
    val dateLabel = if (date == today) "本日（${DateUtil.shortLabel(date)}）" else DateUtil.shortLabel(date)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(dateLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SummaryCell(
                    label = "完了件数",
                    value = "${stat.count} 件",
                    delta = previousStat?.let { stat.count - it.count },
                    deltaUnit = "件",
                )
                SummaryCell(
                    label = "作業時間",
                    value = DateUtil.durationLabel(stat.minutes) ?: "0分",
                    delta = previousStat?.let { stat.minutes - it.minutes },
                    deltaUnit = "分",
                )
            }
            if (previousStat != null) {
                Spacer(Modifier.height(6.dp))
                Text("前稼働日比", fontSize = 10.sp, color = Color(0xFF9CA3AF))
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, delta: Int?, deltaUnit: String) {
    Column {
        Text(label, color = Color(0xFF6B7280), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        if (delta != null) {
            Spacer(Modifier.height(2.dp))
            val (arrow, color) = when {
                delta > 0 -> "▲" to Green700
                delta < 0 -> "▼" to Red500
                else -> "―" to Color(0xFF9CA3AF)
            }
            Text(
                "$arrow ${if (delta == 0) "変化なし" else "${kotlin.math.abs(delta)}$deltaUnit"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItemDto, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    Card(
        onClick = { feedback(); onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.order.part_name ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(status = WorkStatus.COMPLETED, color = Emerald500)
            }
            Spacer(Modifier.height(6.dp))
            Text(item.process_name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF6B7280))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                DateUtil.durationLabel(item.work_minutes)?.let { InfoLabel("作業時間", it, MaterialTheme.colorScheme.primary) }
                item.order.po_number?.let { InfoLabel("発注番号", it) }
                item.defect_count?.takeIf { it > 0 }?.let { InfoLabel("不良", "$it 個", Color(0xFFE11D48)) }
            }
            DateUtil.dateTimeFull(item.completed_at)?.let {
                Spacer(Modifier.height(8.dp))
                Text("$it 完了", fontSize = 12.sp, color = Color(0xFF9CA3AF))
            }
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String, valueColor: Color = Color(0xFF111827)) {
    Column {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = valueColor)
    }
}
