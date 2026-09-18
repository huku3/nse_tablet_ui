package jp.co.nse.worker.ui.scandata

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.InspectionCandidateOrderDto
import jp.co.nse.worker.data.ScanDataFileDto
import jp.co.nse.worker.data.WorkerRepository
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.scan.startBarcodeScan
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ScanDataViewModel(private val repo: WorkerRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var files by mutableStateOf<List<ScanDataFileDto>>(emptyList())
        private set
    var busyFilename by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    var messageIsError by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            when (val res = repo.scanDataFiles()) {
                is ApiResult.Success -> files = res.data
                is ApiResult.Failure -> error = res.message
            }
            loading = false
        }
    }

    fun delete(filename: String) {
        viewModelScope.launch {
            busyFilename = filename
            when (val res = repo.deleteScanData(filename)) {
                is ApiResult.Success -> {
                    files = files.filterNot { it.name == filename }
                    message = "「$filename」を削除しました。"
                    messageIsError = false
                }
                is ApiResult.Failure -> {
                    message = res.message
                    messageIsError = true
                }
            }
            busyFilename = null
        }
    }

    fun attachInspection(filename: String, orderId: Int, onDone: (success: Boolean) -> Unit) {
        viewModelScope.launch {
            busyFilename = filename
            when (val res = repo.attachScanDataInspection(filename, orderId)) {
                is ApiResult.Success -> {
                    message = res.data
                    messageIsError = false
                    onDone(true)
                }
                is ApiResult.Failure -> {
                    message = res.message
                    messageIsError = true
                    onDone(false)
                }
            }
            busyFilename = null
        }
    }
}

/**
 * スキャンデータ：コピー機で読み取ったPDF（Web管理画面「スキャンデータ」と同じデータ）を
 * タブレットから閲覧・向き変更（保存）・削除・検査記録用図面への取り込みができる画面。
 * ホーム画面の下部タブではなく、ヘッダーのメニューから開く単独画面（在庫画面と同じ位置付け）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDataScreen(onBack: () -> Unit, onLogout: () -> Unit = {}) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: ScanDataViewModel = viewModel(
        factory = viewModelFactory { initializer { ScanDataViewModel(container.workerRepository) } },
    )
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFile by remember { mutableStateOf<ScanDataFileDto?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var attachTarget by remember { mutableStateOf<String?>(null) }
    var previewReloadTick by remember { mutableStateOf(0) }
    var rotating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.message = null
        }
    }

    fun onRotateClick() {
        val filename = selectedFile?.name ?: return
        feedback()
        scope.launch {
            rotating = true
            when (val res = container.workerRepository.rotateScanData(filename)) {
                is ApiResult.Success -> previewReloadTick++
                is ApiResult.Failure -> snackbarHostState.showSnackbar(res.message)
            }
            rotating = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val file = selectedFile
                    if (file != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    file.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(
                                    DateUtil.dateTimeFull(file.modified_at) ?: file.modified_at,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { onRotateClick() }, enabled = !rotating, modifier = Modifier.size(40.dp)) {
                                if (rotating) {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.RotateRight,
                                        contentDescription = "向きを変える",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        HeaderTitle("スキャンデータ")
                    }
                },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                feedback()
                                if (selectedFile != null) selectedFile = null else onBack()
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        HeaderLogo()
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    HeaderOverflowMenu(showScanData = false)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            val file = selectedFile
            if (file != null) {
                ScanDataPreview(filename = file.name, reloadTick = previewReloadTick)
            } else {
                when {
                    vm.loading && vm.files.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                    vm.error != null && vm.files.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { feedback(); vm.load() }) { Text("再読み込み") }
                    }
                    vm.files.isEmpty() -> Text(
                        "スキャンデータはありません",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Bold,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.files, key = { it.name }) { file ->
                            ScanDataFileRow(
                                file = file,
                                busy = vm.busyFilename == file.name,
                                onView = { feedback(); selectedFile = file; previewReloadTick = 0 },
                                onAttachInspection = { feedback(); attachTarget = file.name },
                                onDelete = { feedback(); deleteTarget = file.name },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { filename ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("削除の確認", fontWeight = FontWeight.ExtraBold) },
            text = { Text("「$filename」を削除します。元に戻せません。よろしいですか？") },
            confirmButton = {
                Button(
                    onClick = { vm.delete(filename); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Red500),
                ) { Text("削除する", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }

    attachTarget?.let { filename ->
        AttachInspectionDialog(
            filename = filename,
            busy = vm.busyFilename == filename,
            onDismiss = { attachTarget = null },
            onConfirm = { orderId ->
                vm.attachInspection(filename, orderId) { success -> if (success) attachTarget = null }
            },
        )
    }
}

@Composable
private fun ScanDataFileRow(
    file: ScanDataFileDto,
    busy: Boolean,
    onView: () -> Unit,
    onAttachInspection: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Row {
                        Text(formatFileSize(file.size), fontSize = 12.sp, color = Color(0xFF6B7280))
                        Text(" ・ ", fontSize = 12.sp, color = Color(0xFF6B7280))
                        Text(DateUtil.dateTimeFull(file.modified_at) ?: file.modified_at, fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                }
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onView, enabled = !busy) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("表示")
                }
                OutlinedButton(onClick = onAttachInspection, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("検査用図面登録")
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("削除")
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1fKB".format(bytes / 1024.0)
    else -> "${bytes}B"
}

/**
 * 検査記録用図面として取り込む対象の受注を選ぶダイアログ。最終検査が完了していて、
 * まだ検査記録用図面が取り込まれていない受注を候補として最初から一覧表示し、
 * 客先名・品番・発注番号等での絞り込み（入力 or バーコードスキャン）もできる。
 */
