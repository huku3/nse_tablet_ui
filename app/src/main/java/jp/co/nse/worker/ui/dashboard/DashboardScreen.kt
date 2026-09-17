package jp.co.nse.worker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.AnnouncementDto
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.LeaveEntryDto
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.NotificationPreviewCard
import jp.co.nse.worker.ui.components.ScrollToBottomFab
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.FlowerOfDay
import jp.co.nse.worker.util.FlowerOfDayEntry
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** ダッシュボードに表示する「本日の担当作業」件数サマリー */
data class TaskSummary(val waiting: Int = 0, val inProgress: Int = 0, val completed: Int = 0)

/** 「本日の担当作業」カードで、どの件数をタップして受注テーブルを開いているか */
enum class TaskStatCategory { WAITING, IN_PROGRESS, COMPLETED }

/** ダッシュボードに表示する日別の工程納期件数（本日・明日・明後日、未完了の担当工程が対象） */
data class TaskDeadlineDaySummary(val date: LocalDate, val count: Int)

/** ダッシュボードに表示する日別の材料到着予定件数（本日・翌稼働日・翌々稼働日、割り当て権限がある場合のみ取得） */
data class MaterialWaitingDaySummary(val date: LocalDate, val count: Int)

/** ダッシュボードに表示する日別の出荷予定件数（本日・翌日・翌々日） */
data class ShippingDaySummary(val date: LocalDate, val count: Int)

/** ダッシュボードに表示する日別の有給休暇取得者一覧（直近30日のうち表示対象の日だけ） */
data class StaffLeaveDaySummary(val date: LocalDate, val staffNames: List<String>)

/** 「お休み状況」カード・カレンダーの対象課。この端末は精密部品製造課のみを扱う */
const val StaffLeaveDepartment = "精密部品製造課"

val WeekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

/**
 * 候補日から「お休み状況」カード・カレンダーの表示対象日を絞り込む。
 * 日曜は工場が稼働しないため常に対象外、土曜は例外稼働日（休日マスタのoverrides）で
 * 実際に稼働する日だけを対象にする。平日は休日マスタの祝日等であっても表示対象のままにする
 * （このカードの目的は「誰が休むか」であり、会社休日そのものの表示ではないため）。
 */
fun filterStaffLeaveDates(dates: List<LocalDate>, holidays: Set<String>, overrides: Set<String>): List<LocalDate> =
    dates.filter { date ->
        when (date.dayOfWeek) {
            java.time.DayOfWeek.SUNDAY -> false
            java.time.DayOfWeek.SATURDAY -> DateUtil.isWorkingDay(date, holidays, overrides)
            else -> true
        }
    }

/** ダッシュボードカードに表示する、本日から30日間のうちの表示対象日 */
fun staffLeaveTargetDates(today: LocalDate, holidays: Set<String>, overrides: Set<String>): List<LocalDate> =
    filterStaffLeaveDates((0..29L).map { today.plusDays(it) }, holidays, overrides)

/**
 * 休暇1件分の表示ラベル。Web版の出荷カレンダー「休暇予定」パネルと同じ書式
 * （例：「佐藤（有給休暇・半日）」）にする。
 */
fun leaveLabel(entry: LeaveEntryDto): String {
    val category = entry.category_name
    val halfDay = if (entry.am_pm != null) "・半日" else ""
    return if (category != null) "${entry.user_name}（$category$halfDay）" else entry.user_name
}

