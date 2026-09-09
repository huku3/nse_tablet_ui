package jp.co.nse.worker.ui.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ProcessBriefDto
import jp.co.nse.worker.data.TaskDetailDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.OrderStatusBadge
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.tasklist.StatusChip
import jp.co.nse.worker.ui.tasklist.statusColor
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.ui.theme.Emerald500
import jp.co.nse.worker.ui.theme.Orange400
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    processId: Int,
    onBack: () -> Unit,
    onViewDrawing: (processId: Int, title: String?, orderId: Int, poNumber: String?) -> Unit = { _, _, _, _ -> },
    onOpenCheckSheet: (orderId: Int) -> Unit = {},
    onLogout: () -> Unit = {},
    onCompleted: (processName: String) -> Unit = { onBack() },
) {
    val feedback = rememberClickFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: TaskDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { TaskDetailViewModel(container.workerRepository, processId) }
        }
    )

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPauseDialog by remember { mutableStateOf(false) }
    var showDefectDialog by remember { mutableStateOf(false) }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var showBrokenDialog by remember { mutableStateOf(false) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    val userName = rememberCurrentUserName()

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(vm.actionMessage) {
        vm.actionMessage?.let {
            snackbarHost.showSnackbar(it)
            vm.actionMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("作業詳細") },
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
            val detail = vm.detail
            when {
                vm.loading && detail == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null && detail == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                    }
                }
                detail != null -> {
                    DetailContent(
                        detail = detail,
                        actionRunning = vm.actionRunning,
                        onViewDrawing = {
                            onViewDrawing(detail.process.id, detail.order.part_name, detail.order.id, detail.order.po_number)
                        },
                        onOpenCheckSheet = { onOpenCheckSheet(detail.order.id) },
                        onStart = {
                            val procs = detail.all_processes.sortedBy { it.sort_order }
                            val isFirst = procs.firstOrNull()?.id == detail.process.id
                            if (isFirst && detail.needs_material_check) {
                                showMaterialDialog = true
                            } else {
                                vm.changeStatus(WorkStatus.IN_PROGRESS)
                            }
                        },
                        onComplete = { showCompleteDialog = true },
                        onResume = { vm.changeStatus(WorkStatus.IN_PROGRESS) },
                        onPause = { showPauseDialog = true },
                        onBroken = { showBrokenDialog = true },
                        onRecover = { vm.changeStatus(WorkStatus.IN_PROGRESS) },
                        onDefect = { showDefectDialog = true },
                        onUndoStart = { vm.changeStatus(WorkStatus.WAITING) },
                    )
                }
            }
        }
    }

    if (showPauseDialog) {
        PauseReasonDialog(
            onDismiss = { showPauseDialog = false },
            onSelect = { reason ->
                showPauseDialog = false
                vm.changeStatus(WorkStatus.PAUSED, pauseReason = reason)
            },
        )
    }

    if (showDefectDialog) {
        DefectDialog(
            onDismiss = { showDefectDialog = false },
            onSubmit = { count ->
                showDefectDialog = false
                vm.reportDefect(count)
            },
        )
    }

    if (showMaterialDialog) {
        val d = vm.detail
        MaterialCheckDialog(
            partNumber = d?.order?.part_number,
            partName = d?.order?.part_name,
            onConfirm = {
                showMaterialDialog = false
                vm.confirmMaterialThenStart()
            },
            onCancel = { showMaterialDialog = false },
        )
    }

    if (showBrokenDialog) {
        BrokenConfirmDialog(
            onConfirm = {
                showBrokenDialog = false
                vm.changeStatus(WorkStatus.BROKEN)
            },
            onCancel = { showBrokenDialog = false },
        )
    }

    if (showCompleteDialog) {
        CompleteConfirmDialog(
            processName = vm.detail?.process?.process_name,
            onConfirm = {
                showCompleteDialog = false
                val completedName = vm.detail?.process?.process_name.orEmpty()
                vm.changeStatus(
                    WorkStatus.COMPLETED,
                    popOnSuccess = true,
                    onPop = { onCompleted(completedName) },
                )
            },
            onCancel = { showCompleteDialog = false },
        )
    }
}

