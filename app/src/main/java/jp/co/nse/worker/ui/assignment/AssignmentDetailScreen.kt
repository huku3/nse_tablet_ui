package jp.co.nse.worker.ui.assignment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.AssignProcessDto
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.data.OrderType
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerDto
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Green700
import jp.co.nse.worker.ui.theme.Indigo700
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class AssignmentDetailViewModel(
    private val repo: ManagerRepository,
    private val orderId: Int,
) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var order by mutableStateOf<OrderAssignDto?>(null)
        private set
    var workers by mutableStateOf<List<WorkerDto>>(emptyList())
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var deadlineError by mutableStateOf<String?>(null)
        private set
    var holidayDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var overrideDates by mutableStateOf<Set<String>>(emptySet())
        private set
    private val loadedFiscalYears = mutableSetOf<Int>()

    var autoAssigning by mutableStateOf(false)
        private set
    var unassigningAll by mutableStateOf(false)
        private set

    /** 担当工程マスタを参照し、未割り当ての工程にデフォルト担当者を自動で割り振る */
    fun autoAssign() {
        viewModelScope.launch {
            autoAssigning = true
            when (val result = repo.autoAssign(orderId)) {
                is ApiResult.Success -> {
                    message = result.data.message
                    reloadOrder()
                }
                is ApiResult.Failure -> message = result.message
            }
            autoAssigning = false
        }
    }

    /** 担当者割り当て済みの未完了工程を、まとめて未割り当てに戻す */
    fun unassignAll() {
        viewModelScope.launch {
            unassigningAll = true
            when (val result = repo.unassignAll(orderId)) {
                is ApiResult.Success -> {
                    message = result.data.message
                    reloadOrder()
                }
                is ApiResult.Failure -> message = result.message
            }
            unassigningAll = false
        }
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val w = repo.workers()) {
                is ApiResult.Success -> workers = w.data
                is ApiResult.Failure -> { /* 候補取得失敗は致命的ではない */ }
            }
            when (val result = repo.orderDetail(orderId)) {
                is ApiResult.Success -> order = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun assign(processId: Int, workerName: String?) {
        val o = order ?: return
        viewModelScope.launch {
            saving = true
            val result = repo.assignWorker(o.id, processId, workerName)
            saving = false
            when (result) {
                is ApiResult.Success -> reloadOrder()
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    /** カレンダーに表示中の月の年度分の休日データを（未取得なら）読み込む */
    fun ensureHolidaysLoaded(visibleMonth: YearMonth) {
        val fy = DateUtil.fiscalYearOf(visibleMonth.atDay(1))
        if (fy in loadedFiscalYears) return
        loadedFiscalYears += fy
        viewModelScope.launch {
            when (val result = repo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidayDates = holidayDates + result.data.holidays
                    overrideDates = overrideDates + result.data.overrides
                }
                is ApiResult.Failure -> { /* 取得失敗時はサーバー側の最終チェックに委ねる */ }
            }
        }
    }

    fun updateDeadline(processId: Int, deadline: LocalDate?, onSuccess: () -> Unit) {
        val o = order ?: return
        viewModelScope.launch {
            saving = true
            deadlineError = null
            val result = repo.updateDeadline(o.id, processId, deadline?.toString())
            saving = false
            when (result) {
                is ApiResult.Success -> {
                    reloadOrder()
                    onSuccess()
                }
                is ApiResult.Failure -> deadlineError = result.message
            }
        }
    }

    fun clearDeadlineError() {
        deadlineError = null
    }

    private suspend fun reloadOrder() {
        when (val result = repo.orderDetail(orderId)) {
            is ApiResult.Success -> order = result.data
            is ApiResult.Failure -> message = result.message
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentDetailScreen(
    orderId: Int,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: AssignmentDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AssignmentDetailViewModel(container.managerRepository, orderId) }
        }
    )

    val feedback = rememberClickFeedback()
    val snackbar = remember { SnackbarHostState() }
    var pickerProcess by remember { mutableStateOf<AssignProcessDto?>(null) }
    var deadlineProcess by remember { mutableStateOf<AssignProcessDto?>(null) }
    var showAutoAssignConfirm by remember { mutableStateOf(false) }
    var showUnassignAllConfirm by remember { mutableStateOf(false) }
    val userName = rememberCurrentUserName()

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("担当者の割り当て") },
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
            val order = vm.order
            when {
                vm.loading && order == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null && order == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                    }
                }
                order != null -> {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            val hasUnassigned = order.processes.any {
                                it.status != WorkStatus.COMPLETED && it.worker.isNullOrBlank()
                            }
                            val hasAssigned = order.processes.any {
                                it.status != WorkStatus.COMPLETED && !it.worker.isNullOrBlank()
                            }
                            Column(Modifier.padding(bottom = 6.dp)) {
                                Text(
                                    order.part_name ?: "—",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    buildString {
                                        append("No.${order.id}")
                                        order.po_number?.let { append("　発注 $it") }
                                        order.part_number?.let {
                                            append("　品番 $it")
                                        }
                                    },
                                    fontSize = 13.sp,
                                    color = Color(0xFF6B7280),
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Button(
                                        onClick = { feedback(); showAutoAssignConfirm = true },
                                        enabled = hasUnassigned && !vm.autoAssigning && !vm.unassigningAll,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                    ) {
                                        if (vm.autoAssigning) {
                                            CircularProgressIndicator(
                                                Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "自動割り振り",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { feedback(); showUnassignAllConfirm = true },
                                        enabled = hasAssigned && !vm.autoAssigning && !vm.unassigningAll,
                                        colors = ButtonDefaults.buttonColors(containerColor = Red500),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(52.dp),
                                    ) {
                                        if (vm.unassigningAll) {
                                            CircularProgressIndicator(
                                                Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "一括で未割り当てに戻す",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val procs = order.processes.sortedBy { it.sort_order }
                        items(procs, key = { it.id }) { proc ->
                            ProcessAssignRow(
                                process = proc,
                                onChangeWorker = { pickerProcess = proc },
                                onChangeDeadline = { deadlineProcess = proc },
                            )
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

    pickerProcess?.let { proc ->
        vm.order?.let { order ->
            WorkerPickerDialog(
                order = order,
                processName = proc.process_name,
                current = proc.worker,
                workers = vm.workers,
                saving = vm.saving,
                onSelect = { name ->
                    pickerProcess = null
                    vm.assign(proc.id, name)
                },
                onDismiss = { pickerProcess = null },
            )
        }
    }

    deadlineProcess?.let { proc ->
        val order = vm.order
        if (order != null) {
            val sortedProcs = order.processes.sortedBy { it.sort_order }
            val prevProcess = sortedProcs.lastOrNull { it.sort_order < proc.sort_order }
            val minDate = prevProcess?.process_deadline?.let { DateUtil.parse(it) }
            val maxDate = DateUtil.parse(order.delivery_date)

            DeadlineCalendarDialog(
                order = order,
                processName = proc.process_name,
                initialDate = DateUtil.parse(proc.process_deadline),
                minDate = minDate,
                maxDate = maxDate,
                holidayDates = vm.holidayDates,
                overrideDates = vm.overrideDates,
                saving = vm.saving,
                serverError = vm.deadlineError,
                onVisibleMonthChanged = { vm.ensureHolidaysLoaded(it) },
                onConfirm = { date ->
                    vm.updateDeadline(proc.id, date) { deadlineProcess = null }
                },
                onDismiss = {
                    vm.clearDeadlineError()
                    deadlineProcess = null
                },
            )
        }
    }

    if (showAutoAssignConfirm) {
        AutoAssignConfirmDialog(
            onConfirm = {
                showAutoAssignConfirm = false
                vm.autoAssign()
            },
            onCancel = { showAutoAssignConfirm = false },
        )
    }

    if (showUnassignAllConfirm) {
        UnassignAllConfirmDialog(
            onConfirm = {
                showUnassignAllConfirm = false
                vm.unassignAll()
            },
            onCancel = { showUnassignAllConfirm = false },
        )
    }
}

@Composable
private fun AutoAssignConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("未割り当て工程を自動割り振りしますか？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "担当工程マスタでデフォルト担当者が設定されている工程に、未割り当て分をまとめて割り振ります。" +
                    "マスタに設定がない工程はスキップされます。",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = { feedback(); onConfirm() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("自動割り振りする") }
        },
        dismissButton = { TextButton(onClick = { feedback(); onCancel() }) { Text("キャンセル") } },
    )
}

@Composable
private fun UnassignAllConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("担当者を一括で未割り当てに戻しますか？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "この受注の未完了工程に割り当てられている担当者を、まとめて未割り当てに戻します。" +
                    "完了済みの工程は対象外です。この操作は取り消せません。",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = { feedback(); onConfirm() },
                colors = ButtonDefaults.buttonColors(containerColor = Red500),
            ) { Text("未割り当てに戻す") }
        },
        dismissButton = { TextButton(onClick = { feedback(); onCancel() }) { Text("キャンセル") } },
    )
}

/** 受注No・品番・品名・客先納期・注文数・区分をまとめて表示する（担当者/工程納期の変更モーダル共通ヘッダー） */
@Composable
fun OrderContextCard(order: OrderAssignDto) {
    val typeLabel = OrderType.label(order.order_type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F6))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("受注No.${order.id}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B7280))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Indigo700)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(typeLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(order.part_name ?: "—", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1F2937))
        order.part_number?.let {
            Spacer(Modifier.height(2.dp))
            Text("品番: $it", fontSize = 12.sp, color = Color(0xFF6B7280))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DateUtil.parse(order.delivery_date)?.let {
                OrderContextMiniLabel("客先納期", DateUtil.shortLabel(it))
            }
            order.quantity?.let { OrderContextMiniLabel("注文数", "$it 個") }
        }
    }
}

@Composable
private fun OrderContextMiniLabel(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.sp, color = Color(0xFF1F2937), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProcessAssignRow(
    process: AssignProcessDto,
    onChangeWorker: () -> Unit,
    onChangeDeadline: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val isCompleted = process.status == WorkStatus.COMPLETED
    val hasWorker = !process.worker.isNullOrBlank()
    val deadline = DateUtil.parse(process.process_deadline)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) Color(0xFFECFDF5) else Color.White,
        ),
        border = if (isCompleted) BorderStroke(1.5.dp, Color(0xFF6EE7B7)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        process.process_name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isCompleted) Color(0xFF065F46) else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (hasWorker) {
                        Text(
                            "担当: ${process.worker}",
                            fontSize = 14.sp,
                            color = if (isCompleted) Color(0xFF10B981) else Green700,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Text("未割当", fontSize = 14.sp, color = Color(0xFF9CA3AF))
                    }
                }
                if (isCompleted) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFD1FAE5))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("完了", color = Color(0xFF047857), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    TextButton(onClick = { feedback(); onChangeWorker() }) {
                        Text(if (hasWorker) "変更" else "割り当て", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (deadline != null) "工程納期: ${DateUtil.shortLabel(deadline)}" else "工程納期: 未設定",
                    fontSize = 13.sp,
                    fontWeight = if (deadline != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (deadline != null) Color(0xFF1F2937) else Color(0xFF9CA3AF),
                    modifier = Modifier.weight(1f),
                )
                if (!isCompleted) {
                    TextButton(onClick = { feedback(); onChangeDeadline() }) {
                        Text(if (deadline != null) "変更" else "設定", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerPickerDialog(
    order: OrderAssignDto,
    processName: String,
    current: String?,
    workers: List<WorkerDto>,
    saving: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("「$processName」の担当者", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                OrderContextCard(order)
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        WorkerRow(
                            name = "未割当に戻す",
                            color = Color(0xFF9CA3AF),
                            selected = current.isNullOrBlank(),
                            onClick = { onSelect(null) },
                        )
                    }
                    items(workers, key = { it.id }) { w ->
                        WorkerRow(
                            name = w.name,
                            color = parseHex(w.color),
                            selected = current == w.name,
                            onClick = { onSelect(w.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { feedback(); onDismiss() }, enabled = !saving) {
                Text("キャンセル", color = Color(0xFF6B7280))
            }
        },
    )
}

@Composable
private fun WorkerRow(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { feedback(); onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            name,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF1F2937),
        )
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

private fun parseHex(hex: String?): Color {
    if (hex.isNullOrBlank()) return Green600
    return runCatching {
        val h = hex.removePrefix("#")
        Color(
            h.substring(0, 2).toInt(16),
            h.substring(2, 4).toInt(16),
            h.substring(4, 6).toInt(16),
        )
    }.getOrDefault(Green600)
}
