package jp.co.nse.worker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.ui.announcement.AnnouncementDetailScreen
import jp.co.nse.worker.ui.announcement.AnnouncementListScreen
import jp.co.nse.worker.ui.assignment.AssignmentDetailScreen
import jp.co.nse.worker.ui.checksheet.CheckSheetScreen
import jp.co.nse.worker.ui.dashboard.DashboardScreen
import jp.co.nse.worker.ui.dashboard.StaffLeaveCalendarScreen
import jp.co.nse.worker.ui.drawing.DrawingScreen
import jp.co.nse.worker.ui.inquiry.OrderInquiryScreen
import jp.co.nse.worker.ui.inventory.InventoryScreen
import jp.co.nse.worker.ui.login.LoginScreen
import jp.co.nse.worker.ui.mypage.MyPageScreen
import jp.co.nse.worker.ui.processassignment.ProcessAssignmentScreen
import jp.co.nse.worker.ui.report.ReportDetailScreen
import jp.co.nse.worker.ui.report.ReportListScreen
import jp.co.nse.worker.ui.report.ReportScreen
import jp.co.nse.worker.ui.scandata.ScanDataScreen
import jp.co.nse.worker.ui.settings.SettingsScreen
import jp.co.nse.worker.ui.splash.SplashScreen
import jp.co.nse.worker.ui.taskdetail.TaskDetailScreen
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val MYPAGE = "mypage"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val HOME = "home?tab={tab}"
    const val DETAIL = "detail/{processId}"
    const val ASSIGN_DETAIL = "assign/{orderId}"
    const val PROCESS_ASSIGNMENTS = "process-assignments"
    const val INVENTORY = "inventory"
    const val ORDER_INQUIRY = "order-inquiry"
    const val SCAN_DATA = "scan-data"
    const val REPORT = "report"
    const val REPORT_LIST = "report-list"
    const val REPORT_DETAIL = "report-detail/{reportId}"
    const val ANNOUNCEMENT_LIST = "announcement-list"
    const val ANNOUNCEMENT_DETAIL = "announcement-detail/{announcementId}"

    fun reportDetail(reportId: Int) = "report-detail/$reportId"
    fun announcementDetail(announcementId: Int) = "announcement-detail/$announcementId"
    const val DRAWING = "drawing/{processId}?title={title}&orderId={orderId}&poNumber={poNumber}"
    const val CHECKSHEET = "checksheet/{orderId}"
    const val STAFF_LEAVE_CALENDAR = "staff-leave-calendar?date={date}"

    fun detail(processId: Int) = "detail/$processId"
    fun assignDetail(orderId: Int) = "assign/$orderId"
    fun drawing(processId: Int, title: String?, orderId: Int, poNumber: String?) =
        "drawing/$processId?title=${Uri.encode(title ?: "")}" +
            "&orderId=$orderId&poNumber=${Uri.encode(poNumber ?: "")}"
    fun checksheet(orderId: Int) = "checksheet/$orderId"
    fun staffLeaveCalendar(date: java.time.LocalDate) = "staff-leave-calendar?date=$date"
    fun home(tab: String? = null) = "home?tab=${tab ?: ""}"
}

/**
 * 戻るボタンや遷移用ボタンを連打すると、画面遷移アニメーションの完了前にpopBackStack()/navigate()が
 * 複数回実行されてナビゲーションスタックの状態が壊れ、画面が真っ白なまま操作不能になることがある。
 * 現在の画面のライフサイクルがRESUMED（＝直前の遷移アニメーションが完了し実際に最前面にある）
 * 状態のときだけ実行することで、連打による二重実行を防ぐ（Android公式が推奨するガード）。
 */
private fun NavHostController.popBackStackSafely(): Boolean {
    val isResumed = currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
    return isResumed && popBackStack()
}

private fun NavHostController.navigateSafely(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        navigate(route)
    }
}