class DashboardViewModel(
    private val workerRepo: WorkerRepository,
    private val managerRepo: ManagerRepository,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var taskSummary by mutableStateOf(TaskSummary())
        private set
    var materialWaitingSummary by mutableStateOf<List<MaterialWaitingDaySummary>?>(null)
        private set
    var shippingSummary by mutableStateOf<List<ShippingDaySummary>?>(null)
        private set
    var deadlineSummary by mutableStateOf<List<TaskDeadlineDaySummary>>(emptyList())
        private set
    var staffLeaveDays by mutableStateOf<List<StaffLeaveDaySummary>>(emptyList())
        private set
    var announcements by mutableStateOf<List<AnnouncementDto>>(emptyList())
        private set

    // 日付を選択したときにカード内へそのまま一覧表示できるよう、集計元の受注データも保持しておく
    private var materialWaitingOrders: List<OrderAssignDto> = emptyList()
    private var shippingDays: List<jp.co.nse.worker.data.ShippingCalendarDayDto> = emptyList()
    private var activeTasks: List<jp.co.nse.worker.data.TaskItemDto> = emptyList()
    private var completedTasks: List<jp.co.nse.worker.data.CompletedTaskDto> = emptyList()

    fun taskRowsFor(category: TaskStatCategory): List<OrderTableRow> = when (category) {
        TaskStatCategory.WAITING -> activeTasks.filter { it.status == WorkStatus.WAITING }.map { it.order.toTableRow() }
        TaskStatCategory.IN_PROGRESS -> activeTasks.filter { it.status != WorkStatus.WAITING }.map { it.order.toTableRow() }
        TaskStatCategory.COMPLETED -> completedTasks.map { it.order.toTableRow() }
    }

    fun materialRowsFor(date: LocalDate): List<OrderTableRow> =
        materialWaitingOrders
            .filter { DateUtil.parse(it.material_arrived_at) == date }
            .map { it.toTableRow() }

    fun shippingRowsFor(date: LocalDate): List<OrderTableRow> =
        shippingDays.firstOrNull { DateUtil.parse(it.date) == date }
            ?.orders
            ?.filter { it.status != "shipped" && it.status != "billed" }
            ?.map { it.toTableRow() }
            ?: emptyList()

    fun load(canViewShipping: Boolean) {
        viewModelScope.launch {
            loading = true
            loadTaskSummary()
            // 材料入荷状況は特定の権限を持つ人だけの情報ではなく、工場の全員が
            // 材料の到着見込みを把握できた方がよいため、権限に関わらず全員に表示する
            loadMaterialWaitingSummary()
            if (canViewShipping) loadShippingSummary()
            loadStaffLeaveDays()
            loadAnnouncements()
            loading = false
        }
    }

    private suspend fun loadAnnouncements() {
        when (val result = workerRepo.announcements()) {
            is ApiResult.Success -> announcements = result.data
            is ApiResult.Failure -> {}
        }
    }

    private suspend fun loadTaskSummary() {
        when (val result = workerRepo.tasks()) {
            is ApiResult.Success -> {
                activeTasks = result.data.active
                completedTasks = result.data.completed_today
                taskSummary = TaskSummary(
                    waiting = result.data.active.count { it.status == WorkStatus.WAITING },
                    inProgress = result.data.active.count { it.status != WorkStatus.WAITING },
                    completed = result.data.completed_today.size,
                )
                // 完了済みは納期を気にする必要がないため、未完了（active）の担当工程だけを対象にする
                val today = LocalDate.now()
                deadlineSummary = (0..2).map { offset ->
                    val date = today.plusDays(offset.toLong())
                    val count = result.data.active.count { DateUtil.parse(it.process_deadline) == date }
                    TaskDeadlineDaySummary(date, count)
                }
            }
            is ApiResult.Failure -> {}
        }
    }

    /**
     * 材料待ち（status="material_waiting"）と材料到着日（status="material_arrived_date"）の
     * 受注を、材料到着予定日（material_arrived_at）が直近10稼働日（本日＋先の9稼働日）の
     * どれに当たるかで件数集計する。生産管理システム側はmaterial_arrived_atが今日以前になった
     * 時点で自動的にmaterial_waiting→material_arrived_dateへステータスを切り替えるため、
     * material_waitingだけを見ると「本日到着予定」の分がこの切り替えで抜け落ちてしまう。
     * 休日等で「明日」「明後日」が実際の稼働日とずれて誤解を招くため、日付そのものを
     * ラベルに使う（出荷予定カードと同じ考え方）。横スクロールで全日分を表示する。
     */
    private suspend fun loadMaterialWaitingSummary() {
        val orders = when (val result = managerRepo.orders()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return
        }
        val today = LocalDate.now()
        val holidays = mutableSetOf<String>()
        val overrides = mutableSetOf<String>()
        setOf(DateUtil.fiscalYearOf(today), DateUtil.fiscalYearOf(today.plusDays(21))).forEach { fy ->
            when (val result = managerRepo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidays += result.data.holidays
                    overrides += result.data.overrides
                }
                is ApiResult.Failure -> {}
            }
        }
        val targetDates = mutableListOf<LocalDate>()
        var cursor = today
        while (targetDates.size < 10) {
            if (DateUtil.isWorkingDay(cursor, holidays, overrides)) targetDates += cursor
            cursor = cursor.plusDays(1)
        }
        val waiting = orders.filter { it.status == "material_waiting" || it.status == "material_arrived_date" }
        materialWaitingOrders = waiting
        materialWaitingSummary = targetDates.map { date ->
            val count = waiting.count { DateUtil.parse(it.material_arrived_at) == date }
            MaterialWaitingDaySummary(date, count)
        }
    }

    /**
     * 直近10稼働日分の出荷予定件数。休日マスタ（休日・例外稼働日）を参照して、
     * 土日・休日を除いた実際の稼働日だけを対象にする。「明日」「明後日」という表記は
     * 休日等で実際の稼働日とずれて誤解を招くため、日付そのものをラベルに使う。
     * 横スクロールで全日分を表示する。
     */
    private suspend fun loadShippingSummary() {
        val today = LocalDate.now()
        val holidays = mutableSetOf<String>()
        val overrides = mutableSetOf<String>()
        setOf(DateUtil.fiscalYearOf(today), DateUtil.fiscalYearOf(today.plusDays(21))).forEach { fy ->
            when (val result = managerRepo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidays += result.data.holidays
                    overrides += result.data.overrides
                }
                is ApiResult.Failure -> {}
            }
        }

        val targetDates = mutableListOf<LocalDate>()
        var cursor = today
        while (targetDates.size < 10) {
            if (DateUtil.isWorkingDay(cursor, holidays, overrides)) targetDates += cursor
            cursor = cursor.plusDays(1)
        }

        val months = targetDates.map { java.time.YearMonth.from(it) }.distinct()
        val days = mutableListOf<jp.co.nse.worker.data.ShippingCalendarDayDto>()
        months.forEach { ym ->
            when (val result = managerRepo.shippingCalendar(ym.year, ym.monthValue)) {
                is ApiResult.Success -> result.data?.weeks?.flatten()?.let { days += it }
                is ApiResult.Failure -> {}
            }
        }
        shippingDays = days
        shippingSummary = targetDates.map { date ->
            val day = days.firstOrNull { DateUtil.parse(it.date) == date }
            val count = day?.orders?.count { it.status != "shipped" && it.status != "billed" } ?: 0
            ShippingDaySummary(date, count)
        }
    }

    /**
     * 「お休み状況」カードに表示する日を、休日マスタ（休日・例外稼働日）を見て決める。
     * 表示対象日（直近30日のうち土日祝を除いた日）の範囲でGET /leavesを呼び、取得者を集計する。
     */
    private suspend fun loadStaffLeaveDays() {
        val today = LocalDate.now()
        val holidays = mutableSetOf<String>()
        val overrides = mutableSetOf<String>()
        setOf(DateUtil.fiscalYearOf(today), DateUtil.fiscalYearOf(today.plusDays(29))).forEach { fy ->
            when (val result = managerRepo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidays += result.data.holidays
                    overrides += result.data.overrides
                }
                is ApiResult.Failure -> {}
            }
        }
        val targetDates = staffLeaveTargetDates(today, holidays, overrides)
        val rangeStart = targetDates.minOrNull() ?: today
        val rangeEnd = targetDates.maxOrNull() ?: today
        when (val result = managerRepo.leaves(rangeStart.toString(), rangeEnd.toString(), StaffLeaveDepartment)) {
            is ApiResult.Success -> {
                val leavesByDate = result.data.days.mapNotNull { day ->
                    DateUtil.parse(day.date)?.let { it to day.leaves }
                }.toMap()
                staffLeaveDays = targetDates.map { date ->
                    StaffLeaveDaySummary(date, leavesByDate[date]?.map(::leaveLabel).orEmpty())
                }
            }
            is ApiResult.Failure -> staffLeaveDays = targetDates.map { StaffLeaveDaySummary(it, emptyList()) }
        }
    }
}

