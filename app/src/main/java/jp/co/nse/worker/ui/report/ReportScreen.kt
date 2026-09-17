package jp.co.nse.worker.ui.report

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import jp.co.nse.worker.data.ReportCategory
import jp.co.nse.worker.data.ImageAttachment
import jp.co.nse.worker.ui.components.HeaderTitle
import jp.co.nse.worker.ui.components.HeaderLogo
import jp.co.nse.worker.ui.components.HeaderOverflowMenu
import jp.co.nse.worker.ui.components.HeaderUserLabel
import jp.co.nse.worker.ui.components.NotificationBell
import jp.co.nse.worker.ui.components.rememberCurrentUserName
import jp.co.nse.worker.util.decodeImageAttachment
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 不具合・UI改善要望をその場で管理者へ送れる画面。
 * 機種・OS・アプリのバージョンは自動で付与するため、作業者は内容だけ入力すればよい。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()
    val userName = rememberCurrentUserName()

    var category by remember { mutableStateOf(ReportCategory.UI_IMPROVEMENT) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var screenshotPreview by remember { mutableStateOf<Bitmap?>(null) }
    var screenshotUpload by remember { mutableStateOf<ImageAttachment?>(null) }
    var loadingImage by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    // 送信後の自動画面遷移と、ヘッダーの戻るボタンが競合してpopBackStack()が二重に呼ばれると、
    // 画面遷移アニメーション中にナビゲーションスタックが壊れて真っ白なまま操作不能になることがある。
    // 呼び出し元を問わず一度しか戻らないようにする
    var backTriggered by remember { mutableStateOf(false) }
    fun goBack() {
        if (backTriggered) return
        backTriggered = true
        onBack()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingImage = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { decodeImageAttachment(context, uri, "screenshot.jpg") }
            screenshotPreview = loaded?.first
            screenshotUpload = loaded?.second
            loadingImage = false
        }
    }

    fun submit() {
        if (sending || title.isBlank() || body.isBlank()) return
        showConfirm = false
        feedback()
        scope.launch {
            sending = true
            error = null
            val result = container.workerRepository.submitReport(
                category = category,
                title = title.trim(),
                body = body.trim(),
                screenName = null,
                screenshot = screenshotUpload,
            )
            when (result) {
                is ApiResult.Success -> sent = true
                is ApiResult.Failure -> error = result.message
            }
            sending = false
        }
    }

    if (sent) {
        LaunchedEffect(Unit) {
            delay(1200)
            goBack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { HeaderTitle("不具合・要望の報告") },
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { feedback(); goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        HeaderLogo()
                    }
                },
                actions = {
                    HeaderUserLabel(userName)
                    NotificationBell()
                    HeaderOverflowMenu(showReport = false)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            if (sent) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("送信しました。ご協力ありがとうございます。", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                return@Column
            }

            Text(
                "画面の使いにくい点や不具合、気になったことを自由に書いてください。機種・アプリのバージョンは自動で送信されます。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(bottom = 20.dp),
            )

            Text("種類", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                ReportCategory.entries.forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { feedback(); category = option },
                        label = { Text(option.label) },
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タイトル") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("内容") },
                placeholder = { Text("例：受注一覧の文字が小さくて見づらい／作業完了ボタンを押すとエラーになる、など") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )

            Text("スクリーンショット（任意）", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (screenshotPreview != null) {
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    Image(
                        bitmap = screenshotPreview!!.asImageBitmap(),
                        contentDescription = "添付したスクリーンショット",
                        modifier = Modifier
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    IconButton(
                        onClick = {
                            feedback()
                            screenshotPreview = null
                            screenshotUpload = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC000000)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "スクリーンショットを削除", tint = Color.White)
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    feedback()
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !loadingImage,
                modifier = Modifier.padding(bottom = 20.dp),
            ) {
                if (loadingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (screenshotPreview == null) "画像を選ぶ" else "別の画像に変える")
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp))
            }

            Button(
                onClick = { feedback(); showConfirm = true },
                enabled = !sending && title.isNotBlank() && body.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text("管理者に送信", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!sending) showConfirm = false },
            title = { Text("この内容で送信しますか？", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("種類: ${category.label}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(body, fontSize = 13.sp, color = Color(0xFF6B7280))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (screenshotPreview != null) "スクリーンショット: 添付あり" else "スクリーンショット: なし",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                    )
                }
            },
            confirmButton = {
                Button(onClick = { submit() }, enabled = !sending) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("送信する", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }, enabled = !sending) {
                    Text("戻る", color = Color(0xFF6B7280))
                }
            },
        )
    }
}

