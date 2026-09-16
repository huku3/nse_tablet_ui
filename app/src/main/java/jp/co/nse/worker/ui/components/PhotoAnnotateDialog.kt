package jp.co.nse.worker.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.co.nse.worker.data.ImageAttachment
import jp.co.nse.worker.util.rememberClickFeedback
import java.io.ByteArrayOutputStream

private const val StrokeWidthPx = 10f

/**
 * 撮影した写真に、指で丸・矢印などを描き込めるマーキング画面（全画面）。
 * 「この内容で使う」を押すと、描き込みを実際のBitmapに焼き込んでからJPEGへ再圧縮して返す。
 * 表示ボックスをBitmapと同じアスペクト比に固定しているため、レターボックス（余白）が出ず、
 * 表示座標→Bitmapのピクセル座標への変換は単純な倍率換算だけで済む。
 */
@Composable
fun PhotoAnnotateDialog(
    bitmap: Bitmap,
    filename: String,
    onConfirm: (Bitmap, ImageAttachment) -> Unit,
    onDismiss: () -> Unit,
) {
    val feedback = rememberClickFeedback()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    fun bakeAndConfirm() {
        val size = boxSize
        val result = if (strokes.isEmpty() || size.width == 0) {
            bitmap
        } else {
            val scale = bitmap.width / size.width.toFloat()
            val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(mutable)
            val paint = Paint().apply {
                color = android.graphics.Color.RED
                style = Paint.Style.STROKE
                strokeWidth = StrokeWidthPx * scale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            strokes.forEach { stroke ->
                if (stroke.size > 1) {
                    val path = android.graphics.Path()
                    path.moveTo(stroke.first().x * scale, stroke.first().y * scale)
                    stroke.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
                    canvas.drawPath(path, paint)
                }
            }
            mutable
        }
        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 85, output)
        onConfirm(result, ImageAttachment(bytes = output.toByteArray(), filename = filename, mimeType = "image/jpeg"))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { feedback(); onDismiss() }) {
                        Icon(Icons.Filled.Close, contentDescription = "閉じる", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("不良箇所に印をつけられます（任意）", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .align(Alignment.CenterHorizontally)
                        .onSizeChanged { boxSize = it },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "撮影した写真",
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInputDrag(
                                onStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { offset -> currentStroke = currentStroke + offset },
                                onEnd = {
                                    if (currentStroke.size > 1) strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                },
                            ),
                    ) {
                        (strokes + listOf(currentStroke)).forEach { stroke ->
                            if (stroke.size > 1) {
                                val path = Path()
                                path.moveTo(stroke.first().x, stroke.first().y)
                                stroke.drop(1).forEach { path.lineTo(it.x, it.y) }
                                drawPath(
                                    path,
                                    color = Color.Red,
                                    style = Stroke(width = StrokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { feedback(); if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("元に戻す", color = Color.White)
                    }
                    TextButton(onClick = { feedback(); strokes.clear() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("全消去", color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { feedback(); bakeAndConfirm() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA)),
                ) {
                    Text("この内容で使う", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** ドラッグ操作をシンプルなコールバックで受け取るための小さなヘルパー */
private fun Modifier.pointerInputDrag(
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset -> onStart(offset) },
            onDragEnd = { onEnd() },
            onDragCancel = { onEnd() },
            onDrag = { change, _ -> onDrag(change.position) },
        )
    },
)