/**
 * ログイン直後、またはヘッダーの「ダッシュボード」アイコンから遷移する画面。
 * 以前はアニメーションだけのウェルカム演出（WelcomeScreen）だったが廃止し、
 * 今日の花・本日の担当作業サマリーなど実用的な情報を表示する常設ページに置き換えた。
 * メイン色の変更・作業実績はマイページ画面（MyPageScreen）に分離してある。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onContinue: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenCheckSheet: (orderId: Int) -> Unit = {},
    onOpenStaffLeaveCalendar: (LocalDate) -> Unit = {},
    onOpenAnnouncementHistory: () -> Unit = {},
    onOpenAnnouncementDetail: (announcementId: Int) -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val flower = remember { FlowerOfDay.today(context) }
    val today = remember { LocalDate.now() }

    val canViewShipping by container.settings.canViewShippingFlow.collectAsState(initial = false)
    val seenAnnouncementIds by container.settings.seenAnnouncementIdsFlow.collectAsState(initial = emptySet())

    val vm: DashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DashboardViewModel(container.workerRepository, container.managerRepository) }
        },
    )
    // canViewShippingは起動直後、DataStoreからの実際の値が届く前は
    // 一瞬だけ初期値(false)を返す。これをそのままload()の引数にすると、
    // 「一部カードが揃っていないダッシュボードが一瞬表示された直後にもう一度読み込み直す」
    // という表示のちらつきが起きるため、初回は実際の値が確定してから一度だけ読み込む
    LaunchedEffect(Unit) {
        val initialCanViewShipping = container.settings.canViewShippingFlow.first()
        vm.load(initialCanViewShipping)
    }
    // ログイン直後などまだポーリングが始まっていない場合でも、ダッシュボードでは
    // 未読通知を即座に表示できるようにする（開始済みなら何もしない）
    LaunchedEffect(Unit) {
        container.notificationCenter.startPolling()
        container.notificationCenter.refresh()
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("ダッシュボード", showDate = false) },
                navigationIcon = { HeaderLogo() },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    HeaderOverflowMenu(showDashboard = false)
                    IconButton(onClick = { feedback(); vm.load(canViewShipping) }) {
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
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (vm.loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                DashboardContent(
                    listState = listState,
                    userName = userName,
                    today = today,
                    flower = flower,
                    taskSummary = vm.taskSummary,
                    deadlineSummary = vm.deadlineSummary,
                    materialWaitingSummary = vm.materialWaitingSummary,
                    shippingSummary = if (canViewShipping) vm.shippingSummary else null,
                    staffLeaveDays = vm.staffLeaveDays,
                    announcements = vm.announcements,
                    seenAnnouncementIds = seenAnnouncementIds,
                    taskRowsFor = vm::taskRowsFor,
                    materialRowsFor = vm::materialRowsFor,
                    shippingRowsFor = vm::shippingRowsFor,
                    onContinue = onContinue,
                    onOpenCheckSheet = onOpenCheckSheet,
                    onOpenStaffLeaveCalendar = onOpenStaffLeaveCalendar,
                    onOpenAnnouncementHistory = onOpenAnnouncementHistory,
                    onOpenAnnouncementDetail = onOpenAnnouncementDetail,
                )
                ScrollToTopFab(
                    visible = listState.firstVisibleItemIndex > 0,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                )
                ScrollToBottomFab(
                    visible = listState.canScrollForward,
                    onClick = {
                        scope.launch {
                            val lastIndex = listState.layoutInfo.totalItemsCount - 1
                            if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    listState: LazyListState,
    userName: String,
    today: LocalDate,
    flower: FlowerOfDayEntry?,
    taskSummary: TaskSummary,
    deadlineSummary: List<TaskDeadlineDaySummary>,
    materialWaitingSummary: List<MaterialWaitingDaySummary>?,
    shippingSummary: List<ShippingDaySummary>?,
    staffLeaveDays: List<StaffLeaveDaySummary>,
    announcements: List<AnnouncementDto>,
    seenAnnouncementIds: Set<String>,
    taskRowsFor: (TaskStatCategory) -> List<OrderTableRow>,
    materialRowsFor: (LocalDate) -> List<OrderTableRow>,
    shippingRowsFor: (LocalDate) -> List<OrderTableRow>,
    onContinue: () -> Unit,
    onOpenCheckSheet: (orderId: Int) -> Unit = {},
    onOpenStaffLeaveCalendar: (LocalDate) -> Unit = {},
    onOpenAnnouncementHistory: () -> Unit = {},
    onOpenAnnouncementDetail: (announcementId: Int) -> Unit = {},
) {
    val feedback = rememberClickFeedback()
    var materialExpanded by remember { mutableStateOf(false) }
    var shippingExpanded by remember { mutableStateOf(false) }
    var selectedTaskCategory by remember { mutableStateOf<TaskStatCategory?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "greeting") {
            Column(modifier = Modifier.fillMaxWidth()) {
                val dateSentence = "${today.monthValue}月${today.dayOfMonth}日${DateUtil.weekdayKanji(today)}曜日。"
                Text(
                    if (flower != null) {
                        "こんにちは、${userName}さん！$dateSentence\n今日の花は${flower.name}、花言葉は${flower.meaning}です。"
                    } else {
                        "こんにちは、${userName}さん！$dateSentence"
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 1度詳細を開いたお知らせは、次からダッシュボードでは目立たせない（端末内のみの既読管理）。
        // 未読が残っていればそれぞれ個別のカードで表示し、タップで直接詳細を開く。
        // 未読が無く、表示中のお知らせ自体はある場合は、控えめな一覧行の形でだけ残しておく。
        // 表示中のお知らせが1件も無ければ、これまで通り履歴を確認できるプレースホルダーを出す。
        val unseenAnnouncements = announcements.filter { it.id.toString() !in seenAnnouncementIds }
        if (unseenAnnouncements.isNotEmpty()) {
            items(unseenAnnouncements, key = { "announcement-${it.id}" }) { announcement ->
                AnnouncementCard(
                    announcement = announcement,
                    onClick = { feedback(); onOpenAnnouncementDetail(announcement.id) },
                )
            }
        } else if (announcements.isNotEmpty()) {
            item(key = "announcements-seen") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    announcements.forEach { announcement ->
                        SeenAnnouncementRow(
                            announcement = announcement,
                            onClick = { feedback(); onOpenAnnouncementDetail(announcement.id) },
                        )
                    }
                }
            }
        } else {
            item(key = "announcements-empty") {
                EmptyAnnouncementCard(onClick = { feedback(); onOpenAnnouncementHistory() })
            }
        }

        item(key = "notifications") {
            NotificationPreviewCard()
        }

        item(key = "task-summary") {
            DashboardCard("本日の担当作業") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock(
                        "未着手",
                        taskSummary.waiting,
                        Color(0xFF9CA3AF),
                        selected = selectedTaskCategory == TaskStatCategory.WAITING,
                        onClick = {
                            feedback()
                            selectedTaskCategory = if (selectedTaskCategory == TaskStatCategory.WAITING) null else TaskStatCategory.WAITING
                        },
                    )
                    StatBlock(
                        "進行中",
                        taskSummary.inProgress,
                        MaterialTheme.colorScheme.primary,
                        selected = selectedTaskCategory == TaskStatCategory.IN_PROGRESS,
                        onClick = {
                            feedback()
                            selectedTaskCategory = if (selectedTaskCategory == TaskStatCategory.IN_PROGRESS) null else TaskStatCategory.IN_PROGRESS
                        },
                    )
                    StatBlock(
                        "完了",
                        taskSummary.completed,
                        Color(0xFF16A34A),
                        selected = selectedTaskCategory == TaskStatCategory.COMPLETED,
                        onClick = {
                            feedback()
                            selectedTaskCategory = if (selectedTaskCategory == TaskStatCategory.COMPLETED) null else TaskStatCategory.COMPLETED
                        },
                    )
                }
                selectedTaskCategory?.let { category ->
                    Spacer(Modifier.height(8.dp))
                    OrderInlineTable(
                        taskRowsFor(category),
                        onRowClick = { orderId -> feedback(); onOpenCheckSheet(orderId) },
                    )
                }
                if (deadlineSummary.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFF3F4F6))
                    Spacer(Modifier.height(12.dp))
                    Text("工程納期", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                    Spacer(Modifier.height(8.dp))
                    DeadlineCountRow(deadlineSummary)
                }
            }
        }

        item(key = "staff-leave-summary") {
            DashboardCard("お休み状況", onTitleClick = { onOpenStaffLeaveCalendar(today) }) {
                StaffLeaveCalendarRow(
                    staffLeaveDays,
                    onSelectDate = { date -> feedback(); onOpenStaffLeaveCalendar(date) },
                )
            }
        }

        materialWaitingSummary?.let { days ->
            item(key = "material-waiting-summary") {
                DashboardCard("材料入荷状況") {
                    DaySummaryToggle(
                        days = days.map { it.date to it.count },
                        highlightNonZero = true,
                        expanded = materialExpanded,
                        onToggle = { feedback(); materialExpanded = !materialExpanded },
                    )
                    if (materialExpanded) {
                        MultiDayOrderTables(
                            dates = days.map { it.date },
                            rowsFor = materialRowsFor,
                            showMaterialColumn = true,
                            onRowClick = { orderId -> feedback(); onOpenCheckSheet(orderId) },
                        )
                    }
                }
            }
        }

        shippingSummary?.let { days ->
            item(key = "shipping-summary") {
                DashboardCard("出荷予定") {
                    DaySummaryToggle(
                        days = days.map { it.date to it.count },
                        highlightNonZero = false,
                        expanded = shippingExpanded,
                        onToggle = { feedback(); shippingExpanded = !shippingExpanded },
                    )
                    if (shippingExpanded) {
                        MultiDayOrderTables(
                            dates = days.map { it.date },
                            rowsFor = shippingRowsFor,
                            onRowClick = { orderId -> feedback(); onOpenCheckSheet(orderId) },
                        )
                    }
                }
            }
        }

        item(key = "continue") {
            Button(
                onClick = { feedback(); onContinue() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text("作業を始める", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/**
 * 管理者・工場長がWeb管理画面（お知らせ管理）で作成したお知らせをカードで表示する。
 * 緊急指定（is_urgent）のものは赤系で強調し、通常のものと見分けやすくする。
 * ダッシュボードには最新（緊急優先）の1件だけを表示し、他にも表示中のお知らせがあれば
 * [moreCount]で「ほかN件」と添える。タップすると詳細画面（本文・添付ファイル）を開く。
 * 作成・編集・既読管理はタブレット側では行わない（Web管理画面のみ）。
 */
