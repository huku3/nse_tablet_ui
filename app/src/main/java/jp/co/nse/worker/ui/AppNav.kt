package jp.co.nse.worker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.ui.assignment.AssignmentDetailScreen
import jp.co.nse.worker.ui.checksheet.CheckSheetScreen
import jp.co.nse.worker.ui.dashboard.DashboardScreen
import jp.co.nse.worker.ui.drawing.DrawingScreen
import jp.co.nse.worker.ui.login.LoginScreen
import jp.co.nse.worker.ui.mypage.MyPageScreen
import jp.co.nse.worker.ui.processassignment.ProcessAssignmentScreen
import jp.co.nse.worker.ui.splash.SplashScreen
import jp.co.nse.worker.ui.taskdetail.TaskDetailScreen
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val MYPAGE = "mypage"
    const val LOGIN = "login"
    const val HOME = "home"
    const val DETAIL = "detail/{processId}"
    const val ASSIGN_DETAIL = "assign/{orderId}"
    const val PROCESS_ASSIGNMENTS = "process-assignments"
    const val DRAWING = "drawing/{processId}?title={title}&orderId={orderId}&poNumber={poNumber}"
    const val CHECKSHEET = "checksheet/{orderId}"

    fun detail(processId: Int) = "detail/$processId"
    fun assignDetail(orderId: Int) = "assign/$orderId"
    fun drawing(processId: Int, title: String?, orderId: Int, poNumber: String?) =
        "drawing/$processId?title=${Uri.encode(title ?: "")}" +
            "&orderId=$orderId&poNumber=${Uri.encode(poNumber ?: "")}"
    fun checksheet(orderId: Int) = "checksheet/$orderId"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()

    // 通知一覧のタップなど、NavControllerを直接持たないUIから工程詳細へ遷移できるようにする
    container.openTask = { processId -> navController.navigate(Routes.detail(processId)) }
    // どの画面のヘッダーからでもマイページへ遷移できるようにする
    container.openMyPage = { navController.navigate(Routes.MYPAGE) }
    // どの画面のヘッダーからでもダッシュボードへ遷移できるようにする
    container.openDashboard = { navController.navigate(Routes.DASHBOARD) }
    // 権限があるアカウントは、どの画面のヘッダーからでも担当工程マスタへ遷移できるようにする
    container.openProcessAssignments = { navController.navigate(Routes.PROCESS_ASSIGNMENTS) }

    val logout: () -> Unit = {
        container.notificationCenter.stopPolling()
        scope.launch {
            container.authRepository.logout()
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val loggedIn = container.authRepository.isLoggedIn()
                    if (loggedIn) container.notificationCenter.startPolling()
                    val target = if (loggedIn) Routes.HOME else Routes.LOGIN
                    navController.navigate(target) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onBack = { navController.popBackStack() },
                onContinue = {
                    container.notificationCenter.startPolling()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
                onLogout = logout,
            )
        }

        composable(Routes.MYPAGE) {
            MyPageScreen(
                onBack = { navController.popBackStack() },
                onOpenTask = { processId -> navController.navigate(Routes.detail(processId)) },
                onLogout = logout,
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) { backStackEntry ->
            val completedProcessName by backStackEntry.savedStateHandle
                .getStateFlow<String?>("completed_process_name", null)
                .collectAsState()
            HomeScreen(
                onOpenTask = { processId -> navController.navigate(Routes.detail(processId)) },
                onOpenOrder = { orderId -> navController.navigate(Routes.assignDetail(orderId)) },
                onOpenCheckSheet = { orderId -> navController.navigate(Routes.checksheet(orderId)) },
                onLogout = logout,
                completedProcessName = completedProcessName,
                onCompletedMessageShown = { backStackEntry.savedStateHandle["completed_process_name"] = null },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("processId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val processId = backStackEntry.arguments?.getInt("processId") ?: 0
            TaskDetailScreen(
                processId = processId,
                onBack = { navController.popBackStack() },
                onViewDrawing = { pid, title, orderId, poNumber ->
                    navController.navigate(Routes.drawing(pid, title, orderId, poNumber))
                },
                onOpenCheckSheet = { orderId -> navController.navigate(Routes.checksheet(orderId)) },
                onLogout = logout,
                onCompleted = { processName ->
                    navController.previousBackStackEntry?.savedStateHandle
                        ?.set("completed_process_name", processName)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.CHECKSHEET,
            arguments = listOf(navArgument("orderId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
            CheckSheetScreen(
                orderId = orderId,
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
                onOpenCheckSheet = { id -> navController.navigate(Routes.checksheet(id)) },
                onLogout = logout,
            )
        }

        composable(Routes.PROCESS_ASSIGNMENTS) {
            ProcessAssignmentScreen(
                onBack = { navController.popBackStack() },
                onLogout = logout,
            )
        }
    }
}
