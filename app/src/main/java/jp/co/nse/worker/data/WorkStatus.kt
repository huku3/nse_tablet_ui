package jp.co.nse.worker.data

/**
 * 工程ステータス。Laravel側 App\Models\OrderProcess の定数に対応する。
 */
object WorkStatus {
    const val WAITING = "waiting"
    const val IN_PROGRESS = "in_progress"
    const val PAUSED = "paused"
    const val BROKEN = "broken"
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"

    /** 日本語ラベル */
    fun label(status: String): String = when (status) {
        WAITING -> "待機"
        IN_PROGRESS -> "作業中"
        PAUSED -> "一時停止"
        BROKEN -> "機械停止"
        COMPLETED -> "作業完了"
        SKIPPED -> "スキップ"
        else -> status
    }

    /** 中断理由（App\Models\OrderProcess::$pauseReasons） */
    val pauseReasons = listOf("電話対応", "トイレ", "休憩", "その他")
}
