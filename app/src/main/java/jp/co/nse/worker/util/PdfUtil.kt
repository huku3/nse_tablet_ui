package jp.co.nse.worker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** PDF添付をお知らせ詳細画面にそのまま表示するためのダウンロード＋ページ描画ユーティリティ */
object PdfUtil {

    // 認証やAPIのベースURLが不要な公開ファイル（Storage::disk('public')）を素直に取得するだけなので、
    // アプリ本体のRetrofit/ApiServiceは使わず単独のクライアントで済ませる
    private val client = OkHttpClient()

    /**
     * URLからPDFをダウンロードし、各ページを[targetWidthPx]幅に合わせて画像化する。
     * ページ数分のビットマップを返す（呼び出し側で不要になったらrecycleすること）。
     */
    suspend fun renderPages(context: Context, url: String, targetWidthPx: Int): List<Bitmap> =
        withContext(Dispatchers.IO) {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body ?: throw IOException("空のレスポンスです")
            if (!response.isSuccessful) throw IOException("PDFの取得に失敗しました（${response.code}）")

            val tempFile = File.createTempFile("announcement_", ".pdf", context.cacheDir)
            try {
                tempFile.outputStream().use { out -> body.byteStream().copyTo(out) }

                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        (0 until renderer.pageCount).map { index ->
                            renderer.openPage(index).use { page ->
                                val ratio = page.height.toFloat() / page.width.toFloat()
                                val height = (targetWidthPx * ratio).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888).apply {
                                    eraseColor(Color.WHITE)
                                    page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }
            } finally {
                tempFile.delete()
            }
        }
}