@Composable
private fun AnnouncementCard(announcement: AnnouncementDto, onClick: () -> Unit) {
    val urgent = announcement.is_urgent
    val fromLabel = announcement.creator_role_label?.let { "${it}からのお知らせ" } ?: "お知らせ"
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (urgent) Color(0xFFFEF2F2) else Color.White),
        shape = RoundedCornerShape(16.dp),
        border = if (urgent) BorderStroke(1.dp, Red500) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                tint = if (urgent) Red500 else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fromLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (urgent) Red500 else Color(0xFF9CA3AF),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    announcement.message,
                    fontSize = 14.sp,
                    fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                    color = if (urgent) Color(0xFFB91C1C) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 表示中のお知らせが全て既読（詳細を開いたことがある）の場合に使う、控えめな一覧行。
 * [AnnouncementCard]ほど目立たせる必要は無いが、内容自体は引き続き確認できるようにしておく。
 */
@Composable
private fun SeenAnnouncementRow(announcement: AnnouncementDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                announcement.message,
                fontSize = 13.sp,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 現在表示中のお知らせが1件も無い時に代わりに出す、控えめなプレースホルダー。
 * これが無いと「お知らせを見に行く入り口」自体がダッシュボードから消えてしまうため、
 * 常にこの枠だけは表示しておき、タップすれば過去のお知らせ履歴を確認できるようにする。
 */
@Composable
private fun EmptyAnnouncementCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "現在お知らせはありません",
                fontSize = 13.sp,
                color = Color(0xFF9CA3AF),
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "お知らせ履歴を見る",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * [onTitleClick]を渡すと、タイトル行がタップ可能になり右端に案内の矢印が付く
 * （例：「お休み状況」タップでカレンダー画面を開く）。
 */
@Composable
private fun DashboardCard(title: String, onTitleClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val feedback = rememberClickFeedback()
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (onTitleClick != null) it.clickable { feedback(); onTitleClick() } else it },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
                if (onTitleClick != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "カレンダーで見る",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * 「お休み状況」カードの中身。直近2週間のうち日曜（工場休業日）を除いた日付を、
 * 横スクロールできるセルで並べる（土曜は例外稼働日として実際に稼働する日だけが対象）。
 * セルの大きさは、以前の週表示（fillMaxWidthを7等分）のときと同じ見た目になる幅・余白にしている。
 * セル内の名前は縦に並べると人数によってセルの高さがバラつくため、1行に横並びで収め、
 * 入りきらない分は末尾を省略して常に同じ高さになるようにしている。
 * 有給休暇取得者がいる日は、一目で分かるようメインカラーで塗りつぶす。
 * 30日分あると月をまたぐため、行を増やさずセルの大きさを崩さないよう、先頭セルと月初のセルだけ
 * 日付を「M/D」表記にして月が変わったことが分かるようにしている（それ以外は「D」のみ）。
 * セルをタップすると、その日を選択した状態でカレンダー画面（[StaffLeaveCalendarScreen]）へ遷移する。
 */
@Composable
private fun StaffLeaveCalendarRow(days: List<StaffLeaveDaySummary>, onSelectDate: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEachIndexed { index, day ->
            val dow = day.date.dayOfWeek
            val onLeave = day.staffNames.isNotEmpty()
            val dateLabel = if (index == 0 || day.date.dayOfMonth == 1) {
                "${day.date.monthValue}/${day.date.dayOfMonth}"
            } else {
                "${day.date.dayOfMonth}"
            }
            val weekdayColor = when {
                onLeave -> MaterialTheme.colorScheme.onPrimary
                dow == java.time.DayOfWeek.SATURDAY -> Color(0xFF2563EB)
                else -> Color(0xFF6B7280)
            }
            val dateColor = if (onLeave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val namesColor = if (onLeave) MaterialTheme.colorScheme.onPrimary else Color(0xFF6B7280)
            Column(
                modifier = Modifier
                    .width(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (onLeave) MaterialTheme.colorScheme.primary else Color(0xFFF9FAFB))
                    .clickable { onSelectDate(day.date) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(WeekdayLabels[dow.value % 7], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = weekdayColor)
                Spacer(Modifier.height(2.dp))
                Text(dateLabel, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = dateColor, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (day.staffNames.isEmpty()) "-" else day.staffNames.joinToString("・"),
                    fontSize = 11.sp,
                    color = namesColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 「本日の担当作業」カードの、本日・明日・明後日それぞれの工程納期件数（タップ不要の単純表示）。 */
@Composable
private fun DeadlineCountRow(days: List<TaskDeadlineDaySummary>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            val color = if (day.count > 0) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(DateUtil.shortLabel(day.date), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${day.count}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color)
                    Text(" 件", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = color)
                }
            }
        }
    }
}

/**
 * 「材料入荷状況」「出荷予定」カードで使う、直近10稼働日分の件数表示。
 * セルの大きさ・見た目は「お休み状況」（[StaffLeaveCalendarRow]）と揃えている
 * （幅88dp、曜日・日付・件数を縦に並べる、該当日は背景をメインカラーで塗る）。
 * 日付ごとに個別選択させるのではなく、カード全体を1つのタブとしてタップすると、
 * 表示中の日数分すべての受注一覧（MultiDayOrderTables）がまとめて開閉するようにしている。
 */
@Composable
private fun DaySummaryToggle(
    days: List<Pair<LocalDate, Int>>,
    highlightNonZero: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEachIndexed { index, (date, count) ->
                val dow = date.dayOfWeek
                val highlighted = highlightNonZero && count > 0
                val dateLabel = if (index == 0 || date.dayOfMonth == 1) {
                    "${date.monthValue}/${date.dayOfMonth}"
                } else {
                    "${date.dayOfMonth}"
                }
                val weekdayColor = when {
                    highlighted -> MaterialTheme.colorScheme.onPrimary
                    dow == java.time.DayOfWeek.SATURDAY -> Color(0xFF2563EB)
                    dow == java.time.DayOfWeek.SUNDAY -> Color(0xFFDC2626)
                    else -> Color(0xFF6B7280)
                }
                val dateColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                val countColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else Color(0xFF6B7280)
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (highlighted) MaterialTheme.colorScheme.primary else Color(0xFFF9FAFB))
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(WeekdayLabels[dow.value % 7], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = weekdayColor)
                    Spacer(Modifier.height(2.dp))
                    Text(dateLabel, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = dateColor, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Text("${count}件", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = countColor, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "閉じる" else "${days.size}日分を表示",
            tint = Color(0xFF9CA3AF),
        )
    }
}

/**
 * 選択したタブ（材料入荷状況／出荷予定）の直近3稼働日分を、日付ごとに見出しを付けて
 * まとめて表示する。ダッシュボード全体がLazyColumnのため、各日のテーブルはColumnで組む。
 */
@Composable
private fun MultiDayOrderTables(
    dates: List<LocalDate>,
    rowsFor: (LocalDate) -> List<OrderTableRow>,
    onRowClick: (orderId: Int) -> Unit,
    showMaterialColumn: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        dates.forEachIndexed { index, date ->
            if (index > 0) HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(vertical = 4.dp))
            Text(
                DateUtil.shortLabel(date),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            OrderInlineTable(rowsFor(date), onRowClick = onRowClick, showMaterialColumn = showMaterialColumn)
        }
    }
}

@Composable
private fun StatBlock(label: String, count: Int, color: Color, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("$count", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
        Text(label, fontSize = 12.sp, color = Color(0xFF6B7280))
    }
}
