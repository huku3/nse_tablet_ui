package jp.co.nse.worker.ui.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.data.MaterialAllocationDto
import jp.co.nse.worker.data.MaterialCandidateDto
import jp.co.nse.worker.data.MaterialInventoryDto
import jp.co.nse.worker.data.MaterialTransactionDto
import jp.co.nse.worker.data.OrderAssignDto
import jp.co.nse.worker.ui.components.DashboardButton
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.OrderStatus
import jp.co.nse.worker.ui.components.OrderStatusBadge
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.ScrollToBottomFab
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 引き当てにより自動で「材料到着済み」に変わる受注ステータス（Web版と同一） */
private val MaterialStatuses = setOf("material_confirming", "material_waiting", "material_arrived_date")

private enum class InventoryTab(val label: String) { PROCESSED("加工済み在庫"), MATERIAL("支給品在庫") }

class InventoryViewModel(private val repo: ManagerRepository) : ViewModel() {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var orders by mutableStateOf<List<OrderAssignDto>>(emptyList())
        private set
    var materials by mutableStateOf<List<MaterialInventoryDto>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)

    var candidatesLoading by mutableStateOf<Set<Int>>(emptySet())
        private set
    var candidatesByMaterial by mutableStateOf<Map<Int, List<MaterialCandidateDto>>>(emptyMap())
        private set

    /** 完了して在庫に積まれた受注（加工済み在庫タブ用。閲覧専用） */
    val processedOrders: List<OrderAssignDto> get() = orders.filter { it.status == "stocked" }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.orders()) {
                is ApiResult.Success -> orders = result.data
                is ApiResult.Failure -> error = result.message
            }
            loadMaterials()
            loading = false
        }
    }

    private suspend fun loadMaterials() {
        when (val result = repo.materialInventories()) {
            is ApiResult.Success -> materials = result.data
            is ApiResult.Failure -> if (error == null) error = result.message
        }
    }

    /** 支給品在庫1件分だけ再取得（アクション後の更新用。都度サーバーの最新値を信頼する） */
    fun refreshMaterials() {
        viewModelScope.launch { loadMaterials() }
    }

    fun loadCandidates(materialInventoryId: Int) {
        if (materialInventoryId in candidatesByMaterial || materialInventoryId in candidatesLoading) return
        candidatesLoading = candidatesLoading + materialInventoryId
        viewModelScope.launch {
            when (val result = repo.materialCandidates(materialInventoryId)) {
                is ApiResult.Success -> candidatesByMaterial = candidatesByMaterial + (materialInventoryId to result.data)
                is ApiResult.Failure -> message = result.message
            }
            candidatesLoading = candidatesLoading - materialInventoryId
        }
    }

    fun restock(materialInventoryId: Int, quantity: Int, note: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repo.restockMaterial(materialInventoryId, quantity, note)) {
                is ApiResult.Success -> {
                    message = "入荷を記録しました。"
                    loadMaterials()
                    onDone()
                }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun allocate(materialInventoryId: Int, orderId: Int, quantity: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            when (val result = repo.allocateMaterial(materialInventoryId, orderId, quantity)) {
                is ApiResult.Success -> {
                    message = "受注 No.$orderId へ引き当てました。"
                    loadMaterials()
                    onDone()
                }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun deallocate(allocationId: Int) {
        viewModelScope.launch {
            when (val result = repo.deallocateMaterial(allocationId)) {
                is ApiResult.Success -> {
                    message = "引き当てを取り消しました。"
                    loadMaterials()
                }
                is ApiResult.Failure -> message = result.message
            }
        }
    }

    fun deleteTransaction(transactionId: Int) {
        viewModelScope.launch {
            when (val result = repo.deleteMaterialTransaction(transactionId)) {
                is ApiResult.Success -> {
                    message = "入荷の取り消しをしました。"
                    loadMaterials()
                }
                is ApiResult.Failure -> message = result.message
            }
        }
    }
}

/**
 * 在庫一覧：加工済み在庫（閲覧専用）／支給品在庫（入荷・引当・履歴）の2タブ構成。
 * 支給品在庫そのものの新規登録・編集・削除はこの画面では扱わない（Web版のみ）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(onLogout: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: InventoryViewModel = viewModel(
        factory = viewModelFactory { initializer { InventoryViewModel(container.managerRepository) } },
    )
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(InventoryTab.PROCESSED) }

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
                title = { HeaderTitle("在庫") },
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
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    InventoryTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { feedback(); tab = t },
                            text = { Text(t.label, fontWeight = FontWeight.Bold) },
                        )
                    }
                }

                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                val scope = rememberCoroutineScope()

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        vm.loading && vm.orders.isEmpty() && vm.materials.isEmpty() ->
                            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)

                        vm.error != null && vm.orders.isEmpty() && vm.materials.isEmpty() -> Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                        }

                        tab == InventoryTab.PROCESSED -> {
                            val processed = vm.processedOrders
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (processed.isEmpty()) {
                                    item(key = "empty") {
                                        Text(
                                            "加工済み在庫はありません",
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = Color(0xFF9CA3AF),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    items(processed, key = { it.id }) { order -> ProcessedOrderCard(order) }
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (vm.materials.isEmpty()) {
                                    item(key = "empty") {
                                        Text(
                                            "支給品在庫はありません",
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            color = Color(0xFF9CA3AF),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                } else {
                                    items(vm.materials, key = { it.id }) { mi ->
                                        MaterialInventoryCard(
                                            material = mi,
                                            candidates = vm.candidatesByMaterial[mi.id],
                                            candidatesLoading = mi.id in vm.candidatesLoading,
                                            onExpandAllocate = { vm.loadCandidates(mi.id) },
                                            onRestock = { qty, note, onDone -> vm.restock(mi.id, qty, note, onDone) },
                                            onAllocate = { orderId, qty, onDone -> vm.allocate(mi.id, orderId, qty, onDone) },
                                            onDeallocate = { allocationId -> vm.deallocate(allocationId) },
                                            onDeleteTransaction = { transactionId -> vm.deleteTransaction(transactionId) },
                                        )
                                    }
                                }
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

@Composable
private fun ProcessedOrderCard(order: OrderAssignDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    order.customer_name?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = Color(0xFF6B7280))
                    }
                    Text(order.part_name ?: "—", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                OrderStatusBadge(order.status)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("受注No.${order.id}", fontSize = 13.sp, color = Color(0xFF6B7280))
                order.quantity?.let { Text("数量 $it", fontSize = 13.sp, color = Color(0xFF6B7280)) }
            }
        }
    }
}

private enum class MaterialPanel { RESTOCK, ALLOCATE, HISTORY }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MaterialInventoryCard(
    material: MaterialInventoryDto,
    candidates: List<MaterialCandidateDto>?,
    candidatesLoading: Boolean,
    onExpandAllocate: () -> Unit,
    onRestock: (quantity: Int, note: String?, onDone: () -> Unit) -> Unit,
    onAllocate: (orderId: Int, quantity: Int, onDone: () -> Unit) -> Unit,
    onDeallocate: (allocationId: Int) -> Unit,
    onDeleteTransaction: (transactionId: Int) -> Unit,
) {
    val feedback = rememberClickFeedback()
    var expanded by remember { mutableStateOf<MaterialPanel?>(null) }
    val effective = (material.quantity - material.allocated_quantity).coerceAtLeast(0)
    val isShortage = material.quantity > 0 && effective <= 0

    fun toggle(panel: MaterialPanel) {
        feedback()
        expanded = if (expanded == panel) null else panel
        if (expanded == MaterialPanel.ALLOCATE) onExpandAllocate()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = if (isShortage) BorderStrokeRed else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(material.material_name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "${material.material_size ?: "—"}　${material.customer_name?.takeIf { it.isNotBlank() } ?: "客先未設定"}",
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
            )
            Spacer(Modifier.height(6.dp))
            if (material.part_numbers.isEmpty()) {
                Text("品番未設定", fontSize = 13.sp, color = Color(0xFF9CA3AF))
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    material.part_numbers.forEach { partNumber ->
                        Text(
                            partNumber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4338CA),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEEF2FF))
                                .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StockStat("倉庫", material.quantity, Color(0xFFEBF0FA), Color(0xFF1351B4), Modifier.weight(1f))
                StockStat("引当済み", material.allocated_quantity, Color(0xFFFEF3C7), Color(0xFFB45309), Modifier.weight(1f))
                StockStat(
                    "有効在庫",
                    effective,
                    if (isShortage) Color(0xFFFEF2F2) else Color(0xFFECFDF5),
                    if (isShortage) Red500 else Color(0xFF059669),
                    Modifier.weight(1f),
                )
            }
            if (isShortage) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Red500, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("在庫不足", color = Red500, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { toggle(MaterialPanel.RESTOCK) }) { Text("入荷", fontWeight = FontWeight.Bold) }
                if (effective > 0) {
                    OutlinedButton(onClick = { toggle(MaterialPanel.ALLOCATE) }) { Text("引当", fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(onClick = { toggle(MaterialPanel.HISTORY) }) { Text("履歴", fontWeight = FontWeight.Bold) }
            }

            when (expanded) {
                MaterialPanel.RESTOCK -> {
                    Spacer(Modifier.height(12.dp))
                    RestockPanel(onConfirm = { qty, note -> onRestock(qty, note) { expanded = null } })
                }
                MaterialPanel.ALLOCATE -> {
                    Spacer(Modifier.height(12.dp))
                    AllocatePanel(
                        effectiveQty = effective,
                        allocations = material.allocations,
                        candidates = candidates,
                        loading = candidatesLoading,
                        onAllocate = { orderId, qty -> onAllocate(orderId, qty) {} },
                    )
                }
                MaterialPanel.HISTORY -> {
                    Spacer(Modifier.height(12.dp))
                    HistoryPanel(
                        allocations = material.allocations,
                        transactions = material.transactions,
                        onDeallocate = onDeallocate,
                        onDeleteTransaction = onDeleteTransaction,
                    )
                }
                null -> {}
            }
        }
    }
}

private val BorderStrokeRed = androidx.compose.foundation.BorderStroke(1.5.dp, Red500)

@Composable
private fun StockStat(label: String, value: Int, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF6B7280))
        Text("$value", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = fg)
    }
}

@Composable
private fun RestockPanel(onConfirm: (quantity: Int, note: String?) -> Unit) {
    var quantity by remember { mutableStateOf(1) }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFECFDF5))
            .padding(14.dp),
    ) {
        Text("入荷数量", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF065F46))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (quantity > 1) quantity-- }) {
                Icon(Icons.Filled.Remove, contentDescription = "減らす")
            }
            Text(
                "$quantity",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                modifier = Modifier.width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { quantity++ }) {
                Icon(Icons.Filled.Add, contentDescription = "増やす")
            }
            Text("個", color = Color(0xFF6B7280))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("備考（任意）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { onConfirm(quantity, note.takeIf { it.isNotBlank() }) }) {
            Text("入荷を確定", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AllocatePanel(
    effectiveQty: Int,
    allocations: List<MaterialAllocationDto>,
    candidates: List<MaterialCandidateDto>?,
    loading: Boolean,
    onAllocate: (orderId: Int, quantity: Int) -> Unit,
) {
    var confirmTarget by remember { mutableStateOf<MaterialCandidateDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFFBEB))
            .padding(14.dp),
    ) {
        Text(
            "引き当て候補受注（有効在庫 ${effectiveQty}個）",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF92400E),
        )
        Spacer(Modifier.height(10.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Amber500)
            }
            candidates == null -> {}
            candidates.isEmpty() -> Text("引き当て候補の受注がありません", color = Color(0xFF9CA3AF))
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate ->
                    val already = allocations.firstOrNull { it.order_id == candidate.order_id }
                    AllocateCandidateRow(
                        candidate = candidate,
                        alreadyAllocated = already,
                        maxQuantity = effectiveQty,
                        onAllocate = { qty -> confirmTarget = candidate.copy(order_quantity = qty) },
                    )
                }
            }
        }
    }

    confirmTarget?.let { candidate ->
        val willChangeStatus = candidate.status in MaterialStatuses
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text("受注 No.${candidate.order_id} へ引き当てますか？") },
            text = {
                Column {
                    Text("${candidate.order_quantity ?: 0}個 引き当てます。")
                    if (willChangeStatus) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "受注のステータスが「材料到着済み」に変わります。",
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAllocate(candidate.order_id, candidate.order_quantity ?: 1)
                    confirmTarget = null
                }) { Text("引き当てる") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun AllocateCandidateRow(
    candidate: MaterialCandidateDto,
    alreadyAllocated: MaterialAllocationDto?,
    maxQuantity: Int,
    onAllocate: (quantity: Int) -> Unit,
) {
    var quantity by remember(candidate.order_id) {
        mutableStateOf((candidate.order_quantity ?: 1).coerceAtMost(maxQuantity).coerceAtLeast(1))
    }
    val date = DateUtil.parse(candidate.delivery_date)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (alreadyAllocated != null) Color(0xFFF0FDF4) else Color.White)
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("No.${candidate.order_id}", fontWeight = FontWeight.Bold, color = Color(0xFF1351B4), fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                candidate.status?.let { OrderStatus.style(it).let { s -> Text(s.label, fontSize = 12.sp, color = s.text) } }
            }
            Text(candidate.part_name ?: "—", fontSize = 14.sp)
            Text(candidate.customer_name ?: "—", fontSize = 12.sp, color = Color(0xFF6B7280))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                candidate.order_quantity?.let { Text("注文 $it 個", fontSize = 12.sp, color = Color(0xFF6B7280)) }
                if (date != null) {
                    Text(
                        "${DateUtil.shortLabel(date)}　${DateUtil.deadlineNote(date)}",
                        fontSize = 12.sp,
                        color = if (DateUtil.daysUntil(date) < 0) Red500 else Color(0xFF6B7280),
                    )
                }
            }
        }
        if (alreadyAllocated != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF059669))
                Text("${alreadyAllocated.quantity}個", fontWeight = FontWeight.Bold, color = Color(0xFF059669), fontSize = 13.sp)
                Text("引当済み", fontSize = 11.sp, color = Color(0xFF059669))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (quantity > 1) quantity-- }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Remove, contentDescription = "減らす", modifier = Modifier.size(16.dp))
                    }
                    Text("$quantity", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(
                        onClick = { if (quantity < maxQuantity) quantity++ },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "増やす", modifier = Modifier.size(16.dp))
                    }
                }
                Button(onClick = { onAllocate(quantity) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))) {
                    Text("引き当て", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private data class HistoryRow(
    val isRestock: Boolean,
    val createdAt: String,
    val quantity: Int,
    val orderId: Int?,
    val note: String?,
    val createdBy: String?,
    val transactionId: Int?,
    val allocationId: Int?,
)

@Composable
private fun HistoryPanel(
    allocations: List<MaterialAllocationDto>,
    transactions: List<MaterialTransactionDto>,
    onDeallocate: (allocationId: Int) -> Unit,
    onDeleteTransaction: (transactionId: Int) -> Unit,
) {
    var confirmRow by remember { mutableStateOf<HistoryRow?>(null) }

    val rows = remember(allocations, transactions) {
        (transactions.map {
            HistoryRow(true, it.created_at, it.quantity, null, it.note, it.created_by, it.id, null)
        } + allocations.map {
            HistoryRow(false, it.created_at ?: "", it.quantity, it.order_id, it.note, it.created_by, null, it.id)
        }).sortedByDescending { it.createdAt }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEBF0FA))
            .padding(14.dp),
    ) {
        Text("入出庫履歴", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1351B4))
        Spacer(Modifier.height(10.dp))
        if (rows.isEmpty()) {
            Text("履歴はありません", color = Color(0xFF9CA3AF))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { row ->
                    HistoryRowView(row = row, onCancelClick = { confirmRow = row })
                }
            }
        }
    }

    confirmRow?.let { row ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRow = null },
            title = { Text(if (row.isRestock) "入荷の取り消し" else "引き当ての取り消し") },
            text = {
                Text(
                    if (row.isRestock) {
                        "入荷（${row.quantity}個）を取り消しますか？倉庫在庫数からこの分が差し引かれます。"
                    } else {
                        "引き当て（${row.quantity}個）を取り消しますか？"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (row.isRestock) row.transactionId?.let(onDeleteTransaction)
                        else row.allocationId?.let(onDeallocate)
                        confirmRow = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Red500),
                ) { Text("取り消す") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRow = null }) { Text("戻る") }
            },
        )
    }
}

@Composable
private fun HistoryRowView(row: HistoryRow, onCancelClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (row.isRestock) Color(0xFFD1FAE5) else Color(0xFFFEF3C7))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                if (row.isRestock) "入荷" else "引当",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (row.isRestock) Color(0xFF065F46) else Color(0xFF92400E),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${if (row.isRestock) "+" else "-"}${row.quantity}個",
                fontWeight = FontWeight.Bold,
                color = if (row.isRestock) Color(0xFF059669) else Color(0xFFD97706),
            )
            if (row.orderId != null) {
                Text("受注 No.${row.orderId}", fontSize = 12.sp, color = Color(0xFF6B7280))
            }
            row.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = Color(0xFF9CA3AF)) }
        }
        OutlinedButton(onClick = onCancelClick, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Red500)) {
            Text("取り消し", fontSize = 12.sp)
        }
    }
}
