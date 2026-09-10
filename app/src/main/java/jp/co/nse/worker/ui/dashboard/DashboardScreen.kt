package jp.co.nse.worker.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.NotificationPreviewCard
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
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

/** ダッシュボードに表示する受注件数ショートカット（割り当て権限がある場合のみ取得） */
data class OrderSummary(val today: Int = 0, val urgent: Int = 0, val unassigned: Int = 0)

class DashboardViewModel(
    private val workerRepo: WorkerRepository,
    private val managerRepo: ManagerRepository,
) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var taskSummary by mutableStateOf(TaskSummary())
        private set
    var orderSummary by mutableStateOf<OrderSummary?>(null)
        private set
    var shippingTodayCount by mutableStateOf<Int?>(null)
        private set

    fun load(canAssign: Boolean, canViewShipping: Boolean) {
        viewModelScope.launch {
            loading = true
            loadTaskSummary()
            if (canAssign) loadOrderSummary()
            if (canViewShipping) loadShippingSummary()
            loading = false
        }
    }

    private suspend fun loadTaskSummary() {
        when (val result = workerRepo.tasks()) {
            is ApiResult.Success -> {
                taskSummary = TaskSummary(
                    waiting = result.data.active.count { it.status == WorkStatus.WAITING },
                    inProgress = result.data.active.count { it.status != WorkStatus.WAITING },
                    completed = result.data.completed_today.size,
                )
            }
            is ApiResult.Failure -> {}
        }
    }

    private suspend fun loadOrderSummary() {
        val orders = when (val result = managerRepo.orders()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return
        }
        val today = LocalDate.now()
        val holidays = mutableSetOf<String>()
        val overrides = mutableSetOf<String>()
        when (val result = managerRepo.holidayCalendar(DateUtil.fiscalYearOf(today))) {
            is ApiResult.Success -> {
                holidays += result.data.holidays
                overrides += result.data.overrides
            }
            is ApiResult.Failure -> {}
        }
        fun businessDaysFor(order: OrderAssignDto): Int? {
            val delivery = DateUtil.parse(order.delivery_date) ?: return null
            return DateUtil.businessDaysBetween(today, delivery, holidays, overrides)
        }
        orderSummary = OrderSummary(
            today = orders.count { businessDaysFor(it) == 0 },
            urgent = orders.count { (businessDaysFor(it) ?: Int.MAX_VALUE) <= 3 },
            unassigned = orders.count { o -> o.processes.any { it.status != WorkStatus.COMPLETED && it.worker.isNullOrBlank() } },
        )
    }

    private suspend fun loadShippingSummary() {
        val today = LocalDate.now()
        when (val result = managerRepo.shippingCalendar(today.year, today.monthValue)) {
            is ApiResult.Success -> {
                val day = result.data?.weeks?.flatten()?.firstOrNull { DateUtil.parse(it.date) == today }
                shippingTodayCount = day?.orders?.count { it.status != "shipped" && it.status != "billed" } ?: 0
            }
            is ApiResult.Failure -> {}
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
    onBack: () -> Unit,
    onContinue: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val flower = remember { FlowerOfDay.today(context) }
    val today = remember { LocalDate.now() }

    val canAssign by container.settings.canAssignFlow.collectAsState(initial = false)
    val canViewShipping by container.settings.canViewShippingFlow.collectAsState(initial = false)

    val vm: DashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DashboardViewModel(container.workerRepository, container.managerRepository) }
        },
    )
    // canAssign/canViewShippingは起動直後、DataStoreからの実際の値が届く前は
    // 一瞬だけ初期値(false)を返す。これをそのままload()の引数にすると、
    // 「一部カードが揃っていないダッシュボードが一瞬表示された直後にもう一度読み込み直す」
    // という表示のちらつきが起きるため、初回は実際の値が確定してから一度だけ読み込む
    LaunchedEffect(Unit) {
        val initialCanAssign = container.settings.canAssignFlow.first()
        val initialCanViewShipping = container.settings.canViewShippingFlow.first()
        vm.load(initialCanAssign, initialCanViewShipping)
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
                title = { HeaderTitle("ダッシュボード") },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    MyPageButton()
                    ProcessAssignmentButton()
                    IconButton(onClick = { feedback(); vm.load(canAssign, canViewShipping) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
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
                    orderSummary = if (canAssign) vm.orderSummary else null,
                    shippingTodayCount = if (canViewShipping) vm.shippingTodayCount else null,
                    onContinue = onContinue,
                )
                ScrollToTopFab(
                    visible = listState.firstVisibleItemIndex > 0,
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
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
    orderSummary: OrderSummary?,
    shippingTodayCount: Int?,
    onContinue: () -> Unit,
) {
    val feedback = rememberClickFeedback()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "greeting") {
            Column {
                Text(
                    "こんにちは、${userName}さん",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    DateUtil.shortLabel(today),
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (flower != null) {
            item(key = "flower") {
                DashboardCard("今日の花") {
                    Text(
                        "${flower.name}「${flower.meaning}」",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item(key = "notifications") {
            NotificationPreviewCard()
        }

        item(key = "task-summary") {
            DashboardCard("本日の担当作業") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock("未着手", taskSummary.waiting, Color(0xFF9CA3AF))
                    StatBlock("進行中", taskSummary.inProgress, MaterialTheme.colorScheme.primary)
                    StatBlock("完了", taskSummary.completed, Color(0xFF16A34A))
                }
            }
        }

        orderSummary?.let { summary ->
            item(key = "order-summary") {
                DashboardCard("受注ダッシュボード") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("本日納期", summary.today, Color(0xFFEA580C))
                        StatBlock("3営業日以内", summary.urgent, Color(0xFFDC2626))
                        StatBlock("未割り当て", summary.unassigned, Color(0xFF6B7280))
                    }
                }
            }
        }

        shippingTodayCount?.let { count ->
            item(key = "shipping-summary") {
                DashboardCard("本日の出荷予定") {
                    Text("$count 件", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
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

@Composable
private fun DashboardCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9CA3AF))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatBlock(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = color)
        Text(label, fontSize = 12.sp, color = Color(0xFF6B7280))
    }
}
