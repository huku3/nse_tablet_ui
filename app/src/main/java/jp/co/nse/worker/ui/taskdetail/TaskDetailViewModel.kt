package jp.co.nse.worker.ui.taskdetail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.ImageAttachment
import jp.co.nse.worker.data.ItemActionResponse
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

    /**
     * 仮注文の全工程が完了し、在庫処理が必要なときの案内。OKで閉じたあとに続ける処理（画面を閉じる・読み直す）を持つ。
     * ブラウザ版（tablet.js）と同じく、事務所に確認してもらう案内だけを出す。
     */
    var stockNotice by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun dismissStockNotice() {
        val next = stockNotice
        stockNotice = null
        next?.invoke()
    }

    /** 追加修正フォーム内に出すエラー（ダイアログ表示中はスナックバーが隠れるため） */
    var reworkError by mutableStateOf<String?>(null)

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
                is ApiResult.Success -> {
                    val next = { if (popOnSuccess) onPop() else load() }
                    if (result.data.needs_stock) stockNotice = next else next()
                }
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

    /**
     * 「追加修正が必要」を登録する。成功したら[onSuccess]（入力フォームを閉じる）を呼んでから工程詳細を読み直す。
     * 失敗時はフォームを開いたまま、入力内容を残してメッセージだけ出す。
     * ステータスはアプリ側で書き換えず、読み直した結果の表示に任せる（最終検査などからは完了になる）。
     */
    fun requestRework(
        targetProcessId: Int,
        count: Int,
        content: String,
        attachments: List<ImageAttachment>,
        onSuccess: () -> Unit,
    ) {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch {
            actionRunning = true
            reworkError = null
            val result = repo.reportRework(d.order.id, d.process.id, targetProcessId, count, content, attachments)
            actionRunning = false
            when (result) {
                is ApiResult.Success -> {
                    onSuccess()
                    actionMessage = "追加修正を登録しました。"
                    load()
                }
                is ApiResult.Failure -> reworkError = result.message
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
            if (d.partial != null) {
                runItemAction({ repo.startItem(d.order.id, d.process.id) })
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

    // ===== 部分完了製品の1個ずつ操作 =====

    /**
     * 1個ずつ操作のAPIを呼び、成功したら[onSuccess]（既定は読み直し）を呼ぶ。失敗時はサーバーのmessageをそのまま出す。
     * 呼び出し側で actionRunning を立てていてもいなくても使えるよう、ここで立て直す。
     */
    private suspend fun runItemAction(
        call: suspend () -> ApiResult<ItemActionResponse>,
        onSuccess: (ItemActionResponse) -> Unit = { load() },
    ) {
        actionRunning = true
        val result = call()
        actionRunning = false
        when (result) {
            is ApiResult.Success -> onSuccess(result.data)
            is ApiResult.Failure -> {
                actionMessage = result.message
                // 他の担当者の操作などで状態がずれている可能性があるため読み直しておく
                load()
            }
        }
    }

    /** 開始（1個ずつ／まとめて／複数人工程の自分の次の1個／故障復旧後の再開） */
    fun startItem() {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch { runItemAction({ repo.startItem(d.order.id, d.process.id) }) }
    }

    /**
     * 完了（作業中の分／複数人工程は自分の1個）。
     * 工程のすべての個が完了したら[onProcessDone]（通常の工程完了と同じ動線）を呼び、それ以外は読み直す。
     */
    fun completeItem(onProcessDone: () -> Unit) {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch {
            runItemAction({ repo.completeItem(d.order.id, d.process.id) }) { res ->
                if (res.process_done) {
                    if (res.needs_stock) stockNotice = onProcessDone else onProcessDone()
                } else {
                    val label = PartialItemLabel.of(res.item_indexes.ifEmpty { listOfNotNull(res.item_index) })
                    actionMessage = when {
                        label.isEmpty() -> "完了にしました。"
                        res.item_done == false -> "${label}を完了しました（他の担当者の完了待ち）。"
                        else -> "${label}を完了しました。"
                    }
                    load()
                }
            }
        }
    }

    /** 誤って開始した場合の取り消し（単独担当は作業中の分、複数人工程は自分の1個を未着手に戻す） */
    fun undoStartItem() {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch { runItemAction({ repo.undoStartItem(d.order.id, d.process.id) }) }
    }

    /** 複数人工程のみ: 自分が作業中の1個を中断（paused）・故障中（broken）にする */
    fun pauseItem(status: String, pauseReason: String? = null) {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch { runItemAction({ repo.pauseItem(d.order.id, d.process.id, status, pauseReason) }) }
    }

    /** 複数人工程のみ: 中断中・故障中の1個を選んで再開 */
    fun resumeItem(itemIndex: Int) {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch { runItemAction({ repo.resumeItem(d.order.id, d.process.id, itemIndex) }) }
    }

    /** 単独担当のみ: 「まとめて作業する」の切り替え（その受注のその工程だけに保存される） */
    fun setBatchMode(enabled: Boolean) {
        val d = detail ?: return
        if (actionRunning) return
        viewModelScope.launch { runItemAction({ repo.updateBatchMode(d.order.id, d.process.id, enabled) }) }
    }
}
