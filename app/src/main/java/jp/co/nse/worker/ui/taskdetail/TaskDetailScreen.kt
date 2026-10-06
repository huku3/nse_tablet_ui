package jp.co.nse.worker.ui.taskdetail

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ImageAttachment
import jp.co.nse.worker.data.ProcessBriefDto
import jp.co.nse.worker.data.ProcessDetailDto
import jp.co.nse.worker.data.TaskDetailDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import jp.co.nse.worker.ui.components.CameraCaptureDialog
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.PhotoAnnotateDialog
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.OrderStatusBadge
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.components.ScrollToBottomFab
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.tasklist.StatusChip
import jp.co.nse.worker.ui.tasklist.statusColor
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.ui.theme.Emerald500
import jp.co.nse.worker.ui.theme.Orange400
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.ui.theme.inkFor
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
    onSwitchToNextProcess: (nextProcessId: Int) -> Unit = {},
    onOpenProcess: (processId: Int) -> Unit = {},
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
    var showReworkDialog by remember { mutableStateOf(false) }
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
                            when {
                                isFirst && detail.needs_material_check -> showMaterialDialog = true
                                detail.partial != null -> vm.startItem()
                                else -> vm.changeStatus(WorkStatus.IN_PROGRESS)
                            }
                        },
                        onComplete = { showCompleteDialog = true },
                        onResume = { vm.changeStatus(WorkStatus.IN_PROGRESS) },
                        onPause = { showPauseDialog = true },
                        onBroken = { showBrokenDialog = true },
                        onRecover = { vm.changeStatus(WorkStatus.IN_PROGRESS) },
                        onDefect = { showDefectDialog = true },
                        onRequestRework = { showReworkDialog = true },
                        // 部分完了は PATCH status で待機に戻すと作業中の1個が残るため、専用の取り消しを使う
                        onUndoStart = {
                            if (detail.partial != null) vm.undoStartItem() else vm.changeStatus(WorkStatus.WAITING)
                        },
                        onResumeItem = { vm.resumeItem(it) },
                        onToggleBatch = { vm.setBatchMode(it) },
                        onOpenProcess = onOpenProcess,
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
                // 部分完了の複数人工程は PATCH status が使えない（422）ため、自分の1個だけを中断する
                if (vm.detail?.partial?.is_multi_worker == true) {
                    vm.pauseItem(WorkStatus.PAUSED, pauseReason = reason)
                } else {
                    vm.changeStatus(WorkStatus.PAUSED, pauseReason = reason)
                }
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

    if (showReworkDialog) {
        val d = vm.detail
        val candidates = d?.all_processes
            ?.filter { it.sort_order < d.process.sort_order }
            ?.sortedBy { it.sort_order }
            ?: emptyList()
        ReworkDialog(
            candidates = candidates,
            submitting = vm.actionRunning,
            serverError = vm.reworkError,
            onDismiss = {
                showReworkDialog = false
                vm.reworkError = null
            },
            onSubmit = { targetId, count, content, attachments ->
                vm.requestRework(targetId, count, content, attachments, onSuccess = { showReworkDialog = false })
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

    if (vm.stockNotice != null) {
        StockNoticeDialog(onConfirm = { vm.dismissStockNotice() })
    }

    if (showBrokenDialog) {
        val multiPartial = vm.detail?.partial?.is_multi_worker == true
        BrokenConfirmDialog(
            message = if (multiPartial) {
                "作業中の1個を「故障中」として報告します。あなたの作業は中断され、あとで一覧から選んで再開できます。"
            } else {
                "この工程を「故障中」として報告します。作業は中断され、復旧報告があるまで再開できません。"
            },
            onConfirm = {
                showBrokenDialog = false
                if (multiPartial) vm.pauseItem(WorkStatus.BROKEN) else vm.changeStatus(WorkStatus.BROKEN)
            },
            onCancel = { showBrokenDialog = false },
        )
    }

    if (showCompleteDialog) {
        val partial = vm.detail?.partial
        CompleteConfirmDialog(
            processName = vm.detail?.process?.process_name,
            itemLabel = partial?.let { p ->
                PartialItemLabel.of(
                    if (p.is_multi_worker) listOfNotNull(p.my?.active_item_index) else p.active_item_indexes,
                )
            },
            onConfirm = {
                showCompleteDialog = false
                val d = vm.detail
                val completedName = d?.process?.process_name.orEmpty()
                // 次の工程（sort_orderが直後）の担当者が自分と同じなら、作業一覧には戻らず
                // そのままその工程の作業詳細画面へ切り替える（連続作業を想定した動線）
                val nextSameWorkerProcessId = d?.all_processes
                    ?.filter { it.sort_order > d.process.sort_order }
                    ?.minByOrNull { it.sort_order }
                    ?.takeIf { it.worker == userName }
                    ?.id
                val afterProcessDone = {
                    if (nextSameWorkerProcessId != null) {
                        onSwitchToNextProcess(nextSameWorkerProcessId)
                    } else {
                        onCompleted(completedName)
                    }
                }
                if (partial != null) {
                    // 部分完了は作業中の分だけを完了にし、工程のすべての個が終わったときだけ通常の完了動線に進む
                    vm.completeItem(onProcessDone = afterProcessDone)
                } else {
                    vm.changeStatus(WorkStatus.COMPLETED, popOnSuccess = true, onPop = afterProcessDone)
                }
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
    onRequestRework: () -> Unit,
    onUndoStart: () -> Unit,
    onResumeItem: (itemIndex: Int) -> Unit,
    onToggleBatch: (Boolean) -> Unit,
    onOpenProcess: (processId: Int) -> Unit,
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
            onRequestRework = onRequestRework,
            onUndoStart = onUndoStart,
            onResumeItem = onResumeItem,
            onToggleBatch = onToggleBatch,
        )
    }

    val hasPipeline = detail.all_processes.isNotEmpty()
    val pipelineCard: @Composable () -> Unit = {
        ProcessPipelineCard(detail = detail, currentUserName = currentUserName, onOpenProcess = onOpenProcess)
    }
    val headerCard: @Composable () -> Unit = {
        DetailHeaderCard(detail = detail)
        val partial = detail.partial
        if (partial != null) {
            // 部分完了は1個ずつの表に担当者ごとの状態も出すため、複数人作業の案内カードは出さない
            Spacer(Modifier.height(12.dp))
            PartialItemsCard(partial = partial, currentUserName = currentUserName)
        } else if (detail.process.is_multi_worker) {
            Spacer(Modifier.height(12.dp))
            MultiWorkerInfoCard(process = detail.process)
        }
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
            ScrollToBottomFab(
                visible = scroll.value < scroll.maxValue,
                onClick = { scope.launch { scroll.animateScrollTo(scroll.maxValue) } },
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
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
                "No.${order.id}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
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

/**
 * 複数人割り当て可の工程の場合に、自分の参加状況と他の担当者の状況を示す案内カード。
 * 工程全体の集計ステータス（ヘッダーのStatusChip）とは別に、自分だけの状態を明示することで、
 * 「他の人が作業中でも自分は未着手」といった食い違いをその場で確認できるようにする。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MultiWorkerInfoCard(process: ProcessDetailDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "複数人作業の工程です（あなたの状態: ${WorkStatus.label(process.my_status ?: WorkStatus.WAITING)}）",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF3730A3),
            )
            if (process.co_workers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    process.co_workers.forEach { w ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                "${w.name}: ${WorkStatus.label(w.status)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF3730A3),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text("他に割り当てられている人はいません。", fontSize = 13.sp, color = Color(0xFF6366F1))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "他の人が作業中でも、あなたは自分の分だけ中断して別の受注の作業に移ることができます。",
                fontSize = 12.sp,
                color = Color(0xFF6366F1),
            )
        }
    }
}

/** 加工工程の横並びパイプライン。横向きでは画面幅いっぱいに表示し、工程数が多くても見切れないようにする */
@Composable
private fun ProcessPipelineCard(detail: TaskDetailDto, currentUserName: String, onOpenProcess: (processId: Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("加工工程", color = Color(0xFF9CA3AF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ProcessPipeline(
                detail.all_processes,
                currentId = detail.process.id,
                currentUserName = currentUserName,
                onOpenProcess = onOpenProcess,
            )
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
                InfoCell("注文数", order.quantity?.let { "$it" } ?: "—", Modifier.weight(1f), unit = order.quantity?.let { "個" })
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
internal val ActionAreaMaxWidth = 480.dp

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
    onRequestRework: () -> Unit,
    onUndoStart: () -> Unit,
    onResumeItem: (itemIndex: Int) -> Unit,
    onToggleBatch: (Boolean) -> Unit,
) {
    val partial = detail.partial
    if (partial != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().widthIn(max = ActionAreaMaxWidth)) {
                if (partial.is_multi_worker) {
                    MultiWorkerPartialActions(
                        detail = detail,
                        partial = partial,
                        actionRunning = actionRunning,
                        onStart = onStart,
                        onComplete = onComplete,
                        onPause = onPause,
                        onBroken = onBroken,
                        onResumeItem = onResumeItem,
                        onDefect = onDefect,
                        onRequestRework = onRequestRework,
                        onUndoStart = onUndoStart,
                    )
                } else {
                    SingleWorkerPartialActions(
                        detail = detail,
                        partial = partial,
                        actionRunning = actionRunning,
                        onStart = onStart,
                        onComplete = onComplete,
                        onResume = onResume,
                        onPause = onPause,
                        onBroken = onBroken,
                        onRecover = onRecover,
                        onDefect = onDefect,
                        onRequestRework = onRequestRework,
                        onToggleBatch = onToggleBatch,
                        onUndoStart = onUndoStart,
                    )
                }
            }
        }
        return
    }
    val feedback = rememberClickFeedback()
    // 複数人割り当て可の工程は、工程全体の集計ステータスではなく自分の参加状況でボタンを
    // 出し分ける（他の人が作業中でも、自分がまだ未着手なら「作業開始」、自分が中断中なら
    // 「再開」を出す）。API呼び出し自体は単独工程と同じ関数・同じエンドポイントを使う
    val myStatus = if (detail.process.is_multi_worker) {
        detail.process.my_status ?: WorkStatus.WAITING
    } else {
        detail.process.status
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().widthIn(max = ActionAreaMaxWidth)) {
            when (myStatus) {
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
                    // 追加修正は工程名で絞らず、工程自体が作業中のときに全工程で出す（tablet.js renderReworkSection 対応）
                    if (detail.process.status == WorkStatus.IN_PROGRESS) {
                        Spacer(Modifier.height(10.dp))
                        SubButton(
                            "追加修正が必要",
                            Color(0xFF9333EA),
                            Modifier.fillMaxWidth(),
                            enabled = !actionRunning,
                            onClick = onRequestRework,
                        )
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
                        if (detail.process.is_multi_worker) "あなたの担当分は完了しています。" else "この工程は完了しています。",
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
internal fun LockedBanner(blocking: String?, worker: String?) {
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
internal fun BigButton(
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
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = inkFor(color)),
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
internal fun SubButton(text: String, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
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
    unit: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF9FAFB))
            .padding(12.dp),
    ) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        // 数字と単位を同じTextに混ぜると、端末フォントによっては桁の大きさがばらついて
        // 見えることがあるため、数字と単位は別々のTextに分ける
        Row {
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
            unit?.let {
                Text(
                    it,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ProcessPipeline(
    processes: List<ProcessBriefDto>,
    currentId: Int,
    currentUserName: String,
    onOpenProcess: (processId: Int) -> Unit,
) {
    val feedback = rememberClickFeedback()
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isCurrent) { feedback(); onOpenProcess(p.id) }
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(circleColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDone && !isCurrent) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = inkFor(circleColor),
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (circleColor == Color(0xFFE5E7EB)) Color(0xFF9CA3AF) else inkFor(circleColor),
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

/** 追加修正に添付するファイル1件分。画像は[preview]でサムネイルを出し、PDFはファイル名だけ出す */
private class ReworkAttachment(val preview: Bitmap?, val attachment: ImageAttachment)

private const val REWORK_MAX_FILE_BYTES = 20L * 1024 * 1024
private const val REWORK_MAX_CONTENT = 2000
private const val REWORK_MAX_COUNT = 999
private val ReworkPurple = Color(0xFF9333EA)

/** サーバー側（mimes:jpg,jpeg,png,gif,pdf）と同じ許可形式 */
private val reworkMimeTypes = arrayOf("image/jpeg", "image/png", "image/gif", "application/pdf")

/**
 * ギャラリー・ファイルから選んだ[uri]を読み込む。形式・サイズが条件外ならエラーメッセージを返す。
 * 20MBを超えるファイルは読み込む前にサイズだけで弾く。
 */
private fun loadReworkAttachment(context: android.content.Context, uri: android.net.Uri): Result<ReworkAttachment> = runCatching {
    val resolver = context.contentResolver
    var name: String? = null
    var size: Long? = null
    resolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (nameIdx >= 0) name = c.getString(nameIdx)
            if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
        }
    }
    val filename = name ?: "attachment"
    val ext = filename.substringAfterLast('.', "").lowercase()
    val mime = resolver.getType(uri) ?: when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        else -> ""
    }
    require(mime in reworkMimeTypes) { "「$filename」は添付できない形式です（jpg / png / gif / pdf のみ）。" }
    require((size ?: 0L) <= REWORK_MAX_FILE_BYTES) { "「$filename」は20MBを超えているため添付できません。" }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("「$filename」を読み込めませんでした。")
    require(bytes.size <= REWORK_MAX_FILE_BYTES) { "「$filename」は20MBを超えているため添付できません。" }
    val preview = if (mime.startsWith("image/")) {
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } else {
        null
    }
    ReworkAttachment(preview, ImageAttachment(bytes = bytes, filename = filename, mimeType = mime))
}

/**
 * 「追加修正が必要」ダイアログ。作業中の工程であれば全工程で表示する（呼び出し元で絞り込み済み）。
 * [candidates]は現在の工程より前（sort_orderが小さい）の工程一覧で、手直しが必要な工程をここから選ぶ。
 * ブラウザ版タブレット画面（resources/js/tablet.js の renderReworkSection / submitRework）に合わせている。
 * 送信中([submitting])は二重送信を防ぎ、失敗時は入力内容を残したまま[serverError]を表示する。
 */
@Composable
private fun ReworkDialog(
    candidates: List<ProcessBriefDto>,
    submitting: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (targetProcessId: Int, count: Int, content: String, attachments: List<ImageAttachment>) -> Unit,
) {
    val feedback = rememberClickFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTarget by remember { mutableStateOf<ProcessBriefDto?>(null) }
    var countText by remember { mutableStateOf("1") }
    var content by remember { mutableStateOf("") }
    val attachments = remember { androidx.compose.runtime.mutableStateListOf<ReworkAttachment>() }
    var showCamera by remember { mutableStateOf(false) }
    var photoToAnnotate by remember { mutableStateOf<Bitmap?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var photoSeq by remember { mutableIntStateOf(1) }

    val pickFiles = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.map { loadReworkAttachment(context, it) }
            }
            results.forEach { r -> r.getOrNull()?.let { attachments.add(it) } }
            localError = results.mapNotNull { it.exceptionOrNull()?.message }.joinToString("\n").ifBlank { null }
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            filename = "rework_$photoSeq.jpg",
            onCaptured = { bitmap, _ ->
                // 撮って出しでは無く、マーキング画面を経由してから確定させる
                showCamera = false
                photoToAnnotate = bitmap
            },
            onDismiss = { showCamera = false },
        )
    }

    photoToAnnotate?.let { raw ->
        PhotoAnnotateDialog(
            bitmap = raw,
            filename = "rework_$photoSeq.jpg",
            onConfirm = { annotated, attachment ->
                attachments.add(ReworkAttachment(annotated, attachment))
                photoSeq++
                photoToAnnotate = null
            },
            onDismiss = { photoToAnnotate = null },
        )
    }

    fun submit() {
        val target = selectedTarget
        val count = countText.toIntOrNull()
        localError = when {
            target == null -> "対象工程を選択してください。"
            count == null || count !in 1..REWORK_MAX_COUNT -> "件数は1〜${REWORK_MAX_COUNT}で入力してください。"
            content.isBlank() -> "内容を入力してください。"
            else -> null
        }
        if (localError == null && target != null && count != null) {
            onSubmit(target.id, count, content.trim(), attachments.map { it.attachment })
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text("追加修正が必要な場合", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("対象工程（必須）", fontSize = 13.sp, color = Color(0xFF6B7280))
                Spacer(Modifier.height(4.dp))
                if (candidates.isEmpty()) {
                    Text("選択できる前工程がありません。", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        candidates.forEach { proc ->
                            val selected = selectedTarget?.id == proc.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) ReworkPurple.copy(alpha = 0.1f) else Color.Transparent)
                                    .clickable(enabled = !submitting) { feedback(); selectedTarget = proc }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { feedback(); selectedTarget = proc },
                                    enabled = !submitting,
                                    colors = RadioButtonDefaults.colors(selectedColor = ReworkPurple),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    proc.process_name,
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("件数", fontSize = 13.sp, color = Color(0xFF6B7280))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val current = countText.toIntOrNull() ?: 1
                    OutlinedButton(
                        onClick = { feedback(); countText = (current - 1).coerceIn(1, REWORK_MAX_COUNT).toString() },
                        enabled = !submitting,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("−", fontSize = 20.sp) }
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { v -> countText = v.filter { it.isDigit() }.take(3) },
                        enabled = !submitting,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        ),
                        modifier = Modifier.width(88.dp),
                    )
                    OutlinedButton(
                        onClick = { feedback(); countText = (current + 1).coerceIn(1, REWORK_MAX_COUNT).toString() },
                        enabled = !submitting,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) { Text("＋", fontSize = 20.sp) }
                }
                Spacer(Modifier.height(12.dp))
                Text("内容（必須）", fontSize = 13.sp, color = Color(0xFF6B7280))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(REWORK_MAX_CONTENT) },
                    enabled = !submitting,
                    placeholder = { Text("どこをどう直す必要があるか") },
                    minLines = 3,
                    supportingText = { Text("${content.length} / $REWORK_MAX_CONTENT") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("写真（任意・複数可）jpg / png / gif / pdf、1ファイル20MBまで", fontSize = 13.sp, color = Color(0xFF6B7280))
                if (attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEachIndexed { index, item ->
                            Box(Modifier.size(96.dp)) {
                                if (item.preview != null) {
                                    Image(
                                        bitmap = item.preview.asImageBitmap(),
                                        contentDescription = item.attachment.filename,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF3F4F6))
                                            .padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, tint = Color(0xFF6B7280))
                                        Text(
                                            item.attachment.filename,
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { feedback(); attachments.removeAt(index) },
                                    enabled = !submitting,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xCC000000)),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "添付を削除", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { feedback(); showCamera = true }, enabled = !submitting) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("撮影")
                    }
                    OutlinedButton(onClick = { feedback(); pickFiles.launch(reworkMimeTypes) }, enabled = !submitting) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("ギャラリー")
                    }
                }
                (localError ?: serverError)?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { feedback(); submit() },
                enabled = !submitting,
                colors = ButtonDefaults.buttonColors(containerColor = ReworkPurple),
            ) {
                if (submitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("登録中…")
                } else {
                    Text("追加修正を登録する")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { feedback(); onDismiss() }, enabled = !submitting) { Text("キャンセル") }
        },
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
private fun CompleteConfirmDialog(
    processName: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    itemLabel: String? = null,
) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("作業を完了しますか？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                buildString {
                    processName?.let { append("「$it」") }
                    itemLabel?.takeIf { it.isNotEmpty() }?.let { append("の$it") }
                    if (processName != null || !itemLabel.isNullOrEmpty()) append("を")
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
private fun StockNoticeDialog(onConfirm: () -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text("在庫処理が必要です", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "全工程が完了しました。この受注は仮注文のため、在庫処理が必要です。事務所にご確認ください。",
                fontSize = 14.sp,
            )
        },
        confirmButton = { Button(onClick = { feedback(); onConfirm() }) { Text("OK") } },
    )
}

@Composable
private fun BrokenConfirmDialog(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val feedback = rememberClickFeedback()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("故障として報告しますか？", fontWeight = FontWeight.Bold) },
        text = {
            Text(message, fontSize = 14.sp)
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
