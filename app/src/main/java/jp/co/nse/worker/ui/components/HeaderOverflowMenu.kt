package jp.co.nse.worker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.ui.visibleHomeTabs
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * ハンバーガーメニュー（３本線）。ホーム画面下部のタブ切替と、マイページ・ダッシュボード・
 * 担当工程マスタ・報告関連への導線をまとめている。以前は各画面のヘッダーに5つ前後の
 * アイコンを並べていたが、数が多く分かりにくいため、使用頻度の低いものをテキスト付きの
 * メニューにまとめた（通知ベル・更新・ログアウトは引き続き画面ごとに常時表示のアイコンとして残す）。
 * 画面下部のタブバー自体はそのまま残し、このメニューはどの画面からでも同じタブへ移動できる
 * ショートカットを追加するもの。
 *
 * 各画面は自分自身を開く項目だけ[showMyPage]等をfalseにして隠す
 * （例：マイページ画面では「マイページ」項目を出さない）。担当工程マスタ・報告一覧は、
 * 元のボタンと同じくその権限を持つアカウントにしか表示されない。
 *
 * [currentHomeTab]には現在表示中のホームタブ名（[jp.co.nse.worker.ui.HomeTab.name]）を渡すと、
 * そのタブ自身の項目を隠す。[onSwitchHomeTab]を渡すとタブ切替はそちらを使う
 * （ホーム画面のタブ内から呼ばれた場合、画面遷移せずpagerStateで直接切り替えるため）。
 * 渡さない場合は[jp.co.nse.worker.data.AppContainer.openHomeTab]でホーム画面へ遷移する。
 */
@Composable
fun HeaderOverflowMenu(
    showMyPage: Boolean = true,
    showSettings: Boolean = true,
    showDashboard: Boolean = true,
    showProcessAssignments: Boolean = true,
    showInventory: Boolean = true,
    showOrderInquiry: Boolean = true,
    showScanData: Boolean = true,
    showReport: Boolean = true,
    showReportList: Boolean = true,
    currentHomeTab: String? = null,
    onSwitchHomeTab: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val feedback = rememberClickFeedback()
    var expanded by remember { mutableStateOf(false) }
    val canManageProcessAssignments by context.appContainer.settings.canManageProcessAssignmentsFlow.collectAsState(initial = false)
    val canManageReports by context.appContainer.settings.canManageReportsFlow.collectAsState(initial = false)
    val canViewScanData by context.appContainer.settings.canViewScanDataFlow.collectAsState(initial = false)
    val canAssign by context.appContainer.settings.canAssignFlow.collectAsState(initial = false)
    val canViewOrders by context.appContainer.settings.canViewOrdersFlow.collectAsState(initial = false)
    val canViewShipping by context.appContainer.settings.canViewShippingFlow.collectAsState(initial = false)
    val homeTabs = remember(canAssign, canViewOrders, canViewShipping, currentHomeTab) {
        visibleHomeTabs(canAssign, canViewOrders, canViewShipping).filter { it.name != currentHomeTab }
    }
    val switchTab = onSwitchHomeTab ?: { key: String -> context.appContainer.openHomeTab?.invoke(key) }

    Box {
        IconButton(onClick = { feedback(); expanded = true }) {
            Icon(Icons.Filled.Menu, contentDescription = "メニュー", tint = MaterialTheme.colorScheme.onPrimary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // 他のカード類（作業実績・出荷カレンダーなど）と統一感を持たせるため、
            // アプリ内で使っている白背景・角丸16dpに揃える（M3標準のままだとメイン色が
            // うっすら乗った灰色がかった背景になり、他の白いカードと馴染まないため）
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
        ) {
            homeTabs.forEach { tab ->
                DropdownMenuItem(
                    text = { Text(tab.label) },
                    leadingIcon = { Icon(tab.icon, contentDescription = null) },
                    onClick = { expanded = false; feedback(); switchTab(tab.name) },
                )
            }
            if (homeTabs.isNotEmpty()) HorizontalDivider()
            if (showMyPage) {
                DropdownMenuItem(
                    text = { Text("作業実績") },
                    leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openMyPage?.invoke() },
                )
            }
            if (showSettings) {
                DropdownMenuItem(
                    text = { Text("設定") },
                    leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openSettings?.invoke() },
                )
            }
            if (showDashboard) {
                DropdownMenuItem(
                    text = { Text("ダッシュボード") },
                    leadingIcon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openDashboard?.invoke() },
                )
            }
            if (showProcessAssignments && canManageProcessAssignments) {
                DropdownMenuItem(
                    text = { Text("担当工程マスタ") },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openProcessAssignments?.invoke() },
                )
            }
            // 在庫は受注一覧権限（orders.view、閲覧のみ）か割り当て権限（checksheet.assign_worker、
            // 閲覧＋入荷・引当等の操作）のどちらかを持つアカウントに表示する
            if (showInventory && (canViewOrders || canAssign)) {
                DropdownMenuItem(
                    text = { Text("在庫") },
                    leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openInventory?.invoke() },
                )
            }
            // 受注照会（客先問い合わせ対応）も在庫と同じく、受注一覧権限か割り当て権限の
            // どちらかを持つアカウントに表示する
            if (showOrderInquiry && (canViewOrders || canAssign)) {
                DropdownMenuItem(
                    text = { Text("受注照会") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openOrderInquiry?.invoke() },
                )
            }
            if (showScanData && canViewScanData) {
                DropdownMenuItem(
                    text = { Text("スキャンデータ") },
                    leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openScanData?.invoke() },
                )
            }
            if (showReport) {
                DropdownMenuItem(
                    text = { Text("不具合・要望の報告") },
                    leadingIcon = { Icon(Icons.Filled.Feedback, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openReport?.invoke() },
                )
            }
            if (showReportList && canManageReports) {
                DropdownMenuItem(
                    text = { Text("報告一覧") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null) },
                    onClick = { expanded = false; feedback(); context.appContainer.openReportList?.invoke() },
                )
            }
        }
    }
}
