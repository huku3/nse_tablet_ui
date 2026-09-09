package jp.co.nse.worker.ui.taskdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.TaskDetailDto
import jp.co.nse.worker.data.WorkStatus
import jp.co.nse.worker.data.WorkerRepository
import kotlinx.coroutines.launch

class TaskDetailViewModel(
    private val repo: WorkerRepository,
    private val processId: Int,
) : ViewModel() {

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var detail by mutableStateOf<TaskDetailDto?>(null)
        private set

    /** アクション実行中（ボタン二重押下防止） */
    var actionRunning by mutableStateOf(false)
        private set

    /** 一時的なエラーメッセージ（スナックバー表示用） */
    var actionMessage by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val result = repo.detail(processId)) {
                is ApiResult.Success -> detail = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    /**
     * ステータスを更新する。
     * @param popOnSuccess 完了時など、成功後に画面を閉じる場合true
     */
    fun changeStatus(
        status: String,
        pauseReason: String? = null,
        popOnSuccess: Boolean = false,
        onPop: () -> Unit = {},
    ) {
        val d = detail ?: return
        viewModelScope.launch {
            actionRunning = true
            val result = repo.updateStatus(d.order.id, d.process.id, status, pauseReason)
            actionRunning = false
            when (result) {
                is ApiResult.Success -> if (popOnSuccess) onPop() else load()
                is ApiResult.Failure -> actionMessage = result.message
            }
        }
    }

    fun reportDefect(count: Int) {
        val d = detail ?: return
        viewModelScope.launch {
            actionRunning = true
            val result = repo.reportDefect(d.order.id, d.process.id, count)
            actionRunning = false
            when (result) {
                is ApiResult.Success -> load()
                is ApiResult.Failure -> actionMessage = result.message
            }
        }
    }

    /** 材料確認OK → 確認APIを呼んでから作業開始 */
    fun confirmMaterialThenStart() {
        val d = detail ?: return
        viewModelScope.launch {
            actionRunning = true
            val confirm = repo.confirmMaterial(d.order.id)
            if (confirm is ApiResult.Failure) {
                actionRunning = false
                actionMessage = confirm.message
                return@launch
            }
            val start = repo.updateStatus(d.order.id, d.process.id, WorkStatus.IN_PROGRESS)
            actionRunning = false
            when (start) {
                is ApiResult.Success -> load()
                is ApiResult.Failure -> actionMessage = start.message
            }
        }
    }
}
