package jp.co.nse.worker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import jp.co.nse.worker.data.ImageAttachment
import java.io.ByteArrayOutputStream

/**
 * 画像Uri（ギャラリー選択・カメラ撮影のいずれも）を、プレビュー用Bitmapとアップロード用の
 * 縮小JPEGに変換する。端末のカメラ写真がそのまま数MB〜十数MBになることがあるため、
 * 長辺[maxDimension]pxまでに落として送信量を抑える。
 */
fun decodeImageAttachment(
    context: Context,
    uri: Uri,
    filename: String,
    maxDimension: Int = 1280,
    quality: Int = 80,
): Pair<Bitmap, ImageAttachment>? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // inJustDecodeBounds=trueのdecodeStream()は常にnullを返す仕様（bounds.outWidth/outHeightに
    // サイズを書き込むだけ）。この戻り値をエラー扱いにすると、毎回ここで抜けてしまい
    // どんな画像も一切プレビュー・添付できなくなる
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null

    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return bitmap to ImageAttachment(bytes = output.toByteArray(), filename = filename, mimeType = "image/jpeg")
}

/**
 * CameraX（ImageCapture.OnImageCapturedCallback）から得たJPEGバイト列を、
 * プレビュー用Bitmapとアップロード用の縮小JPEGに変換する。
 * 外部カメラアプリ経由（[decodeImageAttachment]）と違いUri・一時ファイルを介さないため、
 * FileProviderの設定に依存しない。
 */
fun downsampleJpegBytes(
    bytes: ByteArray,
    filename: String,
    maxDimension: Int = 1280,
    quality: Int = 80,
): Pair<Bitmap, ImageAttachment>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return null

    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return bitmap to ImageAttachment(bytes = output.toByteArray(), filename = filename, mimeType = "image/jpeg")
}