@Composable
private fun DetailContent(
    detail: TaskDetailDto,
    actionRunning: Boolean,
    onViewDrawing: () -> Unit,
    onOpenCheckSheet: () -> Unit,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onBroken: () -> Unit,
    onRecover: () -> Unit,
    onDefect: () -> Unit,
    onUndoStart: () -> Unit,
) {
    val currentUserName = rememberCurrentUserName()
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val actionArea: @Composable () -> Unit = {
        ActionArea(
            detail = detail,
            actionRunning = actionRunning,
            onStart = onStart,
            onComplete = onComplete,
            onResume = onResume,
            onPause = onPause,
            onBroken = onBroken,
            onRecover = onRecover,
            onDefect = onDefect,
            onUndoStart = onUndoStart,
        )
    }

    val hasPipeline = detail.all_processes.isNotEmpty()
    val pipelineCard: @Composable () -> Unit = {
        ProcessPipelineCard(detail = detail, currentUserName = currentUserName)
    }
    val headerCard: @Composable () -> Unit = {
        DetailHeaderCard(detail = detail)
    }
    val restCards: @Composable ColumnScope.() -> Unit = {
        DetailInfoCards(detail = detail, onOpenCheckSheet = onOpenCheckSheet, onViewDrawing = onViewDrawing)
    }

    if (isLandscape) {
        // 横向きでは、加工工程だけは画面幅いっぱいを使い（工程数が多くても見切れないように）、
        // その下を左右2カラムにして下までスクロールしなくても操作ボタンが見えるようにする
        Column(Modifier.fillMaxSize()) {
            if (hasPipeline) {
                Box(Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp)) {
                    pipelineCard()
                }
            }
            Row(Modifier.weight(1f)) {
                val leftScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(leftScroll)
                        .padding(16.dp),
                ) {
                    headerCard()
                    Spacer(Modifier.height(12.dp))
                    restCards()
                }
                val rightScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rightScroll)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    actionArea()
                }
            }
        }
    } else {
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
            ) {
                headerCard()
                Spacer(Modifier.height(12.dp))
                if (hasPipeline) {
                    pipelineCard()
                    Spacer(Modifier.height(12.dp))
                }
                restCards()
                Spacer(Modifier.height(20.dp))
                actionArea()
                Spacer(Modifier.height(88.dp))
            }
            ScrollToTopFab(
                visible = scroll.value > 0,
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }
}

@Composable
private fun DetailHeaderCard(detail: TaskDetailDto) {
    val process = detail.process
    val order = detail.order
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "受注No.${order.id}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                process.process_name,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(order.part_name ?: "—")
                    order.po_number?.let { append(" — $it") }
                },
                color = Color(0xFF6B7280),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status = process.status, color = statusColor(process.status))
                order.status?.let { OrderStatusBadge(it) }
            }
        }
    }
}

/** 加工工程の横並びパイプライン。横向きでは画面幅いっぱいに表示し、工程数が多くても見切れないようにする */
@Composable
private fun ProcessPipelineCard(detail: TaskDetailDto, currentUserName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("加工工程", color = Color(0xFF9CA3AF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ProcessPipeline(detail.all_processes, currentId = detail.process.id, currentUserName = currentUserName)
        }
    }
}

