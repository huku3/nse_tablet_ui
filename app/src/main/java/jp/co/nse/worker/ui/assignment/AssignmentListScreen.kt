package jp.co.nse.worker.ui.assignment

import androidx.compose.foundation.background
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
import jp.co.nse.worker.data.AssignProcessDto
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Green700
import jp.co.nse.worker.ui.theme.Orange400
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/** ダッシュボードの絞り込み種別。Web版 assignment_board.blade.php の setDashboardFilter と同じ4種 */
enum class DashboardFilter { ALL, URGENT, TODAY, UNASSIGNED }

class AssignmentListViewModel(private val repo: ManagerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var orders by mutableStateOf<List<OrderAssignDto>>(emptyList())
        private set
    var holidayDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var overrideDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var filter by mutableStateOf(DashboardFilter.ALL)

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.orders()) {
                is ApiResult.Success -> orders = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
            loadHolidays()
        }
    }

    private suspend fun loadHolidays() {
        val today = LocalDate.now()
        val fiscalYears = setOf(
            DateUtil.fiscalYearOf(today.minusDays(14)),
            DateUtil.fiscalYearOf(today),
            DateUtil.fiscalYearOf(today.plusDays(14)),
        )
        val holidays = mutableSetOf<String>()
        val overrides = mutableSetOf<String>()
        fiscalYears.forEach { fy ->
            when (val result = repo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidays += result.data.holidays
                    overrides += result.data.overrides
                }
                is ApiResult.Failure -> { /* 取得失敗時は土日のみで営業日判定する */ }
            }
        }
        holidayDates = holidays
        overrideDates = overrides
    }

    /** 受注の客先納期までの営業日数（本日=0、未来はプラス、超過はマイナス） */
    private fun businessDaysFor(order: OrderAssignDto): Int? {
        val delivery = DateUtil.parse(order.delivery_date) ?: return null
        return DateUtil.businessDaysBetween(LocalDate.now(), delivery, holidayDates, overrideDates)
    }

    private fun hasUnassigned(order: OrderAssignDto): Boolean =
        order.processes.any { it.status != WorkStatus.COMPLETED && it.worker.isNullOrBlank() }

    val todayCount: Int get() = orders.count { businessDaysFor(it) == 0 }
    val urgentCount: Int get() = orders.count { (businessDaysFor(it) ?: Int.MAX_VALUE) <= 3 }
    val unassignedCount: Int get() = orders.count { hasUnassigned(it) }

    val filteredOrders: List<OrderAssignDto> get() = when (filter) {
        DashboardFilter.ALL -> orders
        DashboardFilter.URGENT -> orders.filter { (businessDaysFor(it) ?: Int.MAX_VALUE) <= 3 }
        DashboardFilter.TODAY -> orders.filter { businessDaysFor(it) == 0 }
        DashboardFilter.UNASSIGNED -> orders.filter { hasUnassigned(it) }
    }

    /** 同じカードをもう一度押すと絞り込み解除（Web版と同じ挙動） */
    fun toggleFilter(target: DashboardFilter) {
        filter = if (target == DashboardFilter.ALL || filter == target) DashboardFilter.ALL else target
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentListScreen(
    onOpenOrder: (orderId: Int) -> Unit,
    onLogout: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: AssignmentListViewModel = viewModel(
        factory = viewModelFactory { initializer { AssignmentListViewModel(container.managerRepository) } }
    )
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("割り当て") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    MyPageButton()
                    ProcessAssignmentButton()
                    IconButton(onClick = { feedback(); vm.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
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
                vm.loading && vm.orders.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null && vm.orders.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                    }
                }
                vm.orders.isEmpty() -> {
                    Text(
                        "対象の受注がありません",
                        modifier = Modifier.align(Alignment.Center),
                        fontWeight = FontWeight.Bold,
                    )
                }
                else -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    val filteredOrders = vm.filteredOrders
                    Column(Modifier.fillMaxSize()) {
                        // スクロールしても絞り込み中のダッシュボードが隠れないよう、リストの外（TopAppBar直下）に固定表示する
                        DashboardRow(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            totalCount = vm.orders.size,
                            urgentCount = vm.urgentCount,
                            todayCount = vm.todayCount,
                            unassignedCount = vm.unassignedCount,
                            filter = vm.filter,
                            onSelect = { vm.toggleFilter(it) },
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (filteredOrders.isEmpty()) {
                                    item(key = "filtered-empty") {
                                        Text(
                                            "条件に一致する受注がありません",
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9CA3AF),
                                        )
                                    }
                                } else {
                                    items(filteredOrders, key = { it.id }) { order ->
                                        OrderAssignCard(order = order, onClick = { onOpenOrder(order.id) })
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
                }
            }
        }
    }
}

