package jp.co.nse.worker.ui.assignment

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ScrollToBottomFab
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Green700
import jp.co.nse.worker.ui.theme.Orange400
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.AutoRefreshEffect
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/** ダッシュボードの絞り込み種別。Web版 assignment_board.blade.php の setDashboardFilter と同じ4種 */
enum class DashboardFilter { ALL, URGENT, TODAY, UNASSIGNED }

/**
 * 画面の用途。ASSIGNMENT＝担当者の割り当て（件数ダッシュボードで絞り込み、従来通り）、
 * ORDER_LIST＝受注一覧（閲覧専用。受注ステータスのカードで絞り込む）
 */
enum class OrderListMode { ASSIGNMENT, ORDER_LIST }

class AssignmentListViewModel(
    private val repo: ManagerRepository,
    private val mode: OrderListMode = OrderListMode.ASSIGNMENT,
) : ViewModel() {
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
    var statusFilters by mutableStateOf<Set<String>>(emptySet())

    /**
     * ORDER_LISTモード（受注一覧）は、絞り込みチップに出す全ステータスを明示的にサーバーへ渡して
     * 取得する。サーバー側はstatus未指定時に出荷済み・請求済みを既定で除外するため、指定しないと
     * 「出荷済み」を選んでも常に0件になってしまう（絞り込み自体はこれまで通りクライアント側で行う）。
     */
    private fun statusQuery(): List<String>? =
        if (mode == OrderListMode.ORDER_LIST) jp.co.nse.worker.ui.components.OrderStatus.styles.keys.toList() else null

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            // ORDER_LISTは出荷済み等の古い受注も対象に含めるため、並び順（客先納期の早い順）で
            // それらに枠を取られて直近の受注が見切れないよう、取得件数を多めにしておく
            val perPage = if (mode == OrderListMode.ORDER_LIST) 500 else 100
            when (val result = repo.orders(statusQuery(), perPage)) {
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
    fun businessDaysFor(order: OrderAssignDto): Int? {
        val delivery = DateUtil.parse(order.delivery_date) ?: return null
        return DateUtil.businessDaysBetween(LocalDate.now(), delivery, holidayDates, overrideDates)
    }

    private fun hasUnassigned(order: OrderAssignDto): Boolean =
        order.processes.any { it.status != WorkStatus.COMPLETED && it.worker.isNullOrBlank() }

    val todayCount: Int get() = orders.count { businessDaysFor(it) == 0 }
    val urgentCount: Int get() = orders.count { (businessDaysFor(it) ?: Int.MAX_VALUE) <= 3 }
    val unassignedCount: Int get() = orders.count { hasUnassigned(it) }

    val filteredOrders: List<OrderAssignDto> get() = when (mode) {
        OrderListMode.ASSIGNMENT -> when (filter) {
            DashboardFilter.ALL -> orders
            DashboardFilter.URGENT -> orders.filter { (businessDaysFor(it) ?: Int.MAX_VALUE) <= 3 }
            DashboardFilter.TODAY -> orders.filter { businessDaysFor(it) == 0 }
            DashboardFilter.UNASSIGNED -> orders.filter { hasUnassigned(it) }
        }
        OrderListMode.ORDER_LIST -> {
            if (statusFilters.isEmpty()) orders else orders.filter { it.status in statusFilters }
        }
    }

    /** 同じカードをもう一度押すと絞り込み解除（Web版と同じ挙動） */
    fun toggleFilter(target: DashboardFilter) {
        filter = if (target == DashboardFilter.ALL || filter == target) DashboardFilter.ALL else target
    }

    /** ステータスカードでの複数選択絞り込み。「すべて」を押すと選択解除、ステータスは複数選択可（もう一度押すと解除） */
    fun toggleStatusFilter(status: String?) {
        statusFilters = when {
            status == null -> emptySet()
            status in statusFilters -> statusFilters - status
            else -> statusFilters + status
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentListScreen(
    onOpenOrder: (orderId: Int) -> Unit,
    onLogout: () -> Unit,
    title: String = "割り当て",
    mode: OrderListMode = OrderListMode.ASSIGNMENT,
    isActive: Boolean = true,
    onSwitchTab: (String) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val homeTabKey = if (mode == OrderListMode.ASSIGNMENT) "ASSIGN" else "ORDERS"
    val container = context.appContainer
    // 「割り当て」タブと「受注一覧」タブは同じAssignmentListScreenを異なるmodeで同時に使っており、
    // 両方ともHomeScreenの同じNavBackStackEntry（ViewModelStoreOwner）を共有している。
    // key未指定だとviewModel()はクラス単位でインスタンスを共有してしまい、片方のタブが
    // 先に生成したmode違いのViewModelをもう片方が使い回すことになる
    // （ステータス絞り込みやページ件数がタブ間で正しく効かない原因）ため、modeごとにキーを分ける
    val vm: AssignmentListViewModel = viewModel(
        key = "AssignmentListViewModel:$mode",
        factory = viewModelFactory { initializer { AssignmentListViewModel(container.managerRepository, mode) } }
    )
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    // 生産管理システム側の更新をタブレットにも反映するため、このタブが表示されている間
    // だけ30秒おきに裏側で再取得する（一覧が既にあるときはスピナーを出さず静かに更新）。
    // タブに切り替わった瞬間や、画面復帰時（受注詳細から戻った時など、このNavBackStackEntryの
    // ライフサイクルがRESUMEDへ戻った時）にも、repeatOnLifecycle(RESUMED)により即座に1回再取得する
    // （以前はこことは別にON_RESUMEで明示的にvm.load()する処理もあったが、
    // 完全に重複するリクエストになっていたため削除した。サーバー側のAPIレート制限に
    // 引っかかりやすくなっていた一因）
    AutoRefreshEffect(isActive = isActive, refreshImmediately = true) { vm.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle(title) },
                navigationIcon = { HeaderLogo(onClick = { onSwitchTab(homeTabKey) }) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell(isActive = isActive)
                    HeaderOverflowMenu(currentHomeTab = homeTabKey, onSwitchHomeTab = onSwitchTab)
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
                        if (mode == OrderListMode.ASSIGNMENT) {
                            DashboardRow(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                totalCount = vm.orders.size,
                                urgentCount = vm.urgentCount,
                                todayCount = vm.todayCount,
                                unassignedCount = vm.unassignedCount,
                                filter = vm.filter,
                                onSelect = { vm.toggleFilter(it) },
                            )
                        } else {
                            StatusFilterRow(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                orders = vm.orders,
                                selectedStatuses = vm.statusFilters,
                                onSelect = { vm.toggleStatusFilter(it) },
                            )
                        }
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
                                        OrderAssignCard(
                                            order = order,
                                            daysOverdue = vm.businessDaysFor(order),
                                            onClick = { onOpenOrder(order.id) },
                                        )
                                    }
                                }
                            }
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
            onClick = { onSelect(DashboardFilter.ALL) },
        )
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "3営業日以内納期",
            count = urgentCount,
            valueColor = if (urgentCount > 0) Red500 else Color(0xFF9CA3AF),
            selected = filter == DashboardFilter.URGENT,
            onClick = { onSelect(DashboardFilter.URGENT) },
        )
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "本日納期",
            count = todayCount,
            valueColor = if (todayCount > 0) Orange400 else Color(0xFF9CA3AF),
            selected = filter == DashboardFilter.TODAY,
            onClick = { onSelect(DashboardFilter.TODAY) },
        )
        DashboardCard(
            modifier = Modifier.weight(1f),
            label = "未割り当てがある受注",
            count = unassignedCount,
            valueColor = if (unassignedCount > 0) Amber500 else Green600,
            selected = filter == DashboardFilter.UNASSIGNED,
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
    onClick: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val labelColor = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else Color(0xFF9CA3AF)
    val countColor = if (selected) MaterialTheme.colorScheme.onPrimary else valueColor
    Card(
        onClick = { feedback(); onClick() },
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 0.dp else 1.dp,
            color = if (selected) Color.Transparent else Color(0xFFE5E7EB),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 11.sp, color = labelColor, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = countColor)
                Text("件", fontSize = 12.sp, color = countColor, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

/**
 * 受注一覧（閲覧専用）のステータス絞り込み。ステータスごとにカードで表示し、
 * タップすると絞り込み（複数選択可、もう一度押すと解除）。画面幅に収まらない分は
 * 横スクロールではなく折り返して次の行に表示する（縦向きでも一覧性を保つため）。
 * 「待機」「請求済み」は受注一覧では意味を持たないステータスのため表示しない。
 */
private val HiddenStatusFilterKeys = setOf("waiting", "billed")

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun StatusFilterRow(
    modifier: Modifier = Modifier,
    orders: List<OrderAssignDto>,
    selectedStatuses: Set<String>,
    onSelect: (String?) -> Unit,
) {
    val counts = orders.groupingBy { it.status ?: "" }.eachCount()
    val allStatuses = jp.co.nse.worker.ui.components.OrderStatus.styles.keys
        .filter { it !in HiddenStatusFilterKeys }
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusFilterCard(
            label = "すべて",
            count = orders.size,
            color = Color(0xFF1F2937),
            selected = selectedStatuses.isEmpty(),
            onClick = { onSelect(null) },
        )
        allStatuses.forEach { status ->
            val style = jp.co.nse.worker.ui.components.OrderStatus.style(status)
            StatusFilterCard(
                label = style.label,
                count = counts[status] ?: 0,
                color = style.text,
                selected = status in selectedStatuses,
                onClick = { onSelect(status) },
            )
        }
    }
}

// 文字を特大にしても「材料到着済み」等が見切れないよう、108dpから広げている
private val StatusFilterCardWidth = 128.dp

@Composable
private fun StatusFilterCard(
    label: String,
    count: Int,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val labelColor = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else Color(0xFF9CA3AF)
    val countColor = if (selected) MaterialTheme.colorScheme.onPrimary else color
    Card(
        onClick = { feedback(); onClick() },
        modifier = Modifier.width(StatusFilterCardWidth),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 0.dp else 1.dp,
            color = if (selected) Color.Transparent else Color(0xFFE5E7EB),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = labelColor,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                count.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = countColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OrderAssignCard(
    order: OrderAssignDto,
    daysOverdue: Int?,
    onClick: () -> Unit,
) {
    val total = order.processes.size
    val assigned = order.processes.count { !it.worker.isNullOrBlank() }
    val allAssigned = total > 0 && assigned == total
    val feedback = rememberClickFeedback()
    val isOverdue = daysOverdue != null && daysOverdue < 0
    val hasCustomerName = !order.customer_name.isNullOrBlank()

    Row(modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        if (isOverdue) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(Red500),
            )
        }
        Card(
            onClick = { feedback(); onClick() },
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = if (isOverdue) {
                RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 0.dp)
            } else {
                RoundedCornerShape(16.dp)
            },
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (hasCustomerName) {
                            Text(
                                order.customer_name ?: "",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280),
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            order.part_name ?: "—",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        FilledStatusBadge(order.status)
                        val date = DateUtil.parse(order.delivery_date)
                        if (date != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                DateUtil.shortLabel(date),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOverdue) Red500 else Color(0xFF6B7280),
                            )
                        }
                        if (isOverdue) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Red500)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "${-daysOverdue!!}日超過",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("No.${order.id}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
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
}

@Composable
private fun FilledStatusBadge(status: String?, modifier: Modifier = Modifier) {
    val style = jp.co.nse.worker.ui.components.OrderStatus.style(status)
    Text(
        style.label,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(style.text)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
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
