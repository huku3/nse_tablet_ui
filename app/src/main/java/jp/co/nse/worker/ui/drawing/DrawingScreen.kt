package jp.co.nse.worker.ui.drawing

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.DashboardButton
import jp.co.nse.worker.ui.components.MyPageButton
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.ProcessAssignmentButton
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private sealed interface DrawingState {
    data object Loading : DrawingState
    data class Loaded(val pages: List<Bitmap>) : DrawingState
    data class Error(val message: String) : DrawingState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    processId: Int,
    title: String?,
    orderId: Int = 0,
    poNumber: String? = null,
    onBack: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val feedback = rememberClickFeedback()

    var state by remember { mutableStateOf<DrawingState>(DrawingState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    val userName = rememberCurrentUserName()

    LaunchedEffect(processId, reloadKey) {
        state = DrawingState.Loading
        val file = File(context.cacheDir, "drawing_$processId.pdf")
        when (val res = container.workerRepository.downloadDrawing(processId, file)) {
            is ApiResult.Success -> {
                val pages = withContext(Dispatchers.IO) { renderPdf(res.data) }
                state = if (pages.isEmpty()) {
                    DrawingState.Error("図面を表示できませんでした。")
                } else {
                    DrawingState.Loaded(pages)
                }
            }
            is ApiResult.Failure -> state = DrawingState.Error(res.message)
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    DrawingHeaderTitle(
                        title = title?.takeIf { it.isNotBlank() } ?: "図面",
                        orderId = orderId,
                        poNumber = poNumber,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { feedback(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    MyPageButton()
                    DashboardButton()
                    ProcessAssignmentButton()
                    IconButton(onClick = { feedback(); reloadKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "更新", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { feedback(); onLogout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "ログアウト", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF374151)),
        ) {
            when (val s = state) {
                is DrawingState.Loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = Color.White,
                )

                is DrawingState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { feedback(); reloadKey++ }) { Text("再読み込み") }
                }

                is DrawingState.Loaded -> PdfPager(s.pages)
            }
        }
    }
}

/** 図面画面のヘッダータイトル。品名の下に受注No・発注番号、さらにその下に本日の日付を表示する */
@Composable
private fun DrawingHeaderTitle(title: String, orderId: Int, poNumber: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
        if (orderId > 0) {
            Text(
                buildString {
                    append("受注No.$orderId")
                    poNumber?.takeIf { it.isNotBlank() }?.let { append(" ・ 発注 $it") }
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
            )
        }
        Text(
            DateUtil.shortLabel(LocalDate.now()),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
        )
    }
}

/** ピンチで拡大・縦スクロールで複数ページを閲覧できるビューア */
@Composable
private fun PdfPager(pages: List<Bitmap>) {
    val feedback = rememberClickFeedback()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    // 拡大時のみ横方向のパンを許可（等倍では中央固定）
                    offsetX = if (scale > 1f) offsetX + pan.x else 0f
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pages.forEach { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "図面ページ",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (scale != 1f || offsetX != 0f) {
            TextButton(
                onClick = { feedback(); scale = 1f; offsetX = 0f },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Text("リセット", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** PDFファイルを各ページのBitmapへ描画する。画面表示用に十分な解像度で書き出す */
private fun renderPdf(file: File): List<Bitmap> {
    if (!file.exists() || file.length() == 0L) return emptyList()
    val bitmaps = mutableListOf<Bitmap>()
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        // 横長図面でも文字が読めるよう、長辺2048pxを目安に描画する
        val targetMaxPx = 2048
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                val ratio = page.height.toFloat() / page.width.toFloat()
                val width = minOf(targetMaxPx, maxOf(page.width * 2, 1080))
                val height = (width * ratio).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
            }
        }
    } catch (_: Throwable) {
        return emptyList()
    } finally {
        renderer?.close()
        pfd?.close()
    }
    return bitmaps
}