/**
 * ダッシュボード風の件数表示。Web版 assignment_board.blade.php のダッシュボードカードと同じ4種で、
 * タップすると絞り込み（もう一度押すと解除）。
 */
@Composable
private fun DashboardRow(
    modifier: Modifier = Modifier,
    totalCount: Int,
    urgentCount: Int,
    todayCount: Int,
    unassignedCount: Int,
    filter: DashboardFilter,
    onSelect: (DashboardFilter) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "受注件数",
            count = totalCount,
            valueColor = Color(0xFF1F2937),
            selected = filter == DashboardFilter.ALL,
            enabled = true,
            alwaysEnabled = true,
            onClick = { onSelect(DashboardFilter.ALL) },
        )
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "3営業日以内納期",
            count = urgentCount,
            valueColor = if (urgentCount > 0) Red500 else Color(0xFF9CA3AF),
            selected = filter == DashboardFilter.URGENT,
            enabled = urgentCount > 0,
            onClick = { onSelect(DashboardFilter.URGENT) },
        )
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "本日納期",
            count = todayCount,
            valueColor = if (todayCount > 0) Orange400 else Color(0xFF9CA3AF),
            selected = filter == DashboardFilter.TODAY,
            enabled = todayCount > 0,
            onClick = { onSelect(DashboardFilter.TODAY) },
        )
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "未割り当てがある受注",
            count = unassignedCount,
            valueColor = if (unassignedCount > 0) Amber500 else Green600,
            selected = filter == DashboardFilter.UNASSIGNED,
            enabled = unassignedCount > 0,
            onClick = { onSelect(DashboardFilter.UNASSIGNED) },
        )
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    valueColor: Color,
    selected: Boolean,
    enabled: Boolean,
    alwaysEnabled: Boolean = false,
    onClick: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    Card(
        onClick = { if (enabled || alwaysEnabled) { feedback(); onClick() } },
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE5E7EB),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF9CA3AF), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
                Text("件", fontSize = 12.sp, color = valueColor, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun OrderAssignCard(order: OrderAssignDto, onClick: () -> Unit) {
    val total = order.processes.size
    val assigned = order.processes.count { !it.worker.isNullOrBlank() }
    val allAssigned = total > 0 && assigned == total
    val feedback = rememberClickFeedback()

    Card(
        onClick = { feedback(); onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    order.part_name ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                val date = DateUtil.parse(order.delivery_date)
                if (date != null) {
                    Text(
                        DateUtil.shortLabel(date),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B7280),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("受注No.${order.id}", fontSize = 13.sp, color = Color(0xFF6B7280))
                order.po_number?.let {
                    Text("発注 $it", fontSize = 13.sp, color = Color(0xFF6B7280))
                }
                order.quantity?.let {
                    Text("数量 $it", fontSize = 13.sp, color = Color(0xFF6B7280))
                }
            }

            // 工程名 + 担当者（左から右へ工程順に並べ、工程名の下に担当者を表示）
            val procs = order.processes.sortedBy { it.sort_order }
            if (procs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF9FAFB))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    procs.forEach { proc -> ProcessWorkerColumn(proc) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (allAssigned) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    if (allAssigned) "担当割当済み（$assigned/$total 工程）" else "担当 $assigned/$total 工程",
                    color = if (allAssigned) Color(0xFF15803D) else Color(0xFF92400E),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun ProcessWorkerColumn(process: AssignProcessDto) {
    val isCompleted = process.status == WorkStatus.COMPLETED
    val hasWorker = !process.worker.isNullOrBlank()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            process.process_name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (isCompleted) Color(0xFF9CA3AF) else Color(0xFF374151),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (hasWorker) process.worker!! else "未割当",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                isCompleted -> Color(0xFF9CA3AF)
                hasWorker -> Green700
                else -> Color(0xFFD1D5DB)
            },
            maxLines = 1,
            softWrap = false,
        )
        DateUtil.monthDayLabel(process.process_deadline)?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                it,
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
