package jp.co.nse.worker.data

/**
 * 受注区分。生産管理システム側 Order::$orderTypes に対応する。
 */
object OrderType {
    const val INITIAL = "initial"
    const val MASS_PRODUCTION = "mass_production"
    const val FOUR_M_CHANGE = "4m_change"
    const val PROTOTYPE = "prototype"
    const val DEFECT = "defect"

    /** 表示順を保った (値, ラベル) の一覧 */
    val all: List<Pair<String, String>> = listOf(
        INITIAL to "初品",
        MASS_PRODUCTION to "量産",
        FOUR_M_CHANGE to "4M変更",
        PROTOTYPE to "試作",
        DEFECT to "不具合対応",
    )

    private val labels = all.toMap()

    /** 日本語ラベル（未知の値はそのまま返す） */
    fun label(orderType: String?): String {
        val key = orderType ?: INITIAL
        return labels[key] ?: key
    }

    /**
     * 初品かどうか（特に注意して作業すべき区分）。
     * Web版 worker/_task_card.blade.php の `$isInitial = $ord->order_type === 'initial'` と同じ厳密一致。
     * null（未設定）は初品扱いにしない。区分が明示的に量産等であればそちらを優先する。
     */
    fun isInitial(orderType: String?): Boolean = orderType == INITIAL
}