@Composable
private fun ColumnScope.DetailInfoCards(
    detail: TaskDetailDto,
    onOpenCheckSheet: () -> Unit,
    onViewDrawing: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val process = detail.process
    val order = detail.order

    // 情報グリッド
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCell("品名", order.part_name ?: "—", Modifier.weight(1f))
                InfoCell("発注番号", order.po_number ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                InfoCell("工程納期", DateUtil.monthDayLabel(process.process_deadline) ?: "—", Modifier.weight(1f))
                InfoCell("注文数", order.quantity?.let { "$it 個" } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clickable(onClick = { feedback(); onOpenCheckSheet() })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "工程管理チェックシートを見る",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textDecoration = TextDecoration.Underline,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    // 図面（登録がある場合のみ表示）
    if (order.has_drawing) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { feedback(); onViewDrawing() },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("図面を見る", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    // 注意事項
    process.notes?.takeIf { it.isNotBlank() }?.let { notes ->
        Spacer(Modifier.height(12.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Amber500, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("注意事項", color = Amber500, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(notes, color = Color(0xFF92400E), fontSize = 16.sp)
            }
        }
    }
}

// タブレットの広い画面幅いっぱいにボタンを伸ばすと誤タップしやすいため、アクション領域全体の幅に上限を設ける
private val ActionAreaMaxWidth = 480.dp

@Composable
private fun ActionArea(
    detail: TaskDetailDto,
    actionRunning: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onBroken: () -> Unit,
    onRecover: () -> Unit,
    onDefect: () -> Unit,
    onUndoStart: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().widthIn(max = ActionAreaMaxWidth)) {
            when (detail.process.status) {
                WorkStatus.WAITING -> {
                    if (!detail.can_start) {
                        LockedBanner(blocking = detail.blocking_process, worker = detail.blocking_worker)
                    } else {
                        BigButton(
                            text = "作業開始",
                            color = MaterialTheme.colorScheme.primary,
                            enabled = !actionRunning,
                            onClick = onStart,
                        )
                    }
                }

                WorkStatus.IN_PROGRESS -> {
                    BigButton(
                        text = "加工完了へ進む",
                        color = Amber500,
                        enabled = !actionRunning,
                        onClick = onComplete,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SubButton("中断", Orange400, Modifier.weight(1f), enabled = !actionRunning, onClick = onPause)
                        SubButton("故障中", Red500, Modifier.weight(1f), enabled = !actionRunning, onClick = onBroken)
                        SubButton("不良品報告", Color(0xFFE11D48), Modifier.weight(1f), enabled = !actionRunning, onClick = onDefect)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { feedback(); onUndoStart() },
                        enabled = !actionRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("誤って開始した場合はこちら（待機に戻す）", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                    }
                }

                WorkStatus.PAUSED -> {
                    BigButton(
                        text = "作業を再開する",
                        color = MaterialTheme.colorScheme.primary,
                        enabled = !actionRunning,
                        onClick = onResume,
                    )
                    Spacer(Modifier.height(12.dp))
                    SubButton("故障中として報告", Red500, Modifier.fillMaxWidth(), enabled = !actionRunning, onClick = onBroken)
                }

                WorkStatus.BROKEN -> {
                    BigButton(
                        text = "復旧して作業を再開",
                        color = Color(0xFF4B5563),
                        enabled = !actionRunning,
                        onClick = onRecover,
                        icon = Icons.Filled.Build,
                    )
                }

                else -> {
                    Text(
                        "この工程は完了しています。",
                        color = Emerald500,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockedBanner(blocking: String?, worker: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF92400E), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("前の工程が完了していません", fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                if (!blocking.isNullOrBlank()) {
                    Text(
                        "「$blocking」が完了するまで待機してください",
                        color = Amber500,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    "担当: ${worker?.takeIf { it.isNotBlank() } ?: "未割当"}",
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    BigButton(
        text = "作業開始（前工程待ち）",
        color = Color(0xFFD1D5DB),
        enabled = false,
        onClick = {},
        icon = Icons.Filled.Lock,
    )
}

@Composable
private fun BigButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val feedback = rememberClickFeedback()
    Button(
        onClick = { feedback(); onClick() },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    }
}

@Composable
private fun SubButton(text: String, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    Button(
        onClick = { feedback(); onClick() },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(56.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun InfoCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF9FAFB))
            .padding(12.dp),
    ) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ProcessPipeline(processes: List<ProcessBriefDto>, currentId: Int, currentUserName: String) {
    val sorted = processes.sortedBy { it.sort_order }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        sorted.forEachIndexed { index, p ->
            val isCurrent = p.id == currentId
            val isDone = p.status == WorkStatus.COMPLETED
            val hasWorker = !p.worker.isNullOrBlank()
            val isSelfWorker = currentUserName.isNotBlank() && p.worker == currentUserName
            val circleColor = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isDone -> Emerald500
                else -> Color(0xFFE5E7EB)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(circleColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone && !isCurrent) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (circleColor == Color(0xFFE5E7EB)) Color(0xFF9CA3AF) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 担当者名が長くても省略されないよう、幅の制約を外して全文表示する
                Text(
                    p.process_name,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                    fontSize = 13.sp,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                    softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (hasWorker) p.worker!! else "未割当",
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                    fontSize = 13.sp,
                    fontWeight = if (isSelfWorker) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = when {
                        isSelfWorker -> MaterialTheme.colorScheme.primary
                        hasWorker -> Color(0xFF374151)
                        else -> Color(0xFFD1D5DB)
                    },
                    softWrap = false,
                )
                DateUtil.monthDayLabel(p.process_deadline)?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        it,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// ===== ダイアログ =====

@Composable
private fun PauseReasonDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("中断理由を選択", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WorkStatus.pauseReasons.forEach { reason ->
                    OutlinedButton(
                        onClick = { feedback(); onSelect(reason) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(reason, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { feedback(); onDismiss() }) { Text("キャンセル") } },
    )
}

@Composable
private fun DefectDialog(onDismiss: () -> Unit, onSubmit: (Int) -> Unit) {
    val feedback = rememberClickFeedback()
    var count by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("不良品を報告", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("不良品の個数を入力してください。", fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = { feedback(); if (count > 1) count-- },
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("−", fontSize = 24.sp) }
                    Text("$count", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE11D48))
                    OutlinedButton(
                        onClick = { feedback(); count++ },
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("＋", fontSize = 24.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Text("個", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "・不良品数が入力した数だけ記録されます\n・作業はそのまま継続します（中断しません）",
                    color = Amber500,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { feedback(); onSubmit(count) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE11D48)),
            ) { Text("報告する") }
        },
        dismissButton = { TextButton(onClick = { feedback(); onDismiss() }) { Text("キャンセル") } },
    )
}

@Composable
private fun MaterialCheckDialog(
    partNumber: String?,
    partName: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("材料確認", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("材料到着日です。材料と品番・品名の確認は大丈夫ですか？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("品番: ${partNumber ?: "—"}", fontSize = 14.sp)
                Text("品名: ${partName ?: "—"}", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("OKを押すと作業を開始します。", color = Color(0xFF9CA3AF), fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { feedback(); onConfirm() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { feedback(); onCancel() }) { Text("キャンセル") } },
    )
}

@Composable
private fun CompleteConfirmDialog(processName: String?, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("作業を完了しますか？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                buildString {
                    processName?.let { append("「$it」を") }
                    append("完了として記録します。この操作は取り消せません。")
                },
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = { feedback(); onConfirm() },
                colors = ButtonDefaults.buttonColors(containerColor = Amber500),
            ) { Text("完了する") }
        },
        dismissButton = { TextButton(onClick = { feedback(); onCancel() }) { Text("キャンセル") } },
    )
}

@Composable
private fun BrokenConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("故障として報告しますか？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "この工程を「故障中」として報告します。作業は中断され、復旧報告があるまで再開できません。",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            Button(
                onClick = { feedback(); onConfirm() },
                colors = ButtonDefaults.buttonColors(containerColor = Red500),
            ) { Text("故障として報告する") }
        },
        dismissButton = { TextButton(onClick = { feedback(); onCancel() }) { Text("キャンセル") } },
    )
}
