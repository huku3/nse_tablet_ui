package jp.co.nse.worker.ui.tasklist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import jp.co.nse.worker.data.CompletedTaskDto
import jp.co.nse.worker.data.OrderBriefDto
import jp.co.nse.worker.data.OrderType
import jp.co.nse.worker.data.ProcessBriefDto
import jp.co.nse.worker.data.TaskItemDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.OrderStatusBadge
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.theme.Emerald500
import jp.co.nse.worker.ui.theme.Green600
import jp.co.nse.worker.ui.theme.Orange400
import jp.co.nse.worker.ui.theme.Indigo700
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaskListViewModel(private val repo: WorkerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var tasks by mutableStateOf<List<TaskItemDto>>(emptyList())
        private set
    var completed by mutableStateOf<List<CompletedTaskDto>>(emptyList())
        private set
    var lookingUp by mutableStateOf(false)
        private set

    /** バーコード値から担当工程を特定し、見つかれば [onFound] にprocess_idを渡す */
    fun lookupBarcode(code: String, onFound: (Int) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            lookingUp = true
            when (val result = repo.findByBarcode(code)) {
                is ApiResult.Success -> onFound(result.data.process_id)
                is ApiResult.Failure -> onError(result.message)
            }
            lookingUp = false
        }
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.tasks()) {
                is ApiResult.Success -> {
                    tasks = result.data.active
                    completed = result.data.completed_today
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onOpenTask: (processId: Int) -> Unit,
    onLogout: () -> Unit,
    completedProcessName: String? = null,
    onCompletedMessageShown: () -> Unit = {},
) {
    val feedback = rememberClickFeedback()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val vm: TaskListViewModel = viewModel(
        factory = viewModelFactory { initializer { TaskListViewModel(container.workerRepository) } }
    )
    // アプリのバージョン（端末で動いているビルドを判別できるよう表示）
    val versionLabel = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toLong()
            }
            "v${pi.versionName} ($code)"
        }.getOrDefault("")
    }
    var userName by remember { mutableStateOf("") }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    fun onScan() {
        jp.co.nse.worker.ui.scan.startBarcodeScan(
            context = context,
            onResult = { code ->
                vm.lookupBarcode(
                    code = code,
                    onFound = { processId -> onOpenTask(processId) },
                    onError = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } },
                )
            },
            onError = { msg -> scope.launch { snackbarHost.showSnackbar(msg) } },
        )
    }

    LaunchedEffect(Unit) {
        userName = container.settings.userNameFlow.firstOrNull().orEmpty()
    }

    // 工程完了メッセージは表示から5秒後に自動で消す
    LaunchedEffect(completedProcessName) {
        if (completedProcessName != null) {
            kotlinx.coroutines.delay(5000)
            onCompletedMessageShown()
        }
    }

    // 画面復帰時（作業詳細から戻った時など）に一覧を再読み込みする
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { feedback(); onScan() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null) },
                text = { Text("スキャン", fontWeight = FontWeight.Bold) },
            )
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("作業一覧", fontWeight = FontWeight.Bold)
                        Text(
                            buildString {
                                append(DateUtil.shortLabel(LocalDate.now()))
                                append("　")
                                append(versionLabel)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                },
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
                vm.loading && vm.tasks.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                vm.error != null && vm.tasks.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                    }
                }
                vm.tasks.isEmpty() && vm.completed.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("担当中の工程はありません", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "管理者から作業が割り当てられると、ここに表示されます。",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                else -> {
                    TaskList(
                        tasks = vm.tasks,
                        completed = vm.completed,
                        currentUserName = userName,
                        onOpenTask = onOpenTask,
                    )
                }
            }

            if (vm.lookingUp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("照合中…", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            CompletionBanner(
                processName = completedProcessName,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/** 工程完了直後に一覧画面上部へ表示するお祝いバナー。表示から5秒で自動的に消える */
@Composable
private fun CompletionBanner(processName: String?, modifier: Modifier = Modifier) {
    androidx.compose.animation.AnimatedVisibility(
        visible = processName != null,
        enter = androidx.compose.animation.slideInVertically(
            initialOffsetY = { -it },
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
            ),
        ) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) +
            androidx.compose.animation.fadeOut(),
        modifier = modifier.padding(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Green600),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "「${processName.orEmpty()}」の工程が完了しました！",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    )
                    Text("お疲れ様でした。", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
    }
}

/** 同じ受注（order.id）に属する担当工程をまとめたグループ */
private data class OrderTaskGroup(
    val orderId: Int,
    val order: OrderBriefDto,
    val tasks: List<TaskItemDto>,
)

@Composable
private fun TaskList(
    tasks: List<TaskItemDto>,
    completed: List<CompletedTaskDto>,
    currentUserName: String,
    onOpenTask: (Int) -> Unit,
) {
    // 工程の進行状況（taskProgress）はソート内の比較やカード側でも参照するため、
    // データが変わった時だけ1回計算してマップにしておく（スクロールの再コンポーズの
    // たびに重い集計を繰り返すとGCが増えてカクつきの原因になるため）
    val progressByTaskId = remember(tasks) { tasks.associate { it.id to taskProgress(it) } }

    // 納期日でグループ化（null末尾）し、日付順に並べる。こちらもデータが変わった時だけ計算する
    val groupedByDate = remember(tasks, progressByTaskId) {
        val grouped = tasks.groupBy { it.order.delivery_date }
        grouped.keys.sortedWith(nullsLast(naturalOrder())).map { key ->
            // 同じ受注（order.id）の担当工程を1つのグループにまとめる
            val orderGroups = grouped.getValue(key)
                .groupBy { it.order.id }
                .map { (orderId, items) ->
                    OrderTaskGroup(
                        orderId = orderId,
                        order = items.first().order,
                        tasks = items.sortedBy { it.sort_order },
                    )
                }
                // 同じ納期内では「今すぐ開始できる」工程を含む受注を先頭に
                .sortedWith(
                    compareByDescending<OrderTaskGroup> { group ->
                        group.tasks.any { progressByTaskId[it.id]?.canStartNow == true }
                    }.thenBy { group -> group.tasks.minOf { it.sort_order } }
                )
            key to orderGroups
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groupedByDate.forEach { (key, orderGroups) ->
            item(key = "header-${key ?: "none"}") {
                DateGroupHeader(dateKey = key, count = orderGroups.sumOf { it.tasks.size })
            }
            items(orderGroups, key = { it.orderId }) { group ->
                OrderTaskGroupCard(
                    group = group,
                    currentUserName = currentUserName,
                    progressByTaskId = progressByTaskId,
                    onOpenTask = onOpenTask,
                )
            }
        }

        // 完了した作業（実績）
        if (completed.isNotEmpty()) {
            item(key = "completed-header") {
                CompletedSectionHeader(count = completed.size)
            }
            items(completed, key = { "done-${it.id}" }) { task ->
                CompletedCard(task = task, onClick = { onOpenTask(task.id) })
            }
        }
    }
    ScrollToTopFab(
        visible = listState.firstVisibleItemIndex > 0,
        onClick = { scope.launch { listState.animateScrollToItem(0) } },
        modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
    )
    }
}

@Composable
private fun CompletedSectionHeader(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Emerald500))
        Spacer(Modifier.width(8.dp))
        Text("完了した作業", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text("${count}件", color = Color(0xFF9CA3AF), fontSize = 12.sp)
    }
}

