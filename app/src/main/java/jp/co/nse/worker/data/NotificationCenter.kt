package jp.co.nse.worker.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 未読通知（工程の割り当て・次工程の順番が回ってきた等）をアプリ全体でポーリングして保持する。
 * ヘッダーのベルアイコンからどの画面（作業中の作業詳細画面も含む）でも同じ状態を参照できるように、
 * AppContainer に1つだけ持たせて共有する。
 */
class NotificationCenter(private val repo: WorkerRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollingJob: Job? = null

    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
        private set
    val unreadCount: Int get() = notifications.size

    /**
     * まだ確認されていない新着（ベルの吹き出し表示・一覧のNewバッジ用）。既知のID集合との差分で
     * 判定し、ユーザーが確認する（[consumeJustArrived]を呼ぶ）までポーリングをまたいで蓄積し続ける。
     */
    private var knownIds: Set<String>? = null
    var justArrived by mutableStateOf<List<NotificationDto>>(emptyList())
        private set

    /** ログイン中、30秒おきに未読通知を取得し続ける */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                refresh()
                delay(30_000)
            }
        }
    }

    /** ログアウト時に呼び、ポーリングと保持内容を止める */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        notifications = emptyList()
        knownIds = null
        justArrived = emptyList()
    }

    suspend fun refresh() {
        when (val result = repo.unreadNotifications()) {
            is ApiResult.Success -> {
                val newIds = result.data.map { it.id }.toSet()
                // ログイン直後の初回取得では、既にある未読を「新着」として吹き出し表示しない。
                // 2回目以降のポーリングでは、前回まで無かったIDを新着として積み増す。まだユーザーが
                // 確認していない前回までの新着はそのまま残しつつ、既読等でサーバーの未読一覧から
                // 消えたものだけ取り除く（ユーザーがベルを開くまで新着は消えない仕様のため）
                val previousIds = knownIds
                if (previousIds != null) {
                    val added = result.data.filter { it.id !in previousIds }
                    justArrived = (justArrived.filter { it.id in newIds } + added).distinctBy { it.id }
                }
                knownIds = newIds
                notifications = result.data
            }
            is ApiResult.Failure -> { /* ポーリング中の失敗は静かに無視し次回再試行する */ }
        }
    }

    /** ベルの吹き出し・一覧のNewバッジを消す。通知一覧を実際に開いた（閉じた）ときに呼ぶ */
    fun consumeJustArrived() {
        justArrived = emptyList()
    }

    /** 全件既読にする。楽観的にすぐ0件へ更新し、失敗時のみ実際の状態に戻す */
    fun markAllRead() {
        if (notifications.isEmpty()) return
        notifications = emptyList()
        justArrived = emptyList()
        scope.launch {
            when (repo.markAllNotificationsRead()) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> refresh()
            }
        }
    }

    /** 通知一覧でのスワイプ操作用。1件だけ既読にする。楽観的に即座にリストから消し、失敗時のみ元に戻す */
    fun markRead(id: String) {
        if (notifications.none { it.id == id }) return
        notifications = notifications.filterNot { it.id == id }
        justArrived = justArrived.filterNot { it.id == id }
        scope.launch {
            when (repo.markNotificationRead(id)) {
                is ApiResult.Success -> {}
                is ApiResult.Failure -> refresh()
            }
        }
    }
}
