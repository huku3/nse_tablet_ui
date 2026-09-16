package jp.co.nse.worker.ui.checksheet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.CheckSheetOrderDto
import jp.co.nse.worker.data.ManagerRepository
import jp.co.nse.worker.data.ProcessAssignmentsResponse
import jp.co.nse.worker.data.WorkerDto
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.dashboard.StaffLeaveDepartment
import jp.co.nse.worker.util.DateUtil
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CheckSheetViewModel(
    private val repo: WorkerRepository,
    private val managerRepo: ManagerRepository,
    private val orderId: Int,
) : ViewModel() {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var order by mutableStateOf<CheckSheetOrderDto?>(null)
        private set

    // 担当者割り当て権限がある場合のみ使う編集系の状態
    var workers by mutableStateOf<List<WorkerDto>>(emptyList())
        private set
    private var processAssignments by mutableStateOf(ProcessAssignmentsResponse())
    /** 本日休暇の作業者名（担当者選択ダイアログで選択不可にする対象） */
    var todayLeaveWorkerNames by mutableStateOf<Set<String>>(emptySet())
        private set
    /** 工程納期カレンダーで選択不可にする、割り当て中の作業者の休暇予定日（表示中の月分） */
    var deadlineWorkerLeaveDates by mutableStateOf<Set<String>>(emptySet())
        private set
    private var loadedDeadlineLeaveKey: Pair<String, YearMonth>? = null
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

    var markingArrived by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.checksheet(orderId)) {
                is ApiResult.Success -> order = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    /** 担当者割り当て権限があるユーザーのみ、候補一覧を取得する */
    fun loadWorkers() {
        if (workers.isNotEmpty()) return
        viewModelScope.launch {
            when (val result = managerRepo.workers()) {
                is ApiResult.Success -> workers = result.data
                is ApiResult.Failure -> { /* 候補取得失敗は致命的ではない */ }
            }
            when (val result = managerRepo.processAssignments()) {
                is ApiResult.Success -> processAssignments = result.data
                // 取得失敗時はeligibleWorkers()が全員表示にフォールバックする
                is ApiResult.Failure -> { }
            }
            val today = LocalDate.now().toString()
            when (val leaves = managerRepo.leaves(today, today, StaffLeaveDepartment)) {
                is ApiResult.Success -> todayLeaveWorkerNames =
                    leaves.data.days.flatMap { it.leaves }.map { it.user_name }.toSet()
                is ApiResult.Failure -> { /* 取得失敗時は選択制限をかけないだけにする */ }
            }
        }
    }

    /**
     * 工程納期カレンダーで表示中の月について、割り当て中の作業者の休暇予定日を取得する。
     * 同じ（作業者・月）の組み合わせは再取得しない。
     */
    fun ensureDeadlineWorkerLeaveLoaded(workerName: String?, visibleMonth: YearMonth) {
        if (workerName.isNullOrBlank()) {
            deadlineWorkerLeaveDates = emptySet()
            return
        }
        val key = workerName to visibleMonth
        if (key == loadedDeadlineLeaveKey) return
        loadedDeadlineLeaveKey = key
        viewModelScope.launch {
            when (
                val result = managerRepo.leaves(
                    visibleMonth.atDay(1).toString(),
                    visibleMonth.atEndOfMonth().toString(),
                    StaffLeaveDepartment,
                )
            ) {
                is ApiResult.Success -> deadlineWorkerLeaveDates = result.data.days
                    .filter { day -> day.leaves.any { it.user_name == workerName } }
                    .map { it.date }
                    .toSet()
                is ApiResult.Failure -> deadlineWorkerLeaveDates = emptySet()
            }
        }
    }

    /**
     * 担当者選択ダイアログの候補を、担当工程マスタでこの工程に登録されている作業者だけに絞り込む。
     * マスタ側にその工程自体が登録されていない場合は絞り込みようがないため全員を対象にする。
     */
    fun eligibleWorkers(processName: String): List<WorkerDto> {
        val master = processAssignments.process_masters.find { it.name == processName } ?: return workers
        val assignedUserIds = processAssignments.assignments
            .filter { it.process_master_id == master.id }
            .map { it.user_id }
            .toSet()
        return workers.filter { it.id in assignedUserIds }
    }

    fun assign(processId: Int, workerName: String?) {
        viewModelScope.launch {
            saving = true
            val result = managerRepo.assignWorker(orderId, processId, workerName)
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
            when (val result = managerRepo.holidayCalendar(fy)) {
                is ApiResult.Success -> {
                    holidayDates = holidayDates + result.data.holidays
                    overrideDates = overrideDates + result.data.overrides
                }
                is ApiResult.Failure -> { /* 取得失敗時はサーバー側の最終チェックに委ねる */ }
            }
        }
    }

    fun updateDeadline(processId: Int, deadline: LocalDate?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            saving = true
            deadlineError = null
            val result = managerRepo.updateDeadline(orderId, processId, deadline?.toString())
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

    /** 材料到着済みにする（material_arrived_date → material_arrived） */
    fun markMaterialArrived() {
        viewModelScope.launch {
            markingArrived = true
            when (val result = repo.markMaterialArrived(orderId)) {
                is ApiResult.Success -> reloadOrder()
                is ApiResult.Failure -> message = result.message
            }
            markingArrived = false
        }
    }

    private suspend fun reloadOrder() {
        when (val result = repo.checksheet(orderId)) {
            is ApiResult.Success -> order = result.data
            is ApiResult.Failure -> message = result.message
        }
    }
}