@Composable
private fun CompletedCard(task: CompletedTaskDto, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    Card(
        onClick = { feedback(); onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.order.part_name ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF374151),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.process_name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF6B7280))
                    DateUtil.dateTimeLabel(task.completed_at)?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("$it 完了", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    }
                }
                DateUtil.durationLabel(task.work_minutes)?.let {
                    Spacer(Modifier.height(2.dp))
                    Text("作業時間 $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            StatusChip(status = WorkStatus.COMPLETED, color = Emerald500)
        }
    }
}

@Composable
private fun DateGroupHeader(dateKey: String?, count: Int) {
    val date = DateUtil.parse(dateKey)
    val (label, dotColor) = if (date == null) {
        "納期未設定" to Color(0xFF9CA3AF)
    } else {
        val diff = DateUtil.daysUntil(date)
        val color = when {
            diff < 0 -> Red500
            diff == 0L -> Red500
            diff <= 3 -> Color(0xFFF59E0B)
            else -> Green600
        }
        "${DateUtil.shortLabel(date)}　${DateUtil.deadlineNote(date)}" to color
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        Text("${count}件", color = Color(0xFF9CA3AF), fontSize = 12.sp)
    }
}

/** 待機タスクの進行状況（all_processes から算出） */
private data class TaskProgress(
    val canStartNow: Boolean,        // 待機中 かつ 前工程がすべて完了 → 今すぐ開始できる
    val current: ProcessBriefDto?,   // 工程全体の最前線（最初の未完了工程）
    val completedCount: Int,
    val total: Int,
)

