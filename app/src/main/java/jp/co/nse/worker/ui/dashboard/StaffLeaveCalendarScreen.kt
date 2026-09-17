package jp.co.nse.worker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Indigo50
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class StaffLeaveCalendarViewModel(private val repo: ManagerRepository) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var yearMonth by mutableStateOf(YearMonth.now())
        private set
    var holidayDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var overrideDates by mutableStateOf<Set<String>>(emptySet())
        private set
    private val loadedFiscalYears = mutableSetOf<Int>()

    var leaveByDate by mutableStateOf<Map<LocalDate, List<String>>>(emptyMap())
        private set

    fun load(target: YearMonth = yearMonth) {
        yearMonth = target
        viewModelScope.launch {
            loading = true
            loadHolidaysIfNeeded(target)
            loadLeaves(target)
            loading = false
        }
    }

    private suspend fun loadLeaves(target: YearMonth) {
        when (val result = repo.leaves(target.atDay(1).toString(), target.atEndOfMonth().toString(), StaffLeaveDepartment)) {
            is ApiResult.Success -> {
                leaveByDate = result.data.days.mapNotNull { day ->
                    DateUtil.parse(day.date)?.let { date -> date to day.leaves.map(::leaveLabel) }
                }.toMap()
            }
            is ApiResult.Failure -> leaveByDate = emptyMap()
        }
    }

    private suspend fun loadHolidaysIfNeeded(target: YearMonth) {
        val fiscalYear = DateUtil.fiscalYearOf(target.atDay(1))
        if (fiscalYear in loadedFiscalYears) return
        when (val result = repo.holidayCalendar(fiscalYear)) {
            is ApiResult.Success -> {
                holidayDates = holidayDates + result.data.holidays
                overrideDates = overrideDates + result.data.overrides
                loadedFiscalYears += fiscalYear
            }
            is ApiResult.Failure -> { /* 取得失敗時は土日をそのまま非稼働扱いにする */ }
        }
    }
}

/**
 * ダッシュボードの「お休み状況」カードのタイトルをタップすると開く、月カレンダー表示。
 * 出荷カレンダー（ShippingCalendarScreen）と同じ月グリッド構成の閲覧専用版。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffLeaveCalendarScreen(
    onBack: () -> Unit,
    initialDate: LocalDate = LocalDate.now(),
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: StaffLeaveCalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { StaffLeaveCalendarViewModel(container.managerRepository) } },
    )
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(initialDate) }

    LaunchedEffect(Unit) { vm.load(YearMonth.from(initialDate)) }

    fun goToMonth(target: YearMonth) {
        feedback()
        selectedDate = if (target == YearMonth.from(today)) today else target.atDay(1)
        vm.load(target)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("お休み状況") },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { feedback(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        HeaderLogo()
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    HeaderOverflowMenu()
                    IconButton(onClick = { feedback(); vm.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
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
            if (vm.loading && vm.holidayDates.isEmpty() && vm.overrideDates.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            } else {
                StaffLeaveCalendarContent(
                    yearMonth = vm.yearMonth,
                    today = today,
                    selectedDate = selectedDate,
                    leaveByDate = vm.leaveByDate,
                    holidayDates = vm.holidayDates,
                    overrideDates = vm.overrideDates,
                    onSelectDate = { feedback(); selectedDate = it },
                    onPrevMonth = { goToMonth(vm.yearMonth.minusMonths(1)) },
                    onNextMonth = { goToMonth(vm.yearMonth.plusMonths(1)) },
                )
            }
        }
    }
}

/** 月の日付を、前後を空欄で埋めた7列の週リストに組み立てる */
private fun monthWeeks(yearMonth: YearMonth): List<List<LocalDate?>> {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val cells = mutableListOf<LocalDate?>()
    repeat(leadingBlanks) { cells += null }
    for (day in 1..yearMonth.lengthOfMonth()) cells += yearMonth.atDay(day)
    while (cells.size % 7 != 0) cells += null
    return cells.chunked(7)
}

