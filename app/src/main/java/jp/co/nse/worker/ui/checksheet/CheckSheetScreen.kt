package jp.co.nse.worker.ui.checksheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.AssignProcessDto
import jp.co.nse.worker.data.CheckSheetOrderDto
import jp.co.nse.worker.data.CheckSheetProcessDto
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.data.OrderType
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.ui.assignment.DeadlineCalendarDialog
import jp.co.nse.worker.ui.assignment.WorkerPickerDialog
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.DashboardButton
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.OrderStatus
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.tasklist.StatusChip
import jp.co.nse.worker.ui.tasklist.statusColor
import jp.co.nse.worker.ui.theme.Gray500
import jp.co.nse.worker.ui.theme.Gray800
import jp.co.nse.worker.ui.theme.Indigo700
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch

/**
 * 工程管理チェックシート。
 * PC/Web版 orders.checksheet の内容をタブレット向けに再構成したもの。ガントチャートは対象外。
 * 担当者の割り当て権限があるユーザーは、この画面からも加工担当者・工程納期を変更できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckSheetScreen(
    orderId: Int,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val feedback = rememberClickFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: CheckSheetViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CheckSheetViewModel(container.workerRepository, container.managerRepository, orderId) }
        }
    )
    val userName = rememberCurrentUserName()
    val canAssign by container.settings.canAssignFlow.collectAsState(initial = false)
    val snackbar = remember { SnackbarHostState() }
    var pickerProcess by remember { mutableStateOf<CheckSheetProcessDto?>(null) }
    var deadlineProcess by remember { mutableStateOf<CheckSheetProcessDto?>(null) }
    var showMarkArrivedConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(canAssign) { if (canAssign) vm.loadWorkers() }
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
                title = { HeaderTitle("工程管理チェックシート") },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    MyPageButton()
                    DashboardButton()
                    ProcessAssignmentButton()
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
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF3F4F6)),
        ) {
            val order = vm.order
            when {
                vm.loading && order == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                vm.error != null && order == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                }
                order != null -> CheckSheetContent(
                    order = order,
                    canAssign = canAssign,
                    onChangeWorker = { pickerProcess = it },
                    onChangeDeadline = { deadlineProcess = it },
                    markingArrived = vm.markingArrived,
                    onMarkArrived = { showMarkArrivedConfirm = true },
                )
            }
        }
    }

    pickerProcess?.let { proc ->
        vm.order?.let { order ->
            WorkerPickerDialog(
                order = order.toOrderAssignDto(),
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
                order = order.toOrderAssignDto(),
                processName = proc.process_name,
                initialDate = DateUtil.parse(proc.process_deadline),
                minDate = minDate,
                maxDate = maxDate,
                holidayDates = vm.holidayDates,
                overrideDates = vm.overrideDates,
                saving = vm.saving,
                serverError = vm.deadlineError,
                onVisibleMonthChanged = { vm.ensureHolidaysLoaded(it) },
                onConfirm = { date -> vm.updateDeadline(proc.id, date) { deadlineProcess = null } },
                onDismiss = {
                    vm.clearDeadlineError()
                    deadlineProcess = null
                },
            )
        }
    }

    if (showMarkArrivedConfirm) {
        MarkArrivedConfirmDialog(
            onConfirm = {
                showMarkArrivedConfirm = false
                vm.markMaterialArrived()
            },
            onCancel = { showMarkArrivedConfirm = false },
        )
    }
}

@Composable
private fun MarkArrivedConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val feedback = rememberClickFeedback()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("材料を到着済みにしますか？", fontWeight = FontWeight.Bold) },
        text = { Text("この受注のステータスを「材料到着済み」に変更します。", fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = { feedback(); onConfirm() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("到着済みにする") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { feedback(); onCancel() }) { Text("キャンセル") }
        },
    )
}

private fun CheckSheetOrderDto.toOrderAssignDto(): OrderAssignDto = OrderAssignDto(
    id = id,
    po_number = po_number,
    customer_order_number = customer_order_number,
    part_name = part_name,
    part_number = part_number,
    delivery_date = delivery_date,
    quantity = quantity,
    status = status,
    order_type = order_type,
    processes = processes.map { it.toAssignProcessDto() },
)

private fun CheckSheetProcessDto.toAssignProcessDto(): AssignProcessDto = AssignProcessDto(
    id = id,
    process_name = process_name,
    status = status,
    sort_order = sort_order,
    worker = worker,
    process_deadline = process_deadline,
    process_deadline_start = process_deadline_start,
)

@Composable
private fun CheckSheetContent(
    order: CheckSheetOrderDto,
    canAssign: Boolean,
    onChangeWorker: (CheckSheetProcessDto) -> Unit,
    onChangeDeadline: (CheckSheetProcessDto) -> Unit,
    markingArrived: Boolean,
    onMarkArrived: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                OrderStatusHeader(order)
                if (!order.po_number.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("発注番号: ${order.po_number}", color = Gray500, fontSize = 14.sp)
                }
            }
        }

        BasicInfoCard(order)
        MaterialArrivalBanner(order, markingArrived = markingArrived, onMarkArrived = onMarkArrived)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("区分", color = Gray500, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                OrderTypeRow(order)
            }
        }

        ProcessTable(order, canAssign = canAssign, onChangeWorker = onChangeWorker, onChangeDeadline = onChangeDeadline)
        Spacer(Modifier.height(88.dp))
    }
    ScrollToTopFab(
        visible = scrollState.value > 0,
        onClick = { scope.launch { scrollState.animateScrollTo(0) } },
        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
    )
    }
}

@Composable
private fun OrderStatusHeader(order: CheckSheetOrderDto) {
    val style = OrderStatus.style(order.status)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            order.part_name?.takeIf { it.isNotBlank() } ?: "受注 #${order.id}",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = Gray800,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(style.bg)
                .border(1.dp, style.border, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(style.label, color = style.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BasicInfoCard(order: CheckSheetOrderDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) {
                    LabeledRow("客先", customerLabel(order))
                    LabeledRow("品番", order.part_number.orDash(), mono = true)
                    LabeledRow("品名", order.part_name.orDash())
                    LabeledRow("材料名", order.product?.material_name.orDash())
                    LabeledRow("材料商社", materialSupplierLabel(order))
                    LabeledRow("材料サイズ", (order.product?.material_size ?: order.material_size).orDash(), mono = true)
                }
                Column(Modifier.weight(1f)) {
                    LabeledRow("No", "#${order.id}", mono = true)
                    LabeledRow("発行日", DateUtil.longJapaneseLabel(order.created_at).orDash())
                    LabeledRow("発注番号", order.po_number.orDash(), mono = true)
                    LabeledRow("客先注文番号", order.customer_order_number.orDash(), mono = true)
                    LabeledRow(
                        "客先納期",
                        DateUtil.longJapaneseLabel(order.delivery_date).orDash(),
                        highlight = true,
                    )
                    LabeledRow("注文数", order.quantity?.let { "$it 個" }.orDash())
                    LabeledRow("不良数（合計）", (order.defect_count ?: 0).let { if (it > 0) "$it 個" else "―" })
                    LabeledRow("材料入荷日", DateUtil.longJapaneseLabel(order.material_arrived_at).orDash())
                }
            }
            order.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF9FAFB))
                        .padding(12.dp),
                ) {
                    Text("注意事項", color = Gray500, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(notes, fontSize = 16.sp, color = Color(0xFF374151))
                }
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String, mono: Boolean = false, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (highlight) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF2F2))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (highlight) Color(0xFFB91C1C) else Gray500,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(108.dp),
        )
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (highlight) Color(0xFFDC2626) else Color(0xFF1F2937),
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun MaterialArrivalBanner(order: CheckSheetOrderDto, markingArrived: Boolean, onMarkArrived: () -> Unit) {
    val dateLabel = DateUtil.monthDayLabel(order.material_arrived_at) ?: return
    data class Style(val bg: Color, val text: Color, val message: String, val icon: androidx.compose.ui.graphics.vector.ImageVector?)
    val style = when (order.status) {
        "material_arrived" -> Style(Color(0xFFECFDF5), Color(0xFF065F46), "材料到着済み", Icons.Filled.Check)
        "material_arrived_date" -> Style(
            Color(0xFFFFFBEB),
            Color(0xFF92400E),
            "材料到着日（$dateLabel）— 到着確認が必要です",
            Icons.Filled.Warning,
        )
        else -> Style(Color(0xFFF9FAFB), Color(0xFF374151), "材料入荷予定日: $dateLabel", null)
    }
    val canMarkArrived = order.status == "material_arrived_date"
    Card(
        colors = CardDefaults.cardColors(containerColor = style.bg),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (style.icon != null) {
                Icon(style.icon, contentDescription = null, tint = style.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(style.message, color = style.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onMarkArrived,
                enabled = canMarkArrived && !markingArrived,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                if (markingArrived) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (canMarkArrived) "到着済みにする" else "到着確認済み",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderTypeRow(order: CheckSheetOrderDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OrderType.all.forEach { (key, label) ->
            val active = (order.order_type ?: OrderType.INITIAL) == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Indigo700 else Color.White)
                    .border(2.dp, if (active) Indigo700 else Color(0xFFD1D5DB), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Color(0xFF6B7280),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

// 画面の向き・幅に応じて自動で伸縮するよう、固定幅ではなく比率（weight）で列幅を決める
// （元の固定幅 130/120/70/110/100/170/90/70dp の比率をそのまま踏襲）
private const val ProcessNameWeight = 1.3f
private const val StatusWeight = 1.2f
private const val ProcessDateWeight = 0.7f
private const val WorkerWeight = 1.1f
private const val DeadlineWeight = 1.0f
private const val NotesWeight = 1.7f
private const val QuantityWeight = 0.9f
private const val DefectWeight = 0.7f

@Composable
private fun ProcessTable(
    order: CheckSheetOrderDto,
    canAssign: Boolean,
    onChangeWorker: (CheckSheetProcessDto) -> Unit,
    onChangeDeadline: (CheckSheetProcessDto) -> Unit,
) {
    val totalProcessing = (order.quantity ?: 0) + (order.defect_count ?: 0)
    val sorted = order.processes.sortedBy { it.sort_order }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                "工程一覧",
                color = Gray500,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
            )
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155))
                        .padding(vertical = 10.dp),
                ) {
                    HeaderCell("工程名", ProcessNameWeight)
                    HeaderCell("ステータス", StatusWeight)
                    HeaderCell("加工日", ProcessDateWeight)
                    HeaderCell("作業者", WorkerWeight)
                    HeaderCell("工程納期", DeadlineWeight)
                    HeaderCell("注意事項", NotesWeight)
                    HeaderCell("加工数", QuantityWeight)
                    HeaderCell("不良数", DefectWeight)
                }

                if (sorted.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("工程が登録されていません。", color = Gray500, fontSize = 15.sp)
                    }
                } else {
                    sorted.forEachIndexed { index, process ->
                        ProcessRow(
                            process = process,
                            totalProcessing = totalProcessing,
                            quantity = order.quantity ?: 0,
                            defectCount = order.defect_count ?: 0,
                            rowBg = if (index % 2 == 0) Color.White else Color(0xFFF8FAFC),
                            canAssign = canAssign,
                            onChangeWorker = { onChangeWorker(process) },
                            onChangeDeadline = { onChangeDeadline(process) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    process: CheckSheetProcessDto,
    totalProcessing: Int,
    quantity: Int,
    defectCount: Int,
    rowBg: Color,
    canAssign: Boolean,
    onChangeWorker: () -> Unit,
    onChangeDeadline: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val isCompleted = process.status == WorkStatus.COMPLETED
    val canEdit = canAssign && !isCompleted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            process.process_name,
            modifier = Modifier.weight(ProcessNameWeight).padding(horizontal = 6.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCompleted) Color(0xFF9CA3AF) else Color(0xFF1F2937),
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            softWrap = true,
        )
        Box(Modifier.weight(StatusWeight), contentAlignment = Alignment.Center) {
            StatusChip(status = process.status, color = statusColor(process.status))
        }
        Text(
            DateUtil.monthDayLabel(process.process_date).orEmpty(),
            modifier = Modifier.weight(ProcessDateWeight).padding(horizontal = 4.dp),
            fontSize = 14.sp,
            color = Color(0xFF374151),
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .weight(WorkerWeight)
                .padding(horizontal = 4.dp)
                .then(if (canEdit) Modifier.clickable { feedback(); onChangeWorker() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!process.worker.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF6366F1))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            process.worker,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Text("―", color = Color(0xFFD1D5DB), fontSize = 14.sp)
                }
                if (canEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("変更", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(DeadlineWeight)
                .padding(horizontal = 4.dp)
                .then(if (canEdit) Modifier.clickable { feedback(); onChangeDeadline() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    deadlineLabel(process),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                if (canEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("変更", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            process.notes.orEmpty(),
            modifier = Modifier.weight(NotesWeight).padding(horizontal = 6.dp),
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
        )
        Column(
            modifier = Modifier.weight(QuantityWeight).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (totalProcessing > 0) "$totalProcessing" else "―",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
            )
            if (defectCount > 0) {
                Text("（$quantity+$defectCount）", fontSize = 11.sp, color = Color(0xFF9CA3AF))
            }
        }
        Box(Modifier.weight(DefectWeight), contentAlignment = Alignment.Center) {
            val d = process.defect_count ?: 0
            if (d > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEE2E2))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("$d", color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Box(Modifier.weight(weight).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private fun materialSupplierLabel(order: CheckSheetOrderDto): String {
    val product = order.product
    if (product == null) return order.material_supplier.orDash()
    if (product.material_supplier_type == "客先支給") return "客先支給"
    product.supplier?.name?.takeIf { it.isNotBlank() }?.let { return it }
    if (!product.material_supplier_type.isNullOrBlank() && product.material_supplier_type != "supplier") {
        return product.material_supplier_type
    }
    return order.material_supplier.orDash()
}

private fun customerLabel(order: CheckSheetOrderDto): String =
    order.product?.customer?.name?.takeIf { it.isNotBlank() }
        ?: order.customer_name?.takeIf { it.isNotBlank() }
        ?: "―"

private fun deadlineLabel(process: CheckSheetProcessDto): String {
    val end = DateUtil.monthDayLabel(process.process_deadline)
    val start = DateUtil.monthDayLabel(process.process_deadline_start)
    return when {
        start != null && end != null -> "$start〜$end"
        end != null -> end
        else -> "―"
    }
}

private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "―"