private fun taskProgress(task: TaskItemDto): TaskProgress {
    val sorted = task.all_processes.sortedBy { it.sort_order }
    val isWaiting = task.status == WorkStatus.WAITING
    val priorDone = sorted
        .filter { it.sort_order < task.sort_order }
        .all { it.status == WorkStatus.COMPLETED }
    return TaskProgress(
        canStartNow = isWaiting && priorDone,
        current = sorted.firstOrNull { it.status != WorkStatus.COMPLETED },
        completedCount = sorted.count { it.status == WorkStatus.COMPLETED },
        total = sorted.size,
    )
}

private val AmberDark = Color(0xFFD97706)

/** 同じ受注の担当工程をまとめて1枚のカードに表示する（工程が複数あれば区切り線で並べる） */
@Composable
private fun OrderTaskGroupCard(
    group: OrderTaskGroup,
    currentUserName: String,
    progressByTaskId: Map<Int, TaskProgress>,
    onOpenTask: (Int) -> Unit,
) {
    val order = group.order
    val anyCanStartNow = group.tasks.any { progressByTaskId[it.id]?.canStartNow == true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (anyCanStartNow) Color(0xFFECFDF5) else Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = order.part_name ?: "—",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                order.order_type?.let {
                    Spacer(Modifier.width(10.dp))
                    OrderTypeBadge(it)
                }
            }
            order.status?.let {
                Spacer(Modifier.height(8.dp))
                OrderStatusBadge(it)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                InfoLabel("受注No", "${order.id}")
                order.po_number?.let { InfoLabel("発注番号", it) }
                order.quantity?.let { InfoLabel("注文数", "$it 個") }
            }
            Spacer(Modifier.height(12.dp))

            // 表示を軽くするため、詳しく表示するのは今作業できる／進んでいる現工程1件のみ。
            // group.tasks は sort_order 昇順なので、先頭が自分の担当工程の中で最も手前（現工程）になる。
            // 同じ受注の他の担当工程は工程名だけの軽いラベルで示す。
            val primaryTask = group.tasks.first()
            val otherTasks = group.tasks.drop(1)
            // 前工程待ちをタップした際の誘導先は「自分の担当工程の中で」今すぐ着手できるものに限る。
            // 受注全体の最前列工程（担当者に関係なく）へ誘導すると、他人の担当工程を開いて
            // 作業開始できてしまうため、必ず group.tasks（自分の担当分）の中から選ぶ。
            val myStartableTaskId = group.tasks.firstOrNull { progressByTaskId[it.id]?.canStartNow == true }?.id

            TaskProcessRow(
                task = primaryTask,
                currentUserName = currentUserName,
                progress = progressByTaskId.getValue(primaryTask.id),
                redirectTaskId = myStartableTaskId,
                onOpenTask = onOpenTask,
            )

            if (otherTasks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "他の担当工程",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9CA3AF),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    otherTasks.forEach { task ->
                        OtherProcessChip(task = task, onClick = { onOpenTask(task.id) })
                    }
                }
            }
        }
    }
}

