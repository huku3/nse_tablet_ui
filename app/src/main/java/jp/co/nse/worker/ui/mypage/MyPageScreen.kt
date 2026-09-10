package jp.co.nse.worker.ui.mypage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.HistoryItemDto
import jp.co.nse.worker.ui.components.DashboardButton
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.ScrollToTopFab
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.history.DayStat
import jp.co.nse.worker.ui.history.DaySummaryCard
import jp.co.nse.worker.ui.history.DayTabRow
import jp.co.nse.worker.ui.history.HistoryCard
import jp.co.nse.worker.ui.history.HistoryViewModel
import jp.co.nse.worker.ui.history.recentTabDates
import jp.co.nse.worker.ui.theme.AccentPreset
import jp.co.nse.worker.ui.theme.AccentPresets
import jp.co.nse.worker.ui.theme.DefaultAccentHex
import jp.co.nse.worker.ui.theme.inkFor
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * マイページ：ログイン中アカウントのメイン色の変更、および作業実績の表示。
 * メイン色はサーバー側 users.color に保存され、次回以降のログインでも引き継がれる。
 * タブで「メイン色」「作業実績」を切り替えて、どちらか一方のみ表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onBack: () -> Unit,
    onOpenTask: (processId: Int) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    val accentHex by container.settings.accentColorFlow.collectAsState(initial = DefaultAccentHex)
    var colorSaving by remember { mutableStateOf(false) }
    var colorError by remember { mutableStateOf<String?>(null) }

    val historyVm: HistoryViewModel = viewModel(
        factory = viewModelFactory { initializer { HistoryViewModel(container.workerRepository) } },
    )
    LaunchedEffect(Unit) { historyVm.load() }

    fun pickColor(hex: String) {
        if (colorSaving || hex.equals(accentHex, ignoreCase = true)) return
        feedback()
        scope.launch {
            colorSaving = true
            colorError = null
            when (val result = container.authRepository.updateMyColor(hex)) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> colorError = result.message
            }
            colorSaving = false
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("マイページ") },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    DashboardButton()
                    ProcessAssignmentButton()
                    IconButton(onClick = { feedback(); historyVm.load() }) {
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            MyPageContent(
                listState = listState,
                accentHex = accentHex,
                colorSaving = colorSaving,
                colorError = colorError,
                userName = userName,
                onPickColor = ::pickColor,
                historyVm = historyVm,
                onOpenTask = onOpenTask,
            )
            ScrollToTopFab(
                visible = listState.firstVisibleItemIndex > 0,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }
}

@Composable
private fun MyPageContent(
    listState: androidx.compose.foundation.lazy.LazyListState,
    accentHex: String,
    colorSaving: Boolean,
    colorError: String?,
    userName: String,
    onPickColor: (String) -> Unit,
    historyVm: HistoryViewModel,
    onOpenTask: (Int) -> Unit,
) {
    var selectedSection by remember { mutableStateOf(1) }
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val effectiveSelected = selectedDate ?: today

    val tabDates = remember(historyVm.holidayDates, historyVm.overrideDates) {
        recentTabDates(historyVm.holidayDates, historyVm.overrideDates)
    }
    val statsByDate: Map<LocalDate, DayStat> = remember(historyVm.items) {
        historyVm.items
            .mapNotNull { item -> DateUtil.localDateFromIso(item.completed_at)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> DayStat(count = list.size, minutes = list.sumOf { it.work_minutes ?: 0 }) }
    }
    val itemsByDate: Map<LocalDate, List<HistoryItemDto>> = remember(historyVm.items) {
        historyVm.items
            .mapNotNull { item -> DateUtil.localDateFromIso(item.completed_at)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
    }
    val selectedIndex = tabDates.indexOf(effectiveSelected).coerceAtLeast(0)
    val previousDate = tabDates.getOrNull(selectedIndex + 1)
    val selectedStat = statsByDate[effectiveSelected] ?: DayStat(0, 0)
    val previousStat = previousDate?.let { statsByDate[it] ?: DayStat(0, 0) }
    val selectedItems = itemsByDate[effectiveSelected].orEmpty().sortedByDescending { it.completed_at }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "tabs") {
            TabRow(selectedTabIndex = selectedSection, containerColor = Color.Transparent) {
                Tab(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    text = { Text("メイン色", fontWeight = FontWeight.Bold) },
                )
                Tab(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    text = { Text("作業実績", fontWeight = FontWeight.Bold) },
                )
            }
        }

        if (selectedSection == 0) {
            item(key = "accent") {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        "${userName}さんのアカウントのメイン色です。ヘッダーやボタンの色に使われ、次回ログイン時も引き継がれます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AccentPresets.forEach { preset ->
                            ColorSwatch(
                                preset = preset,
                                selected = preset.hex.equals(accentHex, ignoreCase = true),
                                saving = colorSaving,
                                onClick = { onPickColor(preset.hex) },
                            )
                        }
                    }
                    colorError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        } else {
            when {
                historyVm.loading && historyVm.items.isEmpty() -> item(key = "history-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                historyVm.error != null && historyVm.items.isEmpty() -> item(key = "history-error") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(historyVm.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { historyVm.load() }) { Text("再読み込み") }
                    }
                }
                else -> {
                    item(key = "day-tabs") {
                        DayTabRow(
                            dates = tabDates,
                            today = today,
                            selectedDate = effectiveSelected,
                            onSelect = { selectedDate = it },
                        )
                    }
                    item(key = "summary") {
                        DaySummaryCard(
                            date = effectiveSelected,
                            today = today,
                            stat = selectedStat,
                            previousStat = previousStat,
                        )
                    }
                    if (selectedItems.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("この日の完了実績はありません", color = Color(0xFF9CA3AF), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        items(selectedItems, key = { "history-${it.id}" }) { item ->
                            HistoryCard(item = item, onClick = { onOpenTask(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(preset: AccentPreset, selected: Boolean, saving: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(preset.color)
                .then(
                    if (selected) Modifier.border(3.dp, Color(0xFF111827), CircleShape) else Modifier,
                )
                .clickable(enabled = !saving, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "選択中", tint = inkFor(preset.color))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(preset.label, fontSize = 12.sp, color = Color(0xFF6B7280))
    }
}
