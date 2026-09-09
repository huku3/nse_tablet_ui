package jp.co.nse.worker.ui.scan

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google Play Services のコードスキャナを起動する。
 * カメラ権限不要・専用UIで、読み取った文字列を [onResult] に返す。
 * ユーザーがキャンセルした場合は何も呼ばれない。
 *
 * スキャナー本体はAPKに同梱されておらず、Google Play開発者サービス側から
 * 動的配信される「モジュール」なので、いきなり startScan() を呼ぶと未取得の端末では
 * 一瞬画面が動いて即失敗する。事前に明示的な取得（ModuleInstallClient）を挟むことで、
 * 取得できるなら成功させ、できないなら理由の分かるエラーを返す。
 */
fun startBarcodeScan(
    context: Context,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()
    val scanner = GmsBarcodeScanning.getClient(context, options)

    fun doScan() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue?.trim().orEmpty()
                if (value.isEmpty()) {
                    onError("バーコードを読み取れませんでした。")
                } else {
                    onResult(value)
                }
            }
            .addOnCanceledListener { /* ユーザーがキャンセル: 何もしない */ }
            .addOnFailureListener { e ->
                onError(e.message ?: "スキャンを起動できませんでした。")
            }
    }

    val moduleInstallClient = ModuleInstall.getClient(context)
    moduleInstallClient.areModulesAvailable(scanner)
        .addOnSuccessListener { response ->
            if (response.areModulesAvailable()) {
                doScan()
            } else {
                val request = ModuleInstallRequest.newBuilder().addApi(scanner).build()
                moduleInstallClient.installModules(request)
                    .addOnSuccessListener { doScan() }
                    .addOnFailureListener { e ->
                        onError(
                            "スキャン機能を端末に取得できませんでした。" +
                                "Google Play開発者サービスを最新版に更新してからお試しください。" +
                                (e.message?.let { "（$it）" } ?: "")
                        )
                    }
            }
        }
        .addOnFailureListener {
            // 対応状況の確認自体に失敗した場合も、とりあえずスキャンを試みる
            doScan()
        }
}