/** 現工程以外の担当工程を軽量に示すラベル。ステータスに応じて色分けする */
@Composable
private fun OtherProcessChip(task: TaskItemDto, onClick: () -> Unit) {
    val feedback = rememberClickFeedback()
    val color = statusColor(task.status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .clickable { feedback(); onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            task.process_name,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 受注カード内の1工程分の行。タップでその工程の作業詳細へ遷移する */
@Composable
private fun TaskProcessRow(
    task: TaskItemDto,
    currentUserName: String,
    progress: TaskProgress,
    redirectTaskId: Int?,
    onOpenTask: (Int) -> Unit,
) {
    val feedback = rememberClickFeedback()
    val statusColor = statusColor(task.status)
    val prog = progress
    val isWaiting = task.status == WorkStatus.WAITING
    // 前工程待ちで自分ではまだ着手できない工程をタップした場合は、自分の担当工程の中で
    // 今すぐ着手できるものがあればそちらへ誘導する（他人の担当工程には絶対に誘導しない）
    val targetProcessId = if (isWaiting && !prog.canStartNow) redirectTaskId ?: task.id else task.id

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { feedback(); onOpenTask(targetProcessId) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(8.dp))
                Text(task.process_name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            when {
                isWaiting && prog.canStartNow -> Pill("開始できます", Green600, icon = Icons.Filled.Check)
                isWaiting -> Pill("前工程待ち", AmberDark)
                else -> StatusChip(status = task.status, color = statusColor)
            }
        }

        if (task.total_count > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${task.completed_count} / ${task.total_count} 個 完了",
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
            )
        }

        // 加工工程パイプライン（工程名・担当者・工程納期）は割り当て画面と同様に常時表示する
        if (task.all_processes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // 待機タスクのみ、現在どの工程まで進んでいるかの説明文を追加表示
            if (isWaiting) {
                if (prog.canStartNow) {
                    Text(
                        "前工程はすべて完了しています。タップして開始できます。",
                        color = Green600,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (prog.current != null) {
                    Text(
                        buildString {
                            append("現在「${prog.current.process_name}」工程")
                            prog.current.worker?.takeIf { it.isNotBlank() }?.let { append("（担当: $it）") }
                            append(" ・ ${prog.completedCount}/${prog.total} 工程完了")
                        },
                        color = AmberDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            MiniPipeline(task.all_processes, currentId = task.id, currentUserName = currentUserName)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** 工程の進行状況をコンパクトに表示。完了=✓、自分の工程は緑リングで強調 */
@Composable
private fun MiniPipeline(processes: List<ProcessBriefDto>, currentId: Int, currentUserName: String) {
    val sorted = processes.sortedBy { it.sort_order }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        sorted.forEachIndexed { index, p ->
            val isMine = p.id == currentId
            val isSelfWorker = currentUserName.isNotBlank() &&
                p.worker?.takeIf { it.isNotBlank() } == currentUserName
            val done = p.status == WorkStatus.COMPLETED
            val active = p.status == WorkStatus.IN_PROGRESS ||
                p.status == WorkStatus.PAUSED ||
                p.status == WorkStatus.BROKEN
            val dotColor = when {
                done -> Emerald500
                active -> Color(0xFF3B82F6)
                else -> Color(0xFFD1D5DB)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .then(if (isMine) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (dotColor == Color(0xFFD1D5DB)) Color(0xFF6B7280) else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    p.process_name,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = if (isMine) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                    fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    p.worker?.takeIf { it.isNotBlank() } ?: "未割当",
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = if (isSelfWorker) MaterialTheme.colorScheme.primary else Color(0xFFB0B7C0),
                    fontWeight = if (isSelfWorker) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
                DateUtil.monthDayLabel(p.process_deadline)?.let {
                    Text(
                        it,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        color = Color(0xFFB0B7C0),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** 受注区分バッジ。初品は特に注意して作業すべきため赤で目立たせる */
@Composable
private fun OrderTypeBadge(orderType: String) {
    val isInitial = OrderType.isInitial(orderType)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isInitial) Red500 else Color(0xFFEEF2FF))
            .then(
                if (isInitial) {
                    Modifier.border(2.dp, Color(0xFFB91C1C), RoundedCornerShape(50))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            OrderType.label(orderType),
            color = if (isInitial) Color.White else Indigo700,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun StatusChip(status: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(WorkStatus.label(status), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

fun statusColor(status: String): Color = when (status) {
    WorkStatus.IN_PROGRESS -> Color(0xFF3B82F6)
    WorkStatus.PAUSED -> Orange400
    WorkStatus.BROKEN -> Red500
    WorkStatus.COMPLETED -> Emerald500
    else -> Color(0xFF6B7280)
}
