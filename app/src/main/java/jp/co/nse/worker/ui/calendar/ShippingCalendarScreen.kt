package jp.co.nse.worker.ui.calendar

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import jp.co.nse.worker.data.ShippingBarcodeOrderDto
import jp.co.nse.worker.data.ShippingCalendarDayDto
import jp.co.nse.worker.data.ShippingCalendarMonthDto
import jp.co.nse.worker.data.ShippingCalendarOrderDto
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.DashboardButton
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.scan.startBarcodeScan
import jp.co.nse.worker.ui.theme.Gray500
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Indigo50
import jp.co.nse.worker.ui.theme.inkFor
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

// 4pt刻みの余白スケール。この画面内の余白・間隔はすべてここから選ぶ。
private val Space1 = 4.dp
private val Space2 = 8.dp
private val Space3 = 12.dp
private val Space4 = 16.dp
private val Space6 = 24.dp

private val Mono = FontFamily.Monospace
private val WeekdayLabels = listOf("日", "月", "火", "水", "木", "金", "土")

/** 出荷完了扱いとみなすステータス（Web版の already_shipped 判定と同一） */
private fun isShippedStatus(status: String?): Boolean = status == "shipped" || status == "billed"

/** 一括完了で変更した受注ID一覧。Undo表示・実行に使う */
private data class BulkUndoState(val orderIds: List<Int>, val message: String)

