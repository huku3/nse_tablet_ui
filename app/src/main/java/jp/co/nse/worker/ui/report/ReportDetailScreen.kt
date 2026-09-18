package jp.co.nse.worker.ui.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.ReportDto
import jp.co.nse.worker.data.ReportStatus
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 報告詳細（管理者向け）。スクリーンショットが添付されている場合は画像として表示する */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: Int,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<ReportDto?>(null) }
    var screenshot by remember { mutableStateOf<Bitmap?>(null) }
    var loadingScreenshot by remember { mutableStateOf(false) }
    var screenshotError by remember { mutableStateOf<String?>(null) }
    var statusUpdating by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    fun loadScreenshot(id: Int) {
        loadingScreenshot = true
        screenshotError = null
        scope.launch {
            when (val result = container.managerRepository.reportScreenshotBytes(id)) {
                is ApiResult.Success -> {
                    val decoded = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(result.data, 0, result.data.size)
                    }
                    if (decoded != null) {
                        screenshot = decoded
                    } else {
                        screenshotError = "画像を読み込めませんでした（データが破損している可能性があります）"
                    }
                }
                is ApiResult.Failure -> screenshotError = result.message
            }
            loadingScreenshot = false
        }
    }

    fun load() {
        scope.launch {
            loading = true
            error = null
            when (val result = container.managerRepository.reportDetail(reportId)) {
                is ApiResult.Success -> {
                    report = result.data
                    if (result.data.has_screenshot) loadScreenshot(reportId)
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(reportId) { load() }

    fun changeStatus(status: ReportStatus) {
        if (statusUpdating) return
        feedback()
        scope.launch {
            statusUpdating = true
            when (val result = container.managerRepository.updateReportStatus(reportId, status)) {
                is ApiResult.Success -> report = report?.copy(status = status.apiValue, status_label = status.label)
                is ApiResult.Failure -> error = result.message
            }
            statusUpdating = false
        }
    }

    fun deleteReport() {
        if (deleting) return
        scope.launch {
            deleting = true
            when (val result = container.managerRepository.deleteReport(reportId)) {
                is ApiResult.Success -> { showDeleteConfirm = false; onBack() }
                is ApiResult.Failure -> { showDeleteConfirm = false; error = result.message }
            }
            deleting = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeaderTitle("報告詳細")
                        if (report != null) {
                            IconButton(onClick = { feedback(); showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { feedback(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        HeaderLogo()
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    HeaderOverflowMenu()
                    IconButton(onClick = { feedback(); load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && report == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { load() }, modifier = Modifier.padding(top = 12.dp)) { Text("再読み込み") }
                }
                report != null -> {
                    val r = report!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryBadge(r.category_label ?: r.category)
                        }
                        Text(
                            r.title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        Text(
                            "${r.reporter_name ?: "不明"} ・ ${r.created_at ?: ""}",
                            color = Color(0xFF6B7280),
                            fontSize = 13.sp,
                        )

                        Text(
                            "対応状況",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportStatus.entries.forEach { option ->
                                FilterChip(
                                    selected = r.status == option.apiValue,
                                    onClick = { changeStatus(option) },
                                    enabled = !statusUpdating,
                                    label = { Text(option.label) },
                                )
                            }
                        }

                        Text(
                            "内容",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                        Text(r.body ?: "", fontSize = 14.sp)

                        if (r.has_screenshot) {
                            Text(
                                "スクリーンショット",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                            )
                            when {
                                screenshot != null -> Image(
                                    bitmap = screenshot!!.asImageBitmap(),
                                    contentDescription = "報告に添付されたスクリーンショット",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                                loadingScreenshot -> Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                                else -> Column {
                                    Text(
                                        screenshotError ?: "画像を取得できませんでした",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp,
                                    )
                                    TextButton(onClick = { loadScreenshot(reportId) }) { Text("再試行") }
                                }
                            }
                        }

                        Text(
                            "端末情報",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF9FAFB))
                                .padding(12.dp),
                        ) {
                            InfoLine("画面", r.screen_name)
                            InfoLine("アプリバージョン", r.app_version)
                            InfoLine("OS", r.os_version)
                            InfoLine("機種", r.device_model)
                        }
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { if (!deleting) showDeleteConfirm = false },
                title = { Text("削除の確認", fontWeight = FontWeight.ExtraBold) },
                text = { Text("この報告を削除します。元に戻せません。よろしいですか？") },
                confirmButton = {
                    Button(
                        onClick = { deleteReport() },
                        enabled = !deleting,
                        colors = ButtonDefaults.buttonColors(containerColor = Red500),
                    ) { Text("削除する", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }, enabled = !deleting) { Text("キャンセル") }
                },
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
        Text(value, color = Color(0xFF374151), fontSize = 12.sp)
    }
}

@Composable
private fun CategoryBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE5E7EB))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = Color(0xFF374151), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