@Composable
private fun AttachInspectionDialog(
    filename: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (orderId: Int) -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var candidates by remember { mutableStateOf<List<InspectionCandidateOrderDto>>(emptyList()) }
    var selected by remember { mutableStateOf<InspectionCandidateOrderDto?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        when (val res = container.workerRepository.inspectionDrawingCandidates()) {
            is ApiResult.Success -> candidates = res.data
            is ApiResult.Failure -> loadError = res.message
        }
        loading = false
    }

    fun onScan() {
        startBarcodeScan(
            context = context,
            onResult = { code -> query = code },
            onError = { },
        )
    }

    val filtered = remember(candidates, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            candidates
        } else {
            candidates.filter { order ->
                listOfNotNull(
                    "${order.id}",
                    order.customer_name,
                    order.part_number,
                    order.part_name,
                    order.po_number,
                    order.customer_order_number,
                ).any { it.contains(q, ignoreCase = true) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("検査用図面登録", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "「$filename」を取り込む受注を選んでください。最終検査が完了していて、まだ検査記録用図面が登録されていない受注です。",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("客先名・品番・発注番号で絞り込み") },
                        singleLine = true,
                    )
                    IconButton(
                        onClick = { onScan() },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "バーコードをスキャン", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    loadError != null -> Text(loadError ?: "", color = Red500, fontSize = 13.sp)
                    filtered.isEmpty() -> Text(
                        if (candidates.isEmpty()) {
                            "取り込み候補の受注（最終検査完了・図面未登録）がありません。"
                        } else {
                            "該当する受注が見つかりませんでした。"
                        },
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filtered, key = { it.id }) { order ->
                            val isSelected = selected?.id == order.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFF9FAFB))
                                    .clickable { selected = order }
                                    .padding(10.dp),
                            ) {
                                Column {
                                    Text(
                                        "No.${order.id}　${order.customer_name ?: "—"}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                    Text(order.part_name ?: "—", fontSize = 13.sp, color = Color(0xFF6B7280))
                                    order.inspected_at?.let {
                                        Text(
                                            "最終検査完了: ${DateUtil.dateTimeFull(it) ?: it}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF9CA3AF),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val target = selected
            Button(
                onClick = { target?.let { onConfirm(it.id) } },
                enabled = target != null && !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("取り込む", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("キャンセル") }
        },
    )
}

private sealed interface PreviewState {
    data object Loading : PreviewState
    data class Loaded(val source: ScanDataPdfSource) : PreviewState
    data class Error(val message: String) : PreviewState
}

/**
 * 開いたPdfRendererを保持し、ページを1枚ずつ必要な時にだけ描画する。コピー機のスキャンデータは
 * 図面と違い数十ページに及ぶこともあるため、全ページを一度にBitmap化すると端末のメモリが
 * 足りずアプリごと落ちることがある（実際に発生した不具合）。表示中のページだけ描画することで防ぐ。
 * 呼び出し側は使い終わったら必ず[close]すること。
 */
private class ScanDataPdfSource(file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    val pageCount: Int get() = renderer.pageCount

    /** PdfRendererは同時に複数ページを開けないため、Mutexで1ページずつ順番に描画する */
    suspend fun renderPage(index: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val targetMaxPx = 1600
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val width = minOf(targetMaxPx, maxOf(page.width * 2, 1080))
                    val height = (width * ratio).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

@Composable
private fun ScanDataPreview(filename: String, reloadTick: Int) {
    val context = LocalContext.current
    val container = context.appContainer
    var state by remember(filename, reloadTick) { mutableStateOf<PreviewState>(PreviewState.Loading) }

    LaunchedEffect(filename, reloadTick) {
        state = PreviewState.Loading
        val file = File(context.cacheDir, "scan_data_preview.pdf")
        when (val res = container.workerRepository.downloadScanData(filename, file)) {
            is ApiResult.Success -> {
                val source = withContext(Dispatchers.IO) { runCatching { ScanDataPdfSource(res.data) }.getOrNull() }
                state = if (source == null || source.pageCount == 0) {
                    PreviewState.Error("PDFを表示できませんでした。")
                } else {
                    PreviewState.Loaded(source)
                }
            }
            is ApiResult.Failure -> state = PreviewState.Error(res.message)
        }
    }

    DisposableEffect(filename, reloadTick) {
        onDispose {
            (state as? PreviewState.Loaded)?.source?.close()
        }
    }

    when (val s = state) {
        is PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is PreviewState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Red500)
                Spacer(Modifier.height(8.dp))
                Text(s.message, color = Red500, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            }
        }
        is PreviewState.Loaded -> {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { s.source.pageCount }
            Column(Modifier.fillMaxSize()) {
                if (s.source.pageCount > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / ${s.source.pageCount} ページ",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                    )
                }
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { index ->
                    ScanDataPageImage(source = s.source, index = index)
                }
            }
        }
    }
}

/** ページ画像。ピンチで拡大縮小・パンできる（ダブルタップで等倍にリセット） */
@Composable
private fun ScanDataPageImage(source: ScanDataPdfSource, index: Int) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = source, key2 = index) {
        value = source.renderPage(index)
    }
    val bitmap = bitmapState.value

    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    if (bitmap != null) {
        var scale by remember(bitmap) { mutableFloatStateOf(1f) }
        var offsetX by remember(bitmap) { mutableFloatStateOf(0f) }
        var offsetY by remember(bitmap) { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .clipToBounds()
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
            if (scale != 1f) {
                TextButton(
                    onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Text("リセット", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