class ShippingCalendarViewModel(private val repo: ManagerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var month by mutableStateOf<ShippingCalendarMonthDto?>(null)
        private set
    var yearMonth by mutableStateOf(YearMonth.now())
        private set
    var holidayDates by mutableStateOf<Set<String>>(emptySet())
        private set
    var overrideDates by mutableStateOf<Set<String>>(emptySet())
        private set
    private val loadedFiscalYears = mutableSetOf<Int>()

    var scanLookingUp by mutableStateOf(false)
        private set
    var scanResult by mutableStateOf<ShippingBarcodeOrderDto?>(null)
        private set
    var shipping by mutableStateOf(false)
        private set
    var shipError by mutableStateOf<String?>(null)
        private set

    /** 出荷完了/取り消しの通信中の受注ID（一括・個別どちらも含む。二重タップ防止に使う） */
    var busyOrderIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    var bulkBusy by mutableStateOf(false)
        private set
    private var bulkUndo by mutableStateOf<BulkUndoState?>(null)
    val bulkUndoMessage: String? get() = bulkUndo?.message

    fun load(target: YearMonth = yearMonth) {
        yearMonth = target
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.shippingCalendar(target.year, target.monthValue)) {
                is ApiResult.Success -> month = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
            loadHolidaysIfNeeded(target)
        }
    }

    /** 表示中の月の休日マスタ（休日・例外稼働日）を取得する。土日の色分けに使う */
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

    fun ordersForDate(date: LocalDate): List<ShippingCalendarOrderDto> =
        month?.weeks?.flatten()
            ?.firstOrNull { DateUtil.parse(it.date) == date }
            ?.orders
            .orEmpty()

    private fun setOrderStatus(orderId: Int, newStatus: String) {
        val current = month ?: return
        month = current.copy(
            weeks = current.weeks.map { week ->
                week.map { day ->
                    if (day.orders.none { it.id == orderId }) {
                        day
                    } else {
                        day.copy(orders = day.orders.map { if (it.id == orderId) it.copy(status = newStatus) else it })
                    }
                }
            },
        )
    }

    /** バーコード値（発注番号/客先注文番号/受注ID）から受注を検索し、出荷完了確認モーダルの対象にする */
    fun lookupBarcode(code: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            scanLookingUp = true
            when (val result = repo.shippingBarcodeSearch(code)) {
                is ApiResult.Success -> {
                    shipError = null
                    scanResult = result.data
                }
                is ApiResult.Failure -> onError(result.message)
            }
            scanLookingUp = false
        }
    }

    fun dismissScanResult() {
        scanResult = null
        shipError = null
    }

    /** 出荷完了にする。出荷待ち以外・仮注文はサーバー側で拒否され [shipError] に反映される */
    fun confirmShip(onSuccess: () -> Unit) {
        val order = scanResult ?: return
        viewModelScope.launch {
            shipping = true
            shipError = null
            when (val result = repo.shipOrder(order.id)) {
                is ApiResult.Success -> {
                    scanResult = null
                    setOrderStatus(order.id, "shipped")
                    onSuccess()
                }
                is ApiResult.Failure -> shipError = result.message
            }
            shipping = false
        }
    }

    /** 明細行の丸トグルを単独で押した場合。完了⇄未完了を切り替える。一括Undoは対象外になるので消す */
    fun toggleOrder(order: ShippingCalendarOrderDto, onError: (String) -> Unit) {
        val orderId = order.id
        if (orderId in busyOrderIds) return
        bulkUndo = null
        val goingToComplete = !isShippedStatus(order.status)

        busyOrderIds = busyOrderIds + orderId
        viewModelScope.launch {
            val result = if (goingToComplete) repo.shipOrder(orderId) else repo.unshipOrder(orderId)
            when (result) {
                is ApiResult.Success -> setOrderStatus(orderId, if (goingToComplete) "shipped" else "shipping_wait")
                is ApiResult.Failure -> onError(result.message)
            }
            busyOrderIds = busyOrderIds - orderId
        }
    }

    /**
     * 選択日の未完了分をすべて完了にする。確認モーダルは出さず、即座に完了表示へ切り替える（楽観的更新）。
     * サーバー側で拒否された分だけ表示を戻し、成功した分だけがUndoの対象になる。
     */
    fun bulkCompleteDay(date: LocalDate, onError: (String) -> Unit) {
        if (bulkBusy) return
        val targets = ordersForDate(date).filter { !isShippedStatus(it.status) }
        if (targets.isEmpty()) return

        bulkUndo = null
        bulkBusy = true
        val targetIds = targets.map { it.id }.toSet()
        busyOrderIds = busyOrderIds + targetIds
        // 楽観的更新：結果を待たず先に完了表示にする
        targets.forEach { setOrderStatus(it.id, "shipped") }

        viewModelScope.launch {
            val succeeded = mutableListOf<Int>()
            for (order in targets) {
                when (val result = repo.shipOrder(order.id)) {
                    is ApiResult.Success -> succeeded += order.id
                    is ApiResult.Failure -> {
                        setOrderStatus(order.id, order.status ?: "shipping_wait")
                        onError("No.${order.id}：${result.message}")
                    }
                }
            }
            busyOrderIds = busyOrderIds - targetIds
            bulkBusy = false
            if (succeeded.isNotEmpty()) {
                bulkUndo = BulkUndoState(orderIds = succeeded, message = "${succeeded.size}件を完了にしました")
            }
        }
    }

    /** 一括完了の直後にだけ呼べる。変更した分だけ正確に未完了へ戻す */
    fun undoBulkComplete(onError: (String) -> Unit) {
        val undo = bulkUndo ?: return
        bulkUndo = null
        busyOrderIds = busyOrderIds + undo.orderIds
        viewModelScope.launch {
            for (orderId in undo.orderIds) {
                when (val result = repo.unshipOrder(orderId)) {
                    is ApiResult.Success -> setOrderStatus(orderId, "shipping_wait")
                    is ApiResult.Failure -> onError("No.$orderId：${result.message}")
                }
            }
            busyOrderIds = busyOrderIds - undo.orderIds.toSet()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingCalendarScreen(
    onOpenCheckSheet: (orderId: Int) -> Unit,
    onLogout: () -> Unit = {},
) {
    val feedback = rememberClickFeedback()
    val context = LocalContext.current
    val container = context.appContainer
    val vm: ShippingCalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { ShippingCalendarViewModel(container.managerRepository) } }
    )
    val userName = rememberCurrentUserName()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onError: (String) -> Unit = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } }

    LaunchedEffect(Unit) { vm.load() }

    fun goToMonth(target: YearMonth) {
        feedback()
        selectedDate = if (target == YearMonth.from(today)) today else target.atDay(1)
        vm.load(target)
    }

    fun onScan() {
        startBarcodeScan(
            context = context,
            onResult = { code -> vm.lookupBarcode(code = code, onError = onError) },
            onError = onError,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { feedback(); onScan() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("スキャンして出荷完了", fontWeight = FontWeight.Bold) },
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("出荷カレンダー") },
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
                .background(MaterialTheme.colorScheme.background),
        ) {
            val month = vm.month
            when {
                vm.loading && month == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null && month == null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(Space6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(Space3))
                    Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                }
                month != null -> ShippingCalendarContent(
                    month = month,
                    today = today,
                    selectedDate = selectedDate,
                    holidayDates = vm.holidayDates,
                    overrideDates = vm.overrideDates,
                    busyOrderIds = vm.busyOrderIds,
                    bulkBusy = vm.bulkBusy,
                    bulkUndoMessage = vm.bulkUndoMessage,
                    onSelectDate = { feedback(); selectedDate = it },
                    onPrevMonth = { goToMonth(vm.yearMonth.minusMonths(1)) },
                    onNextMonth = { goToMonth(vm.yearMonth.plusMonths(1)) },
                    onOpenCheckSheet = onOpenCheckSheet,
                    onToggleOrder = { order -> vm.toggleOrder(order, onError) },
                    onBulkComplete = { date -> vm.bulkCompleteDay(date, onError) },
                    onUndoBulk = { vm.undoBulkComplete(onError) },
                )
            }

            if (vm.scanLookingUp) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            vm.scanResult?.let { order ->
                ShipConfirmDialog(
                    order = order,
                    shipping = vm.shipping,
                    error = vm.shipError,
                    onConfirm = {
                        vm.confirmShip {
                            scope.launch { snackbarHost.showSnackbar("出荷完了にしました。") }
                        }
                    },
                    onDismiss = { feedback(); vm.dismissScanResult() },
                )
            }
        }
    }
}

