package jp.co.nse.worker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.ui.assignment.AssignmentListScreen
import jp.co.nse.worker.ui.assignment.OrderListMode
import jp.co.nse.worker.ui.calendar.ShippingCalendarScreen
import jp.co.nse.worker.ui.tasklist.TaskListScreen
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch

private enum class HomeTab(val label: String, val icon: ImageVector) {
    TASKS("作業一覧", Icons.AutoMirrored.Filled.ListAlt),
    ASSIGN("割り当て", Icons.Filled.Group),
    ORDERS("受注一覧", Icons.AutoMirrored.Filled.Assignment),
    SHIPPING("出荷カレンダー", Icons.Filled.LocalShipping),
}

/**
 * ログイン後のホーム。実際に権限を持つタブだけを下部に並べる（割り当て＝checksheet.assign_worker、
 * 受注一覧＝orders.view、出荷カレンダー＝shipping.calendar）。割り当て権限を持つ場合は
 * 受注一覧タブと内容が重複するため受注一覧タブは表示しない。タブが複数ある場合は、
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
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val canAssign by container.settings.canAssignFlow.collectAsState(initial = false)
    val canViewOrders by container.settings.canViewOrdersFlow.collectAsState(initial = false)
    val canViewShipping by container.settings.canViewShippingFlow.collectAsState(initial = false)

    val tabs = remember(canAssign, canViewOrders, canViewShipping) {
        buildList {
            add(HomeTab.TASKS)
            if (canAssign) {
                add(HomeTab.ASSIGN)
            } else if (canViewOrders) {
                add(HomeTab.ORDERS)
            }
            if (canViewShipping) add(HomeTab.SHIPPING)
        }
    }

    @Composable
    fun TabContent(tab: HomeTab) {
        when (tab) {
            HomeTab.TASKS -> TaskListScreen(
                onOpenTask = onOpenTask,
                onLogout = onLogout,
                completedProcessName = completedProcessName,
                onCompletedMessageShown = onCompletedMessageShown,
            )
            HomeTab.ASSIGN -> AssignmentListScreen(onOpenOrder = onOpenOrder, onLogout = onLogout)
            // 受注一覧のみの権限では担当者割り当ての編集はできないため、タップ先は
            // 工程管理チェックシート（閲覧のみ）にする
            HomeTab.ORDERS -> AssignmentListScreen(
                onOpenOrder = onOpenCheckSheet,
                onLogout = onLogout,
                title = "受注一覧",
                mode = OrderListMode.ORDER_LIST,
            )
            HomeTab.SHIPPING -> ShippingCalendarScreen(onOpenCheckSheet = onOpenCheckSheet, onLogout = onLogout)
        }
    }

    if (tabs.size <= 1) {
        TabContent(HomeTab.TASKS)
        return
    }

    val feedback = rememberClickFeedback()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { tabs.size })

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
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                TabContent(tabs[page])
            }

            // 横スワイプできることを示す薄いマーク。タップでもページを切り替えられる
            PageIndicatorDots(
                pageCount = tabs.size,
                pagerState = pagerState,
                onSelect = { index -> feedback(); scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

@Composable
private fun PageIndicatorDots(pageCount: Int, pagerState: PagerState, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == pagerState.currentPage
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.45f else 0.18f),
                        ),
                )
            }
        }
    }
}
