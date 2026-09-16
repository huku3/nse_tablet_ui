package jp.co.nse.worker.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import jp.co.nse.worker.data.ImageAttachment
import jp.co.nse.worker.util.downsampleJpegBytes
import jp.co.nse.worker.util.rememberClickFeedback

/**
 * アプリ内蔵カメラで写真を撮影するダイアログ（全画面表示）。
 * 外部のカメラアプリに頼らずCameraXで直接プレビュー・撮影するため、カメラアプリ自体が
 * 入っていない端末（バーコードスキャンはGoogle Play開発者サービスの専用UIで動いているだけで、
 * 一般的な「カメラアプリ」は入っていない業務用タブレット等）でも動作する。
 */
@Composable
fun CameraCaptureDialog(
    onCaptured: (Bitmap, ImageAttachment) -> Unit,
    onDismiss: () -> Unit,
    filename: String = "photo.jpg",
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val feedback = rememberClickFeedback()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) permissionDenied = true
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) requestPermission.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                hasPermission -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener(
                                {
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val capture = ImageCapture.Builder().build()
                                        imageCapture = capture
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            capture,
                                        )
                                    } catch (e: Exception) {
                                        error = "カメラを起動できませんでした。"
                                    }
                                },
                                ContextCompat.getMainExecutor(ctx),
                            )
                            previewView
                        },
                    )

                    IconButton(
                        onClick = { feedback(); onDismiss() },
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = Color.White)
                    }

                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        error?.let {
                            Text(it, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        if (capturing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
                        } else {
                            IconButton(
                                onClick = {
                                    feedback()
                                    error = null
                                    val capture = imageCapture
                                    if (capture == null) {
                                        error = "カメラの準備ができていません。少し待ってからお試しください。"
                                        return@IconButton
                                    }
                                    capturing = true
                                    capture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                                val buffer = image.planes[0].buffer
                                                val bytes = ByteArray(buffer.remaining())
                                                buffer.get(bytes)
                                                image.close()
                                                capturing = false
                                                val loaded = downsampleJpegBytes(bytes, filename)
                                                if (loaded != null) {
                                                    onCaptured(loaded.first, loaded.second)
                                                } else {
                                                    error = "写真の処理に失敗しました。"
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                capturing = false
                                                error = "撮影に失敗しました。"
                                            }
                                        },
                                    )
                                },
                                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White),
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = "撮影",
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (permissionDenied) {
                                "カメラの権限が許可されていません。設定アプリから権限を許可してください。"
                            } else {
                                "カメラを使用する権限を確認しています…"
                            },
                            color = Color.White,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (permissionDenied) {
                            Button(onClick = { feedback(); requestPermission.launch(Manifest.permission.CAMERA) }) {
                                Text("もう一度許可を求める")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        TextButton(onClick = { feedback(); onDismiss() }) {
                            Text("キャンセル", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