@Composable
private fun ShippingCalendarContent(
    month: ShippingCalendarMonthDto,
    today: LocalDate,
    selectedDate: LocalDate,
    holidayDates: Set<String>,
    overrideDates: Set<String>,
    busyOrderIds: Set<Int>,
    bulkBusy: Boolean,
    bulkUndoMessage: String?,
    onSelectDate: (LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenCheckSheet: (Int) -> Unit,
    onToggleOrder: (ShippingCalendarOrderDto) -> Unit,
    onBulkComplete: (LocalDate) -> Unit,
    onUndoBulk: () -> Unit,
) {
    val selectedDayOrders: List<ShippingCalendarOrderDto> = remember(month, selectedDate) {
        month.weeks.flatten()
            .firstOrNull { DateUtil.parse(it.date) == selectedDate }
            ?.orders
            .orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 736.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .padding(Space4),
        ) {
            MonthHeader(label = "${month.year}年${month.month}月", onPrevMonth = onPrevMonth, onNextMonth = onNextMonth)
            Spacer(Modifier.height(Space4))
            WeekdayHeaderRow()
            Spacer(Modifier.height(Space1))
            Column(verticalArrangement = Arrangement.spacedBy(Space1)) {
                month.weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space1),
                    ) {
                        week.forEach { day ->
                            CalendarDayCell(
                                modifier = Modifier.weight(1f),
                                day = day,
                                isToday = day.inMonth && DateUtil.parse(day.date) == today,
                                isSelected = day.inMonth && DateUtil.parse(day.date) == selectedDate,
                                holidayDates = holidayDates,
                                overrideDates = overrideDates,
                                onClick = { DateUtil.parse(day.date)?.let(onSelectDate) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space6))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(Space4))

            SelectedDayList(
                date = selectedDate,
                orders = selectedDayOrders,
                busyOrderIds = busyOrderIds,
                bulkBusy = bulkBusy,
                bulkUndoMessage = bulkUndoMessage,
                onOpenCheckSheet = onOpenCheckSheet,
                onToggleOrder = onToggleOrder,
                onBulkComplete = { onBulkComplete(selectedDate) },
                onUndoBulk = onUndoBulk,
            )
            Spacer(Modifier.height(Space6))
        }
    }
}

@Composable
private fun MonthHeader(label: String, onPrevMonth: () -> Unit, onNextMonth: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = Mono,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space2)) {
            NavRoundButton(icon = Icons.Filled.ChevronLeft, contentDescription = "前月", onClick = onPrevMonth)
            NavRoundButton(icon = Icons.Filled.ChevronRight, contentDescription = "翌月", onClick = onNextMonth)
        }
    }
}

