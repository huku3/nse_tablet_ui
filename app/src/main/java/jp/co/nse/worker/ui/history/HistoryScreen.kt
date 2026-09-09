package jp.co.nse.worker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.HistoryItemDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.tasklist.StatusChip
import jp.co.nse.worker.ui.theme.Emerald500
import jp.co.nse.worker.ui.theme.Green700
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 対象日の実績集計 */
private data class DayStat(val count: Int, val minutes: Int)

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
private fun recentTabDates(holidays: Set<String>, overrides: Set<String>): List<LocalDate> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenTask: (processId: Int) -> Unit,
    onLogout: () -> Unit = {},
) {
    val feedback = rememberClickFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(container.workerRepository) } }
    )
    val userName = rememberCurrentUserName()

    LaunchedEffect(Unit) { vm.load() }

    val tabDates = remember(vm.holidayDates, vm.overrideDates) {
        recentTabDates(vm.holidayDates, vm.overrideDates)
    }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = remember { LocalDate.now() }
    val effectiveSelected = selectedDate ?: today

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("作業実績") },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    MyPageButton()
                    IconButton(onClick = { feedback(); vm.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新", tint = Color.White)
                    }
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                vm.loading && vm.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null && vm.items.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                }

                else -> HistoryDayView(
                    items = vm.items,
                    tabDates = tabDates,
                    today = today,
                    selectedDate = effectiveSelected,
                    onSelectDate = { selectedDate = it },
                    onOpenTask = onOpenTask,
                )
            }
        }
    }
}

@Composable
private fun HistoryDayView(
    items: List<HistoryItemDto>,
    tabDates: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onOpenTask: (Int) -> Unit,
) {
    val feedback = rememberClickFeedback()

    // 日付ごとに集計
    val statsByDate: Map<LocalDate, DayStat> = remember(items) {
        items
            .mapNotNull { item -> DateUtil.localDateFromIso(item.completed_at)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> DayStat(count = list.size, minutes = list.sumOf { it.work_minutes ?: 0 }) }
    }
    val itemsByDate: Map<LocalDate, List<HistoryItemDto>> = remember(items) {
        items
            .mapNotNull { item -> DateUtil.localDateFromIso(item.completed_at)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
    }

    val selectedIndex = tabDates.indexOf(selectedDate).coerceAtLeast(0)
    val previousDate = tabDates.getOrNull(selectedIndex + 1)
    val selectedStat = statsByDate[selectedDate] ?: DayStat(0, 0)
    val previousStat = previousDate?.let { statsByDate[it] ?: DayStat(0, 0) }
    val selectedItems = itemsByDate[selectedDate].orEmpty().sortedByDescending { it.completed_at }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "tabs") {
                DayTabRow(
                    dates = tabDates,
                    today = today,
                    selectedDate = selectedDate,
                    onSelect = { onSelectDate(it) },
                )
            }

            item(key = "summary") {
                DaySummaryCard(
                    date = selectedDate,
                    today = today,
                    stat = selectedStat,
                    previousStat = previousStat,
                )
            }

            if (selectedItems.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("この日の完了実績はありません", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                items(selectedItems, key = { it.id }) { item ->
                    HistoryCard(item = item, onClick = { feedback(); onOpenTask(item.id) })
                }
            }
        }
        ScrollToTopFab(
            visible = listState.firstVisibleItemIndex > 0,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun DayTabRow(
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White)
                    .then(
                        if (!isSelected) {
                            Modifier
                        } else {
                            Modifier
                        }
                    )
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
private fun DaySummaryCard(
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
private fun HistoryCard(item: HistoryItemDto, onClick: () -> Unit) {
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