@Composable
private fun StaffLeaveCalendarContent(
    yearMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    leaveByDate: Map<LocalDate, List<String>>,
    holidayDates: Set<String>,
    overrideDates: Set<String>,
    onSelectDate: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val weeks = remember(yearMonth) { monthWeeks(yearMonth) }
    // 有給取得状況は当月より前を見せる必要が無いため、前月ボタンは当月まで来たら無効化する
    val canGoPrev = remember(yearMonth) { yearMonth > YearMonth.now() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .widthIn(max = 736.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            CalendarMonthHeader(
                label = "${yearMonth.year}年${yearMonth.monthValue}月",
                canGoPrev = canGoPrev,
                onPrevMonth = onPrevMonth,
                onNextMonth = onNextMonth,
            )
            Spacer(Modifier.height(16.dp))
            CalendarWeekdayHeaderRow()
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                weeks.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { date ->
                            StaffLeaveDayCell(
                                modifier = Modifier.weight(1f),
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                leaveNames = date?.let { leaveByDate[it] }.orEmpty(),
                                holidayDates = holidayDates,
                                overrideDates = overrideDates,
                                onClick = { date?.let(onSelectDate) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(16.dp))

            SelectedDayLeavePanel(date = selectedDate, names = leaveByDate[selectedDate].orEmpty())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CalendarMonthHeader(label: String, canGoPrev: Boolean, onPrevMonth: () -> Unit, onNextMonth: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarNavButton(icon = Icons.Filled.ChevronLeft, contentDescription = "前月", enabled = canGoPrev, onClick = onPrevMonth)
            CalendarNavButton(icon = Icons.Filled.ChevronRight, contentDescription = "翌月", onClick = onNextMonth)
        }
    }
}

@Composable
private fun CalendarNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (enabled) Indigo50 else Color(0xFFF3F4F6))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.primary else Color(0xFFD1D5DB),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun CalendarWeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WeekdayLabels.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (index) {
                        0 -> Color(0xFFDC2626).copy(alpha = 0.75f)
                        6 -> Color(0xFF2563EB).copy(alpha = 0.75f)
                        else -> Color(0xFF6B7280)
                    },
                )
            }
        }
    }
}

@Composable
private fun StaffLeaveDayCell(
    modifier: Modifier,
    date: LocalDate?,
    isToday: Boolean,
    isSelected: Boolean,
    leaveNames: List<String>,
    holidayDates: Set<String>,
    overrideDates: Set<String>,
    onClick: () -> Unit,
) {
    if (date == null) {
        Box(modifier = modifier.aspectRatio(1f))
        return
    }

    val onLeave = leaveNames.isNotEmpty()
    val dayOfWeek = date.dayOfWeek
    val isNonWorkingDay = !DateUtil.isWorkingDay(date, holidayDates, overrideDates)
    val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
    val isNonWorkingWeekend = isNonWorkingDay && isWeekend
    val isNonWorkingSunday = isNonWorkingWeekend && dayOfWeek == DayOfWeek.SUNDAY
    // 土日は色分けだけで休みだと分かるため、スタンプは平日の休業日（会社休日）にだけ表示する
    // （出荷カレンダーのHolidayStampと同じ考え方）
    val isWeekdayHoliday = isNonWorkingDay && !isWeekend

    val background = when {
        onLeave -> MaterialTheme.colorScheme.primary
        isSelected -> Indigo50
        isNonWorkingWeekend -> if (isNonWorkingSunday) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
        else -> Color.White
    }
    val dateColor = when {
        onLeave -> MaterialTheme.colorScheme.onPrimary
        isNonWorkingWeekend -> if (isNonWorkingSunday) Color(0xFFDC2626) else Color(0xFF2563EB)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        onLeave -> null
        isToday || isSelected -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFE5E7EB)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (borderColor != null) {
                    Modifier.border(if (isToday || isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            "${date.dayOfMonth}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = dateColor,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        )
        when {
            onLeave -> Text(
                "${leaveNames.size}名",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
            isWeekdayHoliday -> HolidayStamp(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** 工場休業日（休日マスタ参照）のスタンプ。出荷カレンダーのHolidayStampと同じ見た目 */
@Composable
private fun HolidayStamp(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.EventBusy,
        contentDescription = "休業日",
        tint = Color(0xFFDC2626),
        modifier = modifier.size(22.dp),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SelectedDayLeavePanel(date: LocalDate, names: List<String>) {
    val wd = WeekdayLabels[date.dayOfWeek.value % 7]
    Text(
        "${date.monthValue}月${date.dayOfMonth}日（$wd）の有給休暇取得者",
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(12.dp))
    if (names.isEmpty()) {
        Text("この日の有給休暇取得者はいません", fontSize = 14.sp, color = Color(0xFF9CA3AF))
    } else {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            names.forEach { name -> StaffLeaveNameChip(name) }
        }
    }
}

@Composable
private fun StaffLeaveNameChip(name: String) {
    Text(
        name,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