@Composable
private fun NavRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Indigo50)
            .clickable(onClick = onClick)
            .padding(Space2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space1)) {
        WeekdayLabels.forEachIndexed { index, label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (index) {
                        0 -> Color(0xFFDC2626).copy(alpha = 0.75f)
                        6 -> Color(0xFF2563EB).copy(alpha = 0.75f)
                        else -> Gray500
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    day: ShippingCalendarDayDto,
    isToday: Boolean,
    isSelected: Boolean,
    holidayDates: Set<String>,
    overrideDates: Set<String>,
    onClick: () -> Unit,
) {
    if (!day.inMonth) {
        // 月外の日は罫線なしの空セルで埋め、グリッドの列位置だけ保つ
        Box(modifier = modifier.aspectRatio(1f))
        return
    }

    val date = DateUtil.parse(day.date)
    val orderCount = day.orders.size

    // 休日マスタ（休日・例外稼働日）を参照し、土日でも実際に稼働日なら色を付けない
    val dayOfWeek = date?.dayOfWeek
    val isNonWorkingDay = date != null && !DateUtil.isWorkingDay(date, holidayDates, overrideDates)
    val isWeekend = dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY
    val isNonWorkingWeekend = isNonWorkingDay && isWeekend
    val isNonWorkingSunday = isNonWorkingWeekend && dayOfWeek == java.time.DayOfWeek.SUNDAY
    // 土日は色分けだけで休みだと分かるため、スタンプは平日の休日にだけ表示する
    val isWeekdayHoliday = isNonWorkingDay && !isWeekend

    val background = when {
        isToday -> MaterialTheme.colorScheme.primary
        isSelected -> Indigo50
        isNonWorkingWeekend -> if (isNonWorkingSunday) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
        else -> Color.White
    }
    val dateColor = when {
        isToday -> inkFor(MaterialTheme.colorScheme.primary)
        isNonWorkingWeekend -> if (isNonWorkingSunday) Color(0xFFDC2626) else Color(0xFF2563EB)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        isToday -> null
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFE5E7EB)
    }
    val borderWidth = if (isSelected && !isToday) 2.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Space2))
            .background(background)
            .then(
                if (borderColor != null) {
                    Modifier.border(borderWidth, borderColor, RoundedCornerShape(Space2))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            "${date?.dayOfMonth ?: ""}",
            fontFamily = Mono,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = dateColor,
            modifier = Modifier.align(Alignment.TopStart).padding(top = Space1, start = Space1),
        )
        when {
            orderCount > 0 -> CountBadge(
                count = orderCount,
                onFilledCell = isToday,
                modifier = Modifier.align(Alignment.Center),
            )
            isWeekdayHoliday -> HolidayStamp(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** 工場休業日（休日マスタ参照）のスタンプ。土日は色分けだけで判別できるため平日の休日にだけ使う */
@Composable
private fun HolidayStamp(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.EventBusy,
        contentDescription = "休業日",
        tint = Color(0xFFDC2626),
        modifier = modifier.size(22.dp),
    )
}

@Composable
private fun CountBadge(count: Int, onFilledCell: Boolean, modifier: Modifier = Modifier) {
    val bg = if (onFilledCell) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary
    val fg = inkFor(MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = Space2, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            fontFamily = Mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun SelectedDayList(
    date: LocalDate,
    orders: List<ShippingCalendarOrderDto>,
    busyOrderIds: Set<Int>,
    bulkBusy: Boolean,
    bulkUndoMessage: String?,
    onOpenCheckSheet: (Int) -> Unit,
    onToggleOrder: (ShippingCalendarOrderDto) -> Unit,
    onBulkComplete: () -> Unit,
    onUndoBulk: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val wd = WeekdayLabels[date.dayOfWeek.value % 7]
    val completedCount = orders.count { isShippedStatus(it.status) }
    val totalCount = orders.size
    val allCompleted = totalCount > 0 && completedCount == totalCount

    Text(
        "${date.monthValue}月${date.dayOfMonth}日（$wd）の出荷",
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (totalCount > 0) {
        Spacer(Modifier.height(Space1))
        Text(
            "$completedCount / ${totalCount}件 完了",
            fontFamily = Mono,
            fontSize = 14.sp,
            fontWeight = if (allCompleted) FontWeight.Bold else FontWeight.SemiBold,
            color = if (allCompleted) Green600 else Gray500,
        )
    }
    Spacer(Modifier.height(Space3))

    if (orders.isEmpty()) {
        Text("この日の出荷予定はありません", fontSize = 14.sp, color = Gray500)
        return
    }

    // 一括操作の直後は、たとえ一部が失敗して未完了が残っていてもUndoを優先して見せる
    if (bulkUndoMessage != null) {
        UndoBar(message = bulkUndoMessage, onUndo = { feedback(); onUndoBulk() })
        Spacer(Modifier.height(Space3))
    }
    if (!allCompleted) {
        Button(
            onClick = { feedback(); onBulkComplete() },
            enabled = !bulkBusy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(Space2),
        ) {
            if (bulkBusy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("本日の出荷をすべて完了にする", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(Space3))
    }

    Column {
        orders.forEachIndexed { index, order ->
            ShippingOrderRow(
                order = order,
                busy = order.id in busyOrderIds,
                onClick = { onOpenCheckSheet(order.id) },
                onToggle = { onToggleOrder(order) },
            )
            if (index != orders.lastIndex) {
                HorizontalDivider(color = Color(0xFFF3F4F6))
            }
        }
    }
}

@Composable
private fun UndoBar(message: String, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space2))
            .background(Color(0xFFF3F4F6))
            .padding(horizontal = Space4, vertical = Space3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onUndo) {
            Text("元に戻す", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ShippingOrderRow(
    order: ShippingCalendarOrderDto,
    busy: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val completed = isShippedStatus(order.status)
    val dim = if (completed) 0.42f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompletionToggle(
            completed = completed,
            busy = busy,
            onClick = { if (!busy) { feedback(); onToggle() } },
        )
        Spacer(Modifier.width(Space3))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { feedback(); onClick() }
                .padding(vertical = Space1),
        ) {
            Text(
                order.customer_name ?: "客先未設定",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
            )
            Spacer(Modifier.height(Space1 / 2))
            Text(
                order.part_name ?: "—",
                fontSize = 13.sp,
                color = Gray500.copy(alpha = dim),
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space3))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                order.quantity?.let { "$it 個" } ?: "—",
                fontFamily = Mono,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
            )
            Spacer(Modifier.height(Space1 / 2))
            Text(
                if (completed) "完了" else "未出荷",
                fontFamily = Mono,
                fontSize = 11.sp,
                color = Gray500.copy(alpha = dim),
            )
        }
    }
}

@Composable
private fun CompletionToggle(completed: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else if (completed) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Green600),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "完了", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFD1D5DB), CircleShape),
            )
        }
    }
}

