package jp.co.nse.worker.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import jp.co.nse.worker.appContainer
import jp.co.nse.worker.data.ApiResult
import jp.co.nse.worker.data.AppUpdateDto
import jp.co.nse.worker.util.rememberClickFeedback
import kotlinx.coroutines.launch
import java.io.File

private enum class Phase { Confirm, NeedPermission, Downloading, Error }

/**
 * 起動時に最新版を確認し、端末より新しければ更新ダイアログを表示する。
 * AppNav と同階層に置くオーバーレイ（普段は何も描画しない）。
 */
@Composable
fun UpdateChecker() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val feedback = rememberClickFeedback()

    var update by remember { mutableStateOf<AppUpdateDto?>(null) }
    var phase by remember { mutableStateOf(Phase.Confirm) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val res = container.updateRepository.checkLatest()) {
            is ApiResult.Success -> {
                val latest = res.data
                if (latest.version_code > currentVersionCode(context)) {
                    update = latest
                    phase = Phase.Confirm
                }
            }
            is ApiResult.Failure -> { /* 確認失敗時は何もしない（通常利用を妨げない） */ }
        }
    }

    val u = update ?: return
    if (dismissed) return

    fun startDownload() {
        if (!canInstall(context)) {
            phase = Phase.NeedPermission
            return
        }
        phase = Phase.Downloading
        progress = 0f
        scope.launch {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(dir, "nse-worker.apk")
            when (val res = container.updateRepository.downloadApk(apk) { progress = it }) {
                is ApiResult.Success -> {
                    installApk(context, res.data)
                    // インストーラ起動後はダイアログを閉じる
                    dismissed = true
                }
                is ApiResult.Failure -> {
                    errorMsg = res.message
                    phase = Phase.Error
                }
            }
        }
    }

    when (phase) {
        Phase.Confirm -> AlertDialog(
            onDismissRequest = { if (!u.mandatory) dismissed = true },
            title = { Text("アップデートがあります", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("新しいバージョン ${u.version_name} が利用できます。")
                    if (u.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(u.notes)
                    }
                    if (u.mandatory) {
                        Spacer(Modifier.height(8.dp))
                        Text("このアップデートは必須です。", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { Button(onClick = { feedback(); startDownload() }) { Text("更新する") } },
            dismissButton = {
                if (!u.mandatory) TextButton(onClick = { feedback(); dismissed = true }) { Text("あとで") }
            },
        )

        Phase.NeedPermission -> AlertDialog(
            onDismissRequest = { if (!u.mandatory) dismissed = true },
            title = { Text("インストールの許可が必要です", fontWeight = FontWeight.Bold) },
            text = { Text("このアプリからのインストールを許可してください。設定画面を開いて許可後、もう一度「更新する」を押してください。") },
            confirmButton = {
                Button(onClick = {
                    feedback()
                    openInstallPermissionSettings(context)
                    phase = Phase.Confirm
                }) { Text("設定を開く") }
            },
            dismissButton = {
                if (!u.mandatory) TextButton(onClick = { feedback(); dismissed = true }) { Text("あとで") }
            },
        )

        Phase.Downloading -> AlertDialog(
            onDismissRequest = { /* ダウンロード中は閉じない */ },
            title = { Text("ダウンロード中…", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(progress * 100).toInt()}%")
                }
            },
            confirmButton = {},
        )

        Phase.Error -> AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("更新に失敗しました", fontWeight = FontWeight.Bold) },
            text = { Text(errorMsg ?: "もう一度お試しください。") },
            confirmButton = { Button(onClick = { feedback(); phase = Phase.Confirm }) { Text("再試行") } },
            dismissButton = {
                if (!u.mandatory) TextButton(onClick = { feedback(); dismissed = true }) { Text("閉じる") }
            },
        )
    }
}

/** 端末にインストール済みのversionCode。取得失敗時は更新を促さないよう最大値を返す */
private fun currentVersionCode(context: Context): Long = try {
    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode
    else @Suppress("DEPRECATION") pi.versionCode.toLong()
} catch (e: Exception) {
    Long.MAX_VALUE
}

private fun canInstall(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun openInstallPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun installApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
