package jp.co.nse.worker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.ui.assignment.AssignmentListScreen
import jp.co.nse.worker.ui.assignment.OrderListMode
import jp.co.nse.worker.ui.calendar.ShippingCalendarScreen
import jp.co.nse.worker.ui.tasklist.TaskListScreen
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class HomeTab(val label: String, val icon: ImageVector) {
    TASKS("作業一覧", Icons.AutoMirrored.Filled.ListAlt),
    ASSIGN("割り当て", Icons.Filled.Group),
    ORDERS("受注一覧", Icons.AutoMirrored.Filled.Assignment),
    SHIPPING("出荷カレンダー", Icons.Filled.LocalShipping),
}

/**
 * 実際に権限を持つタブだけを返す（割り当て＝checksheet.assign_worker、受注一覧＝orders.view、
 * 出荷カレンダー＝shipping.calendar）。[HomeScreen]の下部タブと、ヘッダーの
 * [jp.co.nse.worker.ui.components.HeaderOverflowMenu]のタブ切替メニューの両方から参照される。
 * 在庫は下部タブではなくヘッダーのメニューからのみ開く単独画面（[jp.co.nse.worker.data.AppContainer.openInventory]）。
 */
fun visibleHomeTabs(canAssign: Boolean, canViewOrders: Boolean, canViewShipping: Boolean): List<HomeTab> = buildList {
    add(HomeTab.TASKS)
    // 割り当て（checksheet.assign_worker）と受注一覧（orders.view）は独立した別機能のため、
    // 両方の権限を持つアカウントには両方のタブを表示する
    if (canAssign) add(HomeTab.ASSIGN)
    if (canViewOrders) add(HomeTab.ORDERS)
    if (canViewShipping) add(HomeTab.SHIPPING)
}

/**
 * ログイン後のホーム。実際に権限を持つタブだけを下部に並べる（割り当て＝checksheet.assign_worker、
 * 受注一覧＝orders.view、出荷カレンダー＝shipping.calendar）。割り当てと受注一覧は独立した
 * 別機能のため、両方の権限を持つアカウントには両方のタブを表示する。タブが複数ある場合は、
 * 下部タブのタップだけでなく画面を横にスワイプしても切り替えられるようにする。
 */
@Composable
fun HomeScreen(
    onOpenTask: (processId: Int) -> Unit,
    onOpenOrder: (orderId: Int) -> Unit,
    onOpenCheckSheet: (orderId: Int) -> Unit,
    onLogout: () -> Unit,
    completedProcessName: String? = null,
    onCompletedMessageShown: () -> Unit = {},
    initialTab: String = "",
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer

    // canAssign等はDataStoreの実際の値が届く前、一瞬だけ初期値falseを返す。この値でtabsを
    // 組み立てると一時的に「作業一覧タブしか無い」状態になり、その一瞬でrememberPagerStateの
    // initialPageが確定してしまう（rememberは初回コンポーズ時の値だけを使うため、後でtabsが
    // 増えても作り直されない）。その結果、ヘッダーメニュー等からinitialTabを指定して開いても
    // 常に先頭の作業一覧タブに固定されてしまっていたため、実際の値が揃うまではtabsを組み立てず待つ
    var permissionsReady by remember { mutableStateOf(false) }
    var canAssign by remember { mutableStateOf(false) }
    var canViewOrders by remember { mutableStateOf(false) }
    var canViewShipping by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        canAssign = container.settings.canAssignFlow.first()
        canViewOrders = container.settings.canViewOrdersFlow.first()
        canViewShipping = container.settings.canViewShippingFlow.first()
        permissionsReady = true
    }
    if (!permissionsReady) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val canAssignState by container.settings.canAssignFlow.collectAsState(initial = canAssign)
    val canViewOrdersState by container.settings.canViewOrdersFlow.collectAsState(initial = canViewOrders)
    val canViewShippingState by container.settings.canViewShippingFlow.collectAsState(initial = canViewShipping)

    val tabs = remember(canAssignState, canViewOrdersState, canViewShippingState) {
        visibleHomeTabs(canAssignState, canViewOrdersState, canViewShippingState)
    }

    val feedback = rememberClickFeedback()
    val scope = rememberCoroutineScope()
    val initialPage = remember(tabs) { tabs.indexOfFirst { it.name == initialTab }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tabs.size })
    // ヘッダーロゴ・オーバーフローメニューのタブ切替項目から、指定したタブへ直接切り替える
    val switchTab: (String) -> Unit = { key ->
        val index = tabs.indexOfFirst { it.name == key }
        if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
    }

    @Composable
    fun TabContent(tab: HomeTab, isActive: Boolean) {
        when (tab) {
            HomeTab.TASKS -> TaskListScreen(
                onOpenTask = onOpenTask,
                onLogout = onLogout,
                completedProcessName = completedProcessName,
                onCompletedMessageShown = onCompletedMessageShown,
                isActive = isActive,
                onSwitchTab = switchTab,
            )
            HomeTab.ASSIGN -> AssignmentListScreen(onOpenOrder = onOpenOrder, onLogout = onLogout, isActive = isActive, onSwitchTab = switchTab)
            // 受注一覧のみの権限では担当者割り当ての編集はできないため、タップ先は
            // 工程管理チェックシート（閲覧のみ）にする
            HomeTab.ORDERS -> AssignmentListScreen(
                onOpenOrder = onOpenCheckSheet,
                onLogout = onLogout,
                title = "受注一覧",
                mode = OrderListMode.ORDER_LIST,
                isActive = isActive,
                onSwitchTab = switchTab,
            )
            HomeTab.SHIPPING -> ShippingCalendarScreen(onOpenCheckSheet = onOpenCheckSheet, onLogout = onLogout, isActive = isActive, onSwitchTab = switchTab)
        }
    }

    if (tabs.size <= 1) {
        TabContent(HomeTab.TASKS, isActive = true)
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            feedback()
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            HorizontalPager(
                state = pagerState,
                // 全タブをあらかじめcomposeしたまま維持する。デフォルト（0）だと画面外のタブは
                // スワイプ/タップで表示されるたびに破棄→再構築され、その瞬間に各タブの初回データ
                // 取得（LaunchedEffect等）が走り直してラグや通信の重複が発生していたため
                beyondViewportPageCount = tabs.size,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                TabContent(tabs[page], isActive = pagerState.currentPage == page)
            }
        }
    }
}