/**
 * バーコードスキャン結果の出荷完了確認モーダル。
 * サーバー側（[ManagerRepository.shipOrder] → Api\OrderController::ship）が最終判断だが、
 * Web版の出荷カレンダーと同じく「出荷待ち以外」「出荷済み」をここで先に案内し、誤操作を防ぐ。
 */
@Composable
private fun ShipConfirmDialog(
    order: ShippingBarcodeOrderDto,
    shipping: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blocked = order.already_shipped || !order.is_shipping_wait

    AlertDialog(
        onDismissRequest = { if (!shipping) onDismiss() },
        title = { Text("出荷完了確認", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) },
        text = {
            Column {
                Text("以下の受注を出荷完了にしますか？", fontSize = 14.sp, color = Gray500)
                Spacer(Modifier.height(Space3))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Space2))
                        .background(Color(0xFFF9FAFB))
                        .padding(Space4),
                    verticalArrangement = Arrangement.spacedBy(Space2),
                ) {
                    ShipInfoRow("No.", "${order.id}")
                    ShipInfoRow("客先名", order.customer_name ?: "—")
                    ShipInfoRow("発注番号", order.po_number ?: "—")
                    ShipInfoRow("品名", order.part_name ?: "—")
                    ShipInfoRow("数量", order.quantity?.let { "$it 個" } ?: "—")
                    ShipInfoRow("納期", order.delivery_date ?: "—")
                }

                if (order.already_shipped) {
                    Spacer(Modifier.height(Space3))
                    Text(
                        "この受注はすでに出荷完了済みです",
                        color = Color(0xFF92400E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (!order.is_shipping_wait) {
                    Spacer(Modifier.height(Space3))
                    Text(
                        "ステータスが「出荷待ち」ではありません（現在：${order.status_label ?: order.status ?: "—"}）",
                        color = Color(0xFFB91C1C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                error?.let {
                    Spacer(Modifier.height(Space3))
                    Text(it, color = Color(0xFFB91C1C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            if (shipping) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                Button(
                    onClick = onConfirm,
                    enabled = !blocked,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("出荷完了にする", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !shipping) {
                Text("キャンセル", color = Gray500)
            }
        },
    )
}

@Composable
private fun ShipInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
