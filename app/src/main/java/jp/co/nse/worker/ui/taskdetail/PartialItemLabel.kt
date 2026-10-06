package jp.co.nse.worker.ui.taskdetail

/** 部分完了の個数目の表示ラベル。サーバーの item_label と同じ書き方（「3個目」「1〜3個目」「1・3・4個目」） */
object PartialItemLabel {
    fun of(indexes: List<Int>): String {
        val sorted = indexes.distinct().sorted()
        if (sorted.isEmpty()) return ""
        if (sorted.size == 1) return "${sorted[0]}個目"
        val consecutive = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
        return if (consecutive) "${sorted.first()}〜${sorted.last()}個目" else "${sorted.joinToString("・")}個目"
    }
}
