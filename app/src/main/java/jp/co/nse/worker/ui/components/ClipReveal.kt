package jp.co.nse.worker.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * CSSの `clip-path: inset(0 X% 0 0)` を左から右へアニメーションさせるのと同じ要領で、
 * 中身を左から右へ滑らかに出現させる（フェードではなくワイプで見せる）。
 * [progress] は 0f（非表示）〜 1f（全体表示）。
 */
fun Modifier.clipReveal(progress: Float): Modifier = this.drawWithContent {
    val revealWidth = size.width * progress.coerceIn(0f, 1f)
    clipRect(right = revealWidth) {
        this@drawWithContent.drawContent()
    }
}
