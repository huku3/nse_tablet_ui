package jp.co.nse.worker.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * 生産管理システム側のデータ更新をタブレット画面にも反映するための定期再取得。
 *
 * [isActive] が true の間（＝そのタブが今表示されている間）だけポーリングし、他のタブに
 * 切り替わったり画面がバックグラウンドに回ったりすると repeatOnLifecycle が自動で止める
 * ので、無駄な通信は発生しない。[onRefresh] は一覧が既にある状態での裏側の再取得を想定して
 * おり、各画面側で「ローディング中 && 一覧が空」のときだけスピナーを出す作りになっている限り、
 * ユーザー操作を邪魔せず静かに最新化される。
 *
 * [refreshImmediately] を true にすると、タブに切り替わった瞬間（＝[isActive]がfalse→true
 * になった瞬間、およびアプリがバックグラウンドから復帰した瞬間）にも即座に1回再取得する。
 * 通信自体はviewModelScope上の非同期処理でUIスレッドをブロックしないため、これを入れても
 * タブ切り替えのアニメーションがカクつくことはない。
 */
@Composable
fun AutoRefreshEffect(
    isActive: Boolean,
    intervalMillis: Long = 30_000L,
    refreshImmediately: Boolean = false,
    onRefresh: suspend () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isActive, lifecycleOwner) {
        if (!isActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (refreshImmediately) onRefresh()
            while (true) {
                delay(intervalMillis)
                onRefresh()
            }
        }
    }
}