private fun NavHostController.navigateSafely(route: String, builder: NavOptionsBuilder.() -> Unit) {
    if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        navigate(route, builder)
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()

    // 通知一覧のタップなど、NavControllerを直接持たないUIから工程詳細へ遷移できるようにする
    container.openTask = { processId -> navController.navigateSafely(Routes.detail(processId)) }
    // どの画面のヘッダーからでもマイページへ遷移できるようにする
    container.openMyPage = { navController.navigateSafely(Routes.MYPAGE) }
    // どの画面のヘッダーからでも設定（メインカラー・文字の見た目）へ遷移できるようにする
    container.openSettings = { navController.navigateSafely(Routes.SETTINGS) }
    // どの画面のヘッダーからでもダッシュボードへ遷移できるようにする
    container.openDashboard = { navController.navigateSafely(Routes.DASHBOARD) }
    // 権限があるアカウントは、どの画面のヘッダーからでも担当工程マスタへ遷移できるようにする
    container.openProcessAssignments = { navController.navigateSafely(Routes.PROCESS_ASSIGNMENTS) }
    // 権限があるアカウントは、どの画面のヘッダーからでも在庫画面へ遷移できるようにする
    container.openInventory = { navController.navigateSafely(Routes.INVENTORY) }
    // 権限があるアカウントは、どの画面のヘッダーからでも受注照会画面へ遷移できるようにする
    container.openOrderInquiry = { navController.navigateSafely(Routes.ORDER_INQUIRY) }
    // 権限があるアカウントは、どの画面のヘッダーからでもスキャンデータ画面へ遷移できるようにする
    container.openScanData = { navController.navigateSafely(Routes.SCAN_DATA) }
    // どの画面のヘッダーからでも不具合・要望の報告画面へ遷移できるようにする
    container.openReport = { navController.navigateSafely(Routes.REPORT) }
    // 権限があるアカウントは、どの画面のヘッダーからでも報告一覧へ遷移できるようにする
    container.openReportList = { navController.navigateSafely(Routes.REPORT_LIST) }
    // どの画面のヘッダーロゴからでも作業一覧（ホーム）まで一気に戻れるようにする
    container.openHome = {
        navController.navigateSafely(Routes.home()) {
            popUpTo(0) { inclusive = true }
        }
    }
    // どの画面のヘッダーメニューからでも、ホーム画面の指定タブへ一気に戻れるようにする
    container.openHomeTab = { tabKey ->
        navController.navigateSafely(Routes.home(tabKey)) {
            popUpTo(0) { inclusive = true }
        }
    }

    val logout: () -> Unit = {
        container.notificationCenter.stopPolling()
        scope.launch {
            container.authRepository.logout()
            navController.navigateSafely(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    scope.launch {
                        val loggedIn = container.authRepository.isLoggedIn()
                        if (loggedIn) container.notificationCenter.startPolling()
                        val target = if (loggedIn) Routes.home() else Routes.LOGIN
                        navController.navigateSafely(target) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onContinue = {
                    container.notificationCenter.startPolling()
                    navController.navigateSafely(Routes.home()) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
                onLogout = logout,
                onOpenCheckSheet = { orderId -> navController.navigateSafely(Routes.checksheet(orderId)) },
                onOpenStaffLeaveCalendar = { date -> navController.navigateSafely(Routes.staffLeaveCalendar(date)) },
                onOpenAnnouncementHistory = { navController.navigateSafely(Routes.ANNOUNCEMENT_LIST) },
                onOpenAnnouncementDetail = { id -> navController.navigateSafely(Routes.announcementDetail(id)) },
            )
        }

        composable(
            route = Routes.STAFF_LEAVE_CALENDAR,
            arguments = listOf(navArgument("date") { type = NavType.StringType; nullable = true }),
        ) { backStackEntry ->
            val initialDate = backStackEntry.arguments?.getString("date")
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                ?: java.time.LocalDate.now()
            StaffLeaveCalendarScreen(
                onBack = { navController.popBackStackSafely() },
                initialDate = initialDate,
                onLogout = logout,
            )
        }

        composable(Routes.MYPAGE) {
            MyPageScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenTask = { processId -> navController.navigateSafely(Routes.detail(processId)) },
                onLogout = logout,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStackSafely() },
                onLogout = logout,
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigateSafely(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.HOME,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "" }),
        ) { backStackEntry ->
            val completedProcessName by backStackEntry.savedStateHandle
                .getStateFlow<String?>("completed_process_name", null)
                .collectAsState()
            val initialTab = backStackEntry.arguments?.getString("tab").orEmpty()
            HomeScreen(
                onOpenTask = { processId -> navController.navigateSafely(Routes.detail(processId)) },
                onOpenOrder = { orderId -> navController.navigateSafely(Routes.assignDetail(orderId)) },
                onOpenCheckSheet = { orderId -> navController.navigateSafely(Routes.checksheet(orderId)) },
                onLogout = logout,
                completedProcessName = completedProcessName,
                onCompletedMessageShown = { backStackEntry.savedStateHandle["completed_process_name"] = null },
                initialTab = initialTab,
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("processId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val processId = backStackEntry.arguments?.getInt("processId") ?: 0
            TaskDetailScreen(
                processId = processId,
                onBack = { navController.popBackStackSafely() },
                onViewDrawing = { pid, title, orderId, poNumber ->
                    navController.navigateSafely(Routes.drawing(pid, title, orderId, poNumber))
                },
                onOpenCheckSheet = { orderId -> navController.navigateSafely(Routes.checksheet(orderId)) },
                onLogout = logout,
                onCompleted = { processName ->
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set("completed_process_name", processName)
                    navController.popBackStackSafely()
                },
                onSwitchToNextProcess = { nextProcessId ->
                    // 作業一覧には戻らず、この工程詳細を次工程の詳細に差し替える
                    // （popUpToでdetail画面の履歴を積み上げず1件に保つ）
                    navController.navigateSafely(Routes.detail(nextProcessId)) {
                        popUpTo(Routes.DETAIL) { inclusive = true }
                    }
                },
                // 加工工程パイプラインの他工程をタップしたとき。こちらは自分で辿った履歴として
                // 積み上げ、戻るボタンで元の工程詳細に戻れるようにする（差し替えはしない）
                onOpenProcess = { otherProcessId -> navController.navigateSafely(Routes.detail(otherProcessId)) },
            )
        }

        composable(
            route = Routes.CHECKSHEET,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
            CheckSheetScreen(
                orderId = orderId,
                onBack = { navController.popBackStackSafely() },
                onViewDrawing = { pid, title, oid, poNumber ->
                    navController.navigateSafely(Routes.drawing(pid, title, oid, poNumber))
                },
                onLogout = logout,
            )
        }

        composable(
            route = Routes.DRAWING,
            arguments = listOf(
                navArgument("processId") { type = NavType.IntType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("orderId") { type = NavType.IntType; defaultValue = 0 },
                navArgument("poNumber") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val processId = backStackEntry.arguments?.getInt("processId") ?: 0
            val title = backStackEntry.arguments?.getString("title").orEmpty()
            val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
            val poNumber = backStackEntry.arguments?.getString("poNumber").orEmpty()
            DrawingScreen(
                processId = processId,
                title = title,
                orderId = orderId,
                poNumber = poNumber.takeIf { it.isNotBlank() },
                onBack = { navController.popBackStackSafely() },
                onLogout = logout,
            )
        }

        composable(
            route = Routes.ASSIGN_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
            AssignmentDetailScreen(
                orderId = orderId,
                onBack = { navController.popBackStackSafely() },
                onOpenCheckSheet = { id -> navController.navigateSafely(Routes.checksheet(id)) },
                onLogout = logout,
            )
        }

        composable(Routes.PROCESS_ASSIGNMENTS) {
            ProcessAssignmentScreen(
                onBack = { navController.popBackStackSafely() },
                onLogout = logout,
            )
        }

        composable(Routes.INVENTORY) {
            InventoryScreen(
                onBack = { navController.popBackStackSafely() },
                onLogout = logout,
            )
        }

        composable(Routes.ORDER_INQUIRY) {
            OrderInquiryScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenCheckSheet = { orderId -> navController.navigateSafely(Routes.checksheet(orderId)) },
                onLogout = logout,
            )
        }

        composable(Routes.SCAN_DATA) {
            ScanDataScreen(
                onBack = { navController.popBackStackSafely() },
                onLogout = logout,
            )
        }

        composable(Routes.REPORT) {
            ReportScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenDetail = { reportId -> navController.navigateSafely(Routes.reportDetail(reportId)) },
                onLogout = logout,
            )
        }

        composable(Routes.REPORT_LIST) {
            ReportListScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenDetail = { reportId -> navController.navigateSafely(Routes.reportDetail(reportId)) },
                onLogout = logout,
            )
        }

        composable(
            route = Routes.REPORT_DETAIL,
            arguments = listOf(navArgument("reportId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getInt("reportId") ?: 0
            ReportDetailScreen(
                reportId = reportId,
                onBack = { navController.popBackStackSafely() },
                onLogout = logout,
            )
        }

        composable(Routes.ANNOUNCEMENT_LIST) {
            AnnouncementListScreen(
                onBack = { navController.popBackStackSafely() },
                onOpenDetail = { id -> navController.navigateSafely(Routes.announcementDetail(id)) },
                onLogout = logout,
            )
        }

        composable(
            route = Routes.ANNOUNCEMENT_DETAIL,
            arguments = listOf(navArgument("announcementId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val announcementId = backStackEntry.arguments?.getInt("announcementId") ?: 0
            AnnouncementDetailScreen(
                announcementId = announcementId,
                onBack = { navController.popBackStackSafely() },
                onOpenHistory = { navController.navigateSafely(Routes.ANNOUNCEMENT_LIST) },
                onLogout = logout,
            )
        }
    }
}
