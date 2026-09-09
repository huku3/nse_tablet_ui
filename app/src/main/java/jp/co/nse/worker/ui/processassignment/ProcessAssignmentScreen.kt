package jp.co.nse.worker.ui.processassignment

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import jp.co.nse.worker.data.ProcessMasterLiteDto
import jp.co.nse.worker.data.WorkerDto
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Amber500
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch

/** 1人の作業者・1工程についての割り当て状態 */
data class AssignmentState(val assigned: Boolean, val isDefault: Boolean)

private val UnassignedState = AssignmentState(assigned = false, isDefault = false)

class ProcessAssignmentViewModel(private val repo: ManagerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var workers by mutableStateOf<List<WorkerDto>>(emptyList())
        private set
    var processMasters by mutableStateOf<List<ProcessMasterLiteDto>>(emptyList())
        private set
    var selectedMasterId by mutableStateOf<Int?>(null)
        private set
    var isEditMode by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    private var originalState by mutableStateOf<Map<Pair<Int, Int>, AssignmentState>>(emptyMap())
    private var workingState by mutableStateOf<Map<Pair<Int, Int>, AssignmentState>>(emptyMap())

    val pendingCount: Int
        get() {
            val keys = originalState.keys + workingState.keys
            return keys.count { key -> stateOf(originalState, key) != stateOf(workingState, key) }
        }

    private fun stateOf(map: Map<Pair<Int, Int>, AssignmentState>, key: Pair<Int, Int>): AssignmentState =
        map[key] ?: UnassignedState

    fun stateFor(userId: Int, masterId: Int): AssignmentState = stateOf(workingState, userId to masterId)

    fun assignedCountFor(masterId: Int): Int = workers.count { stateFor(it.id, masterId).assigned }

    fun clearMessage() {
        message = null
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.processAssignments()) {
                is ApiResult.Success -> {
                    workers = result.data.workers
                    processMasters = result.data.process_masters
                    val map = result.data.assignments.associate { a ->
                        (a.user_id to a.process_master_id) to AssignmentState(assigned = true, isDefault = a.is_default)
                    }
                    originalState = map
                    workingState = map
                    if (selectedMasterId == null || processMasters.none { it.id == selectedMasterId }) {
                        selectedMasterId = processMasters.firstOrNull()?.id
                    }
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun selectProcessMaster(id: Int) {
        selectedMasterId = id
    }

    fun enterEdit() {
        isEditMode = true
    }

    /** 変更を全て元に戻す */
    fun cancelEdit() {
        workingState = originalState
        isEditMode = false
    }

    /** 担当のON/OFFを切り替える。未担当にした場合は★（デフォルト）も自動的に外れる */
    fun toggleAssignment(userId: Int, masterId: Int) {
        if (!isEditMode) return
        val key = userId to masterId
        val current = stateOf(workingState, key)
        val newMap = workingState.toMutableMap()
        if (current.assigned) {
            newMap.remove(key)
        } else {
            newMap[key] = AssignmentState(assigned = true, isDefault = false)
        }
        workingState = newMap
    }

    /** 自動割り振りのデフォルト担当者を切り替える。同じ工程の他の★は自動的に解除する */
    fun toggleDefault(userId: Int, masterId: Int) {
        if (!isEditMode) return
        val key = userId to masterId
        val current = stateOf(workingState, key)
        if (!current.assigned) return

        val newMap = workingState.toMutableMap()
        if (current.isDefault) {
            newMap[key] = current.copy(isDefault = false)
        } else {
            newMap.keys.filter { it.second == masterId }.forEach { k ->
                val s = newMap.getValue(k)
                if (s.isDefault) newMap[k] = s.copy(isDefault = false)
            }
            newMap[key] = current.copy(isDefault = true)
        }
        workingState = newMap
    }

    /**
     * 変更をまとめて保存する。工程への割り当てON/OFFを先に反映してから、
     * デフォルト担当者の変更を反映する（★の設定には先に割り当てが必要なため）。
     * 通信に失敗した場合は、一部だけ反映された可能性があるためサーバーの状態を読み直す。
     */
    fun save() {
        val keys = (originalState.keys + workingState.keys).toSet()
        val changed = keys.filter { stateOf(originalState, it) != stateOf(workingState, it) }
        if (changed.isEmpty()) {
            isEditMode = false
            return
        }

        viewModelScope.launch {
            isSaving = true
            var failMessage: String? = null

            for (key in changed) {
                val orig = stateOf(originalState, key)
                val work = stateOf(workingState, key)
                if (orig.assigned != work.assigned) {
                    when (val result = repo.toggleProcessAssignment(key.first, key.second)) {
                        is ApiResult.Success -> {}
                        is ApiResult.Failure -> {
                            failMessage = result.message
                            break
                        }
                    }
                }
            }

            if (failMessage == null) {
                for (key in changed) {
                    val orig = stateOf(originalState, key)
                    val work = stateOf(workingState, key)
                    if (!work.assigned || orig.isDefault == work.isDefault) continue
                    val result = if (work.isDefault) {
                        repo.setDefaultProcessAssignment(key.first, key.second)
                    } else {
                        repo.unsetDefaultProcessAssignment(key.first, key.second)
                    }
                    if (result is ApiResult.Failure) {
                        failMessage = result.message
                        break
                    }
                }
            }

            isSaving = false
            isEditMode = false
            if (failMessage != null) {
                message = failMessage
                load()
            } else {
                message = "保存しました。"
                originalState = workingState
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessAssignmentScreen(onBack: () -> Unit, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: ProcessAssignmentViewModel = viewModel(
        factory = viewModelFactory { initializer { ProcessAssignmentViewModel(container.managerRepository) } }
    )
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("担当工程マスタ") },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
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
            when {
                vm.loading && vm.processMasters.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                vm.error != null && vm.processMasters.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                }
                !vm.loading && vm.processMasters.isEmpty() -> Text(
                    "工程マスタが登録されていません。先に工程マスタを登録してください。",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                )
                !vm.loading && vm.workers.isEmpty() -> Text(
                    "作業者アカウントが登録されていません。",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                )
                else -> ProcessAssignmentContent(vm = vm, feedback = feedback)
            }
        }
    }
}

@Composable
private fun ProcessAssignmentContent(vm: ProcessAssignmentViewModel, feedback: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "作業者ごとに担当できる工程を設定します。◯をつけた作業者のみ割り振り時に表示されます。",
            fontSize = 13.sp,
            color = Color(0xFF6B7280),
        )
        Text(
            "◯の作業者の★をクリックすると、その工程の受注が登録された際に自動でその作業者が割り振られます（工程ごとに1人まで）。",
            fontSize = 13.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!vm.isEditMode) {
                Button(onClick = { feedback(); vm.enterEdit() }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("編集", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { feedback(); vm.save() },
                    enabled = !vm.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (vm.isSaving) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("保存する", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = { feedback(); vm.cancelEdit() }, enabled = !vm.isSaving) {
                    Text("キャンセル")
                }
                if (vm.pendingCount > 0) {
                    Text(
                        "${vm.pendingCount}件 変更あり（未保存）",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.processMasters.forEach { master ->
                val selected = vm.selectedMasterId == master.id
                FilterChip(
                    selected = selected,
                    onClick = { feedback(); vm.selectProcessMaster(master.id) },
                    label = { Text(master.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        val selectedMaster = vm.processMasters.firstOrNull { it.id == vm.selectedMasterId }
        if (selectedMaster != null) {
            val assignedCount = vm.assignedCountFor(selectedMaster.id)
            Text(
                "${selectedMaster.name}　$assignedCount / ${vm.workers.size}名 担当可",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f)) {
                items(vm.workers, key = { it.id }) { worker ->
                    val state = vm.stateFor(worker.id, selectedMaster.id)
                    WorkerAssignRow(
                        worker = worker,
                        assigned = state.assigned,
                        isDefault = state.isDefault,
                        editable = vm.isEditMode,
                        onToggleAssigned = { vm.toggleAssignment(worker.id, selectedMaster.id) },
                        onToggleDefault = { vm.toggleDefault(worker.id, selectedMaster.id) },
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "※ 工程マスタに登録されていない工程名が使われている場合は全作業者が表示されます。",
            fontSize = 11.sp,
            color = Color(0xFF9CA3AF),
        )
    }
}

@Composable
private fun WorkerAssignRow(
    worker: WorkerDto,
    assigned: Boolean,
    isDefault: Boolean,
    editable: Boolean,
    onToggleAssigned: () -> Unit,
    onToggleDefault: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(parseWorkerColor(worker.color)),
            contentAlignment = Alignment.Center,
        ) {
            Text(worker.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(worker.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))

        if (assigned) {
            IconButton(onClick = { feedback(); onToggleDefault() }, enabled = editable) {
                Icon(
                    if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "自動割り振りのデフォルト担当者にする",
                    tint = if (isDefault) Amber500 else Color(0xFFD1D5DB),
                )
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }

        Switch(
            checked = assigned,
            onCheckedChange = { onToggleAssigned() },
            enabled = editable,
        )
    }
}

private fun parseWorkerColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF9CA3AF)
    return runCatching {
        val h = hex.removePrefix("#")
        Color(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
    }.getOrDefault(Color(0xFF9CA3AF))
}
