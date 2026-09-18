package jp.co.nse.worker.ui.announcement

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.AnnouncementAttachmentDto
import jp.co.nse.worker.data.AnnouncementDto
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.ui.theme.Red500
import jp.co.nse.worker.util.DateUtil
import jp.co.nse.worker.util.PdfUtil
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch

/**
 * お知らせ詳細。本文・画像・PDF添付を表示する（一覧のカードは短いメッセージのみのため、
 * 詳しい内容はここで確認する）。過去のお知らせ一覧へは、末尾のリンクから移動できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementDetailScreen(
    announcementId: Int,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var announcement by remember { mutableStateOf<AnnouncementDto?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            when (val result = container.workerRepository.announcementDetail(announcementId)) {
                // 詳細を開くと、サーバー側でこのアカウントの既読として記録される
                // （Api\AnnouncementController::show）。ダッシュボードに戻った際は
                // 再読み込みでその結果（is_read）が反映される
                is ApiResult.Success -> announcement = result.data
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(announcementId) { load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("お知らせ") },
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
                loading && announcement == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && announcement == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { load() }) { Text("再読み込み") }
                }
                announcement != null -> AnnouncementDetailContent(
                    announcement = announcement!!,
                    onOpenHistory = { feedback(); onOpenHistory() },
                )
            }
        }
    }
}

@Composable
private fun AnnouncementDetailContent(announcement: AnnouncementDto, onOpenHistory: () -> Unit) {
    val urgent = announcement.is_urgent
    val fromLabel = announcement.creator_role_label?.let { "${it}からのお知らせ" } ?: "お知らせ"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (urgent) Color(0xFFFEF2F2) else Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Campaign,
                            contentDescription = null,
                            tint = if (urgent) Red500 else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            fromLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (urgent) Red500 else Color(0xFF9CA3AF),
                        )
                    }
                    Text(
                        DateUtil.dateTimeFull(announcement.created_at) ?: "",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    announcement.message,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (urgent) Color(0xFFB91C1C) else MaterialTheme.colorScheme.onSurface,
                )
                if (!announcement.body.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        announcement.body,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        announcement.attachments.forEach { attachment ->
            AttachmentView(attachment)
        }

        Card(
            onClick = onOpenHistory,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = Color(0xFF6B7280), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("過去のお知らせ一覧を見る", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF9CA3AF))
            }
        }
    }
}

/**
 * PDFはブラウザで表示すると閲覧しづらいため、端末にPDFビューワーアプリがあれば
 * そちらで開く（MIMEタイプを明示してACTION_VIEWすることで、ブラウザではなく
 * ビューワーアプリの候補が優先される）。見つからない場合のみブラウザにフォールバックする。
 */
private fun openPdf(context: android.content.Context, url: String, fallback: () -> Unit) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(android.net.Uri.parse(url), "application/pdf")
        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (e: android.content.ActivityNotFoundException) {
        fallback()
    }
}

@Composable
private fun AttachmentView(attachment: AnnouncementAttachmentDto) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    if (attachment.isImage) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = attachment.url,
                contentDescription = attachment.original_filename,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { runCatching { uriHandler.openUri(attachment.url) } },
            )
        }
    } else {
        PdfAttachmentView(attachment)
    }
}

/**
 * PDF添付は外部ビューワーを起動するのではなく、ページを画像化してこの画面内に
 * そのまま並べて表示する（[PdfUtil]参照）。取得・描画に失敗した場合のみ、
 * 従来通り外部アプリ／ブラウザで開くボタンにフォールバックする。
 */
@Composable
private fun PdfAttachmentView(attachment: AnnouncementAttachmentDto) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    var pages by remember(attachment.url) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember(attachment.url) { mutableStateOf(true) }
    var failed by remember(attachment.url) { mutableStateOf(false) }

    DisposableEffect(attachment.url) {
        onDispose { pages.forEach { it.recycle() } }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    attachment.original_filename,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val targetWidthPx = with(density) { maxWidth.roundToPx() }

                LaunchedEffect(attachment.url, targetWidthPx) {
                    if (targetWidthPx <= 0) return@LaunchedEffect
                    loading = true
                    failed = false
                    runCatching { PdfUtil.renderPages(context, attachment.url, targetWidthPx) }
                        .onSuccess { pages = it }
                        .onFailure { failed = true }
                    loading = false
                }

                when {
                    loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    failed || pages.isEmpty() -> Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("PDFを表示できませんでした", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { openPdf(context, attachment.url) { runCatching { uriHandler.openUri(attachment.url) } } },
                        ) { Text("外部アプリで開く") }
                    }
                    else -> Column {
                        pages.forEachIndexed { index, bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "${attachment.original_filename} ${index + 1}ページ目",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (index != pages.lastIndex) HorizontalDivider(color = Color(0xFFE5E7EB))
                        }
                    }
                }
            }
        }
    }
}
