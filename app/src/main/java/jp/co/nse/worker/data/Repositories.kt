package jp.co.nse.worker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

/** API呼び出しの結果を表す簡易ラッパー */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String) : ApiResult<Nothing>
}

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * 機能フラグの判定。生産管理システム側 User::hasFeature() と同一ロジック
 * （"*" を持つユーザーは全権限、管理者は常に可）。
 */
private fun UserDto.hasFeature(feature: String): Boolean =
    role == "manager" || permissions?.let { "*" in it || feature in it } == true

/** 割当権限（担当者割り振り・工程管理チェックシートの編集）を持つか */
fun UserDto.canAssign(): Boolean = hasFeature("checksheet.assign_worker")

/** 受注一覧の閲覧権限を持つか（割当権限とは独立した権限） */
fun UserDto.canViewOrders(): Boolean = hasFeature("orders.view")

/** 出荷カレンダーを見る権限を持つか */
fun UserDto.canViewShippingCalendar(): Boolean = hasFeature("shipping.calendar")

/** 担当工程マスタ（マイページからの導線）を見る権限を持つか */
fun UserDto.canManageProcessAssignments(): Boolean = hasFeature("worker_process_assignments.view")

/** 不具合・要望の報告一覧・対応管理を見る権限を持つか */
fun UserDto.canManageReports(): Boolean = hasFeature("reports.manage")

/** Retrofit例外を日本語メッセージへ変換する */
internal fun Throwable.toUserMessage(): String = when (this) {
    is java.net.UnknownHostException -> "サーバーに接続できません。接続先URLとネットワークを確認してください。"
    is java.net.SocketTimeoutException -> "通信がタイムアウトしました。"
    is retrofit2.HttpException -> "サーバーエラー（${code()}）が発生しました。"
    else -> message ?: "通信エラーが発生しました。"
}

/** ActionResponse を返すエンドポイントの共通処理（成功=2xx、失敗時はmessageを抽出） */
internal fun handleAction(response: Response<ActionResponse>): ApiResult<Unit> {
    if (response.isSuccessful) {
        val body = response.body()
        // 成功でも {ok:false} を返すケースは無いが念のため確認
        return if (body?.ok == false) {
            ApiResult.Failure(body.message?.takeIf { it.isNotBlank() } ?: "操作に失敗しました。")
        } else {
            ApiResult.Success(Unit)
        }
    }
    val raw = response.errorBody()?.string()
    // Laravelのabort_unless(...)がメッセージ無しで呼ばれると {"message":""} を返すことがあり、
    // その場合はnullではなく空文字が入るため、blankもnull同様にフォールバックさせる
    val message = raw?.let {
        runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
    }?.takeIf { it.isNotBlank() }
    return ApiResult.Failure(message ?: "操作に失敗しました（${response.code()}）。")
}

class AuthRepository(
    private val apiProvider: () -> ApiService,
    private val settings: SettingsStore,
) {
    suspend fun login(email: String, password: String): ApiResult<UserDto> = try {
        val res = apiProvider().login(LoginRequest(email, password))
        settings.saveToken(res.token, res.user.name, res.user.role, res.user.canAssign(), res.user.canViewOrders(), res.user.canViewShippingCalendar(), res.user.canManageProcessAssignments(), res.user.canManageReports(), res.user.color)
        ApiResult.Success(res.user)
    } catch (e: retrofit2.HttpException) {
        val msg = e.response()?.errorBody()?.string()?.let {
            runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
        }
        ApiResult.Failure(msg ?: "メールアドレスまたはパスワードが正しくありません。")
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** ログイン画面のアカウント一覧を取得 */
    suspend fun loginUsers(): ApiResult<List<LoginUserDto>> = try {
        ApiResult.Success(apiProvider().loginUsers().users)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** アカウントID＋社員番号でログイン */
    suspend fun loginById(userId: Int, employeeNumber: String): ApiResult<UserDto> = try {
        val res = apiProvider().loginById(LoginByIdRequest(userId, employeeNumber))
        settings.saveToken(res.token, res.user.name, res.user.role, res.user.canAssign(), res.user.canViewOrders(), res.user.canViewShippingCalendar(), res.user.canManageProcessAssignments(), res.user.canManageReports(), res.user.color)
        ApiResult.Success(res.user)
    } catch (e: retrofit2.HttpException) {
        val msg = e.response()?.errorBody()?.string()?.let {
            runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
        }
        ApiResult.Failure(msg ?: "社員番号が正しくありません。")
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun logout() {
        runCatching { apiProvider().logout() }
        settings.clearToken()
    }

    /**
     * cachedTokenはAppContainer初期化時に非同期でプライムされるため、起動直後の一瞬は
     * まだ反映されていない可能性がある。スプラッシュ画面からの初回判定でその隙間を
     * ログアウト扱いにしてしまわないよう、DataStoreを直接（suspendで）読む。
     */
    suspend fun isLoggedIn(): Boolean = settings.tokenFlow.first() != null

    /** マイページでメイン色を変更する。成功したら再ログインなしで即座に画面へ反映する */
    suspend fun updateMyColor(hex: String): ApiResult<Unit> = try {
        val response = apiProvider().updateColor(UpdateColorRequest(hex))
        if (response.isSuccessful && response.body()?.ok != false) {
            settings.saveAccentColor(hex)
            ApiResult.Success(Unit)
        } else {
            val raw = response.errorBody()?.string()
            val message = raw?.let {
                runCatching { errorJson.decodeFromString<UpdateColorResponse>(it).message }.getOrNull()
            } ?: response.body()?.message
            ApiResult.Failure(message ?: "メイン色の変更に失敗しました（${response.code()}）。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }
}

class WorkerRepository(
    private val apiProvider: () -> ApiService,
) {
    suspend fun tasks(): ApiResult<TasksResponse> = try {
        ApiResult.Success(apiProvider().tasks())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** ダッシュボードに表示する、現在表示条件を満たすお知らせ一覧 */
    suspend fun announcements(): ApiResult<List<AnnouncementDto>> = try {
        ApiResult.Success(apiProvider().announcements())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 過去のお知らせも含めた一覧 */
    suspend fun announcementHistory(): ApiResult<List<AnnouncementDto>> = try {
        ApiResult.Success(apiProvider().announcementHistory())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** お知らせ詳細（本文・添付ファイルを含む） */
    suspend fun announcementDetail(announcementId: Int): ApiResult<AnnouncementDto> = try {
        ApiResult.Success(apiProvider().announcementDetail(announcementId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** バーコード値（発注番号/客先注文番号/受注ID）から担当工程を特定する */
    suspend fun findByBarcode(code: String): ApiResult<BarcodeResultDto> = try {
        ApiResult.Success(apiProvider().barcode(code))
    } catch (e: retrofit2.HttpException) {
        val msg = e.response()?.errorBody()?.string()?.let {
            runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
        }
        ApiResult.Failure(msg ?: "該当する受注が見つかりません。")
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun history(days: Int = 30): ApiResult<List<HistoryItemDto>> = try {
        ApiResult.Success(apiProvider().taskHistory(days).items)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 指定年度（4月始まり）の休日・例外稼働日の日付集合を取得（稼働日ベースの実績集計に使う） */
    suspend fun holidayCalendar(fiscalYear: Int): ApiResult<HolidaySet> = try {
        val res = apiProvider().holidays(fiscalYear)
        ApiResult.Success(HolidaySet(holidays = res.holidayMap.keys, overrides = res.overrideMap.keys))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun detail(processId: Int): ApiResult<TaskDetailDto> = try {
        ApiResult.Success(apiProvider().taskDetail(processId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun updateStatus(
        orderId: Int,
        processId: Int,
        status: String,
        pauseReason: String? = null,
    ): ApiResult<Unit> = try {
        handleAction(apiProvider().updateStatus(orderId, processId, UpdateStatusRequest(status, pauseReason)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun reportDefect(orderId: Int, processId: Int, count: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().reportDefect(orderId, processId, DefectRequest(count)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 「追加修正が必要」（最終検査・追加修正後検査での手直し登録）。photoは任意（不良箇所の写真） */
    suspend fun reportRework(
        orderId: Int,
        processId: Int,
        targetProcessId: Int,
        count: Int,
        content: String,
        photo: ImageAttachment? = null,
    ): ApiResult<Unit> = try {
        fun text(value: String) = value.toRequestBody("text/plain".toMediaType())
        val photoPart = photo?.let {
            val requestBody = it.bytes.toRequestBody(it.mimeType.toMediaType())
            MultipartBody.Part.createFormData("attachments[]", it.filename, requestBody)
        }
        handleAction(
            apiProvider().reportRework(
                orderId = orderId,
                processId = processId,
                targetProcessId = text(targetProcessId.toString()),
                count = text(count.toString()),
                content = text(content),
                attachments = photoPart,
            ),
        )
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun confirmMaterial(orderId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().confirmMaterial(orderId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 材料到着済みにする（material_arrived_date → material_arrived） */
    suspend fun markMaterialArrived(orderId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().markMaterialArrived(orderId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 工程管理チェックシート（閲覧専用）データを取得 */
    suspend fun checksheet(orderId: Int): ApiResult<CheckSheetOrderDto> = try {
        ApiResult.Success(apiProvider().checksheet(orderId).order)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 図面PDFを [destFile] にダウンロードする。PdfRenderer は seek 可能なファイルを要求するためファイルに保存する */
    suspend fun downloadDrawing(processId: Int, destFile: File): ApiResult<File> = try {
        val res = apiProvider().taskDrawing(processId)
        when {
            res.isSuccessful -> {
                val body = res.body()
                if (body == null) {
                    ApiResult.Failure("図面の取得に失敗しました。")
                } else {
                    withContext(Dispatchers.IO) {
                        body.byteStream().use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    ApiResult.Success(destFile)
                }
            }
            res.code() == 404 -> ApiResult.Failure("この作業には図面が登録されていません。")
            else -> ApiResult.Failure("図面の取得に失敗しました（${res.code()}）。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 未読通知一覧（工程の割り当て・次工程の順番が回ってきた等） */
    suspend fun unreadNotifications(): ApiResult<List<NotificationDto>> = try {
        ApiResult.Success(apiProvider().unreadNotifications().notifications)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 全通知を既読にする */
    suspend fun markAllNotificationsRead(): ApiResult<Unit> = try {
        handleAction(apiProvider().markAllNotificationsRead())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 通知を1件だけ既読にする（通知一覧でのスワイプ操作用） */
    suspend fun markNotificationRead(id: String): ApiResult<Unit> = try {
        handleAction(apiProvider().markNotificationRead(id))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 不具合・UI改善要望を管理者へ報告する。端末情報は呼び出し側で入力させず自動で付与する */
    suspend fun submitReport(
        category: ReportCategory,
        title: String,
        body: String,
        screenName: String?,
        screenshot: ImageAttachment?,
    ): ApiResult<Unit> = try {
        fun text(value: String) = value.toRequestBody("text/plain".toMediaType())
        val screenshotPart = screenshot?.let {
            val requestBody = it.bytes.toRequestBody(it.mimeType.toMediaType())
            MultipartBody.Part.createFormData("screenshot", it.filename, requestBody)
        }
        handleAction(
            apiProvider().submitReport(
                category = text(category.apiValue),
                title = text(title),
                body = text(body),
                screenName = screenName?.let { text(it) },
                appVersion = text(jp.co.nse.worker.BuildConfig.VERSION_NAME),
                osVersion = text("Android ${android.os.Build.VERSION.RELEASE}"),
                deviceModel = text(android.os.Build.MODEL),
                screenshot = screenshotPart,
            ),
        )
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }
}

/** 画像添付の中身（報告のスクリーンショット・手直し登録の写真など共通）。呼び出し側（画面層）でデコードして作る */
data class ImageAttachment(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
)

/** アプリ自動アップデート：最新版確認とAPKダウンロード */
class UpdateRepository(
    private val apiProvider: () -> ApiService,
) {
    suspend fun checkLatest(): ApiResult<AppUpdateDto> = try {
        ApiResult.Success(apiProvider().appLatest())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** APKを [destFile] にダウンロードする。[onProgress] は 0f..1f（不明時は負値を渡さない） */
    suspend fun downloadApk(
        destFile: File,
        onProgress: (Float) -> Unit,
    ): ApiResult<File> = try {
        val res = apiProvider().downloadApk()
        val body = res.body()
        when {
            !res.isSuccessful -> ApiResult.Failure("更新ファイルの取得に失敗しました（${res.code()}）。")
            body == null -> ApiResult.Failure("更新ファイルの取得に失敗しました。")
            else -> {
                withContext(Dispatchers.IO) {
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) onProgress(downloaded.toFloat() / total)
                            }
                        }
                    }
                }
                ApiResult.Success(destFile)
            }
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }
}

/** 管理者向け：受注一覧・作業者候補・担当割当 */
class ManagerRepository(
    private val apiProvider: () -> ApiService,
) {
    /** statusesを渡すとサーバー側でそのステータスに絞り込む（省略時は出荷済み・請求済みを除いた既定セット） */
    suspend fun orders(statuses: List<String>? = null, perPage: Int = 100): ApiResult<List<OrderAssignDto>> = try {
        ApiResult.Success(apiProvider().orders(perPage = perPage, statuses = statuses).data)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun orderDetail(orderId: Int): ApiResult<OrderAssignDto> = try {
        ApiResult.Success(apiProvider().orderDetail(orderId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    suspend fun workers(): ApiResult<List<WorkerDto>> = try {
        ApiResult.Success(apiProvider().workers().workers)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 担当工程マスタ：作業者一覧・工程マスタ一覧・現在の割り当てを取得 */
    suspend fun processAssignments(): ApiResult<ProcessAssignmentsResponse> = try {
        ApiResult.Success(apiProvider().processAssignments())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 担当工程の割り当てON/OFFを切り替える。戻り値は切り替え後の状態 */
    suspend fun toggleProcessAssignment(userId: Int, masterId: Int): ApiResult<Boolean> = try {
        val res = apiProvider().toggleProcessAssignment(ProcessAssignmentRequest(userId, masterId))
        ApiResult.Success(res.assigned)
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 自動割り振りのデフォルト担当者にする（同じ工程の他の担当者のデフォルトは自動的に解除される） */
    suspend fun setDefaultProcessAssignment(userId: Int, masterId: Int): ApiResult<Unit> = try {
        val response = apiProvider().setDefaultProcessAssignment(ProcessAssignmentRequest(userId, masterId))
        if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            val raw = response.errorBody()?.string()
            val message = raw?.let {
                runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
            }
            ApiResult.Failure(message ?: "デフォルト担当者の設定に失敗しました（${response.code()}）。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** デフォルト担当者の指定を解除する */
    suspend fun unsetDefaultProcessAssignment(userId: Int, masterId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().unsetDefaultProcessAssignment(ProcessAssignmentRequest(userId, masterId)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 担当者を割り当て（null で未割当に戻す） */
    suspend fun assignWorker(orderId: Int, processId: Int, workerName: String?): ApiResult<Unit> = try {
        handleAction(apiProvider().assignWorker(orderId, processId, AssignWorkerRequest(workerName)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 支給品在庫一覧（倉庫在庫・引当済み・引当中の受注・入出庫履歴） */
    suspend fun materialInventories(): ApiResult<List<MaterialInventoryDto>> = try {
        ApiResult.Success(apiProvider().materialInventories())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 指定した支給品在庫の品番に紐づく、引当候補の受注一覧 */
    suspend fun materialCandidates(materialInventoryId: Int): ApiResult<List<MaterialCandidateDto>> = try {
        ApiResult.Success(apiProvider().materialCandidates(materialInventoryId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 入荷（倉庫在庫数を増やす） */
    suspend fun restockMaterial(materialInventoryId: Int, quantity: Int, note: String? = null): ApiResult<Unit> = try {
        handleAction(apiProvider().restockMaterial(materialInventoryId, RestockRequest(quantity, note)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /**
     * 引き当て。サーバー側で二重引き当てチェックがあり、失敗時は422メッセージをそのまま返す。
     * 成功すると対象受注のmaterial_arrived_atが更新され、ステータス次第で材料到着済みに変わる
     * （どちらもサーバー側の副作用で、ここでは結果メッセージを表示するだけでよい）。
     */
    suspend fun allocateMaterial(materialInventoryId: Int, orderId: Int, quantity: Int, note: String? = null): ApiResult<Unit> = try {
        handleAction(apiProvider().allocateMaterial(materialInventoryId, AllocateRequest(orderId, quantity, note)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 引き当ての取り消し */
    suspend fun deallocateMaterial(allocationId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().deallocateMaterial(allocationId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 入荷履歴の取り消し。引当済み数量を下回る取消はサーバー側で422拒否される */
    suspend fun deleteMaterialTransaction(transactionId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().deleteMaterialTransaction(transactionId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 工程納期を更新（null で未設定に戻す）。休日・客先納期・前工程順のチェックはサーバー側で行われる */
    suspend fun updateDeadline(orderId: Int, processId: Int, deadline: String?): ApiResult<Unit> = try {
        handleAction(apiProvider().updateDeadline(orderId, processId, UpdateDeadlineRequest(deadline)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 指定年度（4月始まり）の休日・例外稼働日の日付集合を取得 */
    suspend fun holidayCalendar(fiscalYear: Int): ApiResult<HolidaySet> = try {
        val res = apiProvider().holidays(fiscalYear)
        ApiResult.Success(HolidaySet(holidays = res.holidayMap.keys, overrides = res.overrideMap.keys))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 担当工程マスタを参照し、未割り当ての工程にデフォルト担当者を自動で割り振る */
    suspend fun autoAssign(orderId: Int): ApiResult<AutoAssignResponse> = try {
        val response = apiProvider().autoAssign(orderId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body) else ApiResult.Failure("自動割り振りに失敗しました。")
        } else {
            val raw = response.errorBody()?.string()
            val message = raw?.let {
                runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
            }
            ApiResult.Failure(message ?: "自動割り振りに失敗しました（${response.code()}）。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 出荷カレンダー（納期ベース、指定した年月の1ヶ月分）を取得 */
    suspend fun shippingCalendar(year: Int, month: Int): ApiResult<ShippingCalendarMonthDto?> = try {
        val res = apiProvider().shippingCalendar(year, month, months = 1)
        ApiResult.Success(res.calMonths.firstOrNull())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 出荷カレンダーのバーコード検索（発注番号/客先注文番号/受注IDのいずれか） */
    suspend fun shippingBarcodeSearch(code: String): ApiResult<ShippingBarcodeOrderDto> = try {
        val res = apiProvider().shippingBarcodeSearch(code)
        if (res.found && res.order != null) {
            ApiResult.Success(res.order)
        } else {
            ApiResult.Failure(res.message ?: "該当する受注が見つかりません。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 出荷完了にする（出荷待ち以外・仮注文はサーバー側で拒否される） */
    suspend fun shipOrder(orderId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().shipOrder(orderId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 出荷完了を取り消す（出荷完了状態以外はサーバー側で拒否される） */
    suspend fun unshipOrder(orderId: Int): ApiResult<Unit> = try {
        handleAction(apiProvider().unshipOrder(orderId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 不具合・UI改善要望の報告一覧（新しい順） */
    suspend fun reports(): ApiResult<List<ReportDto>> = try {
        ApiResult.Success(apiProvider().reports())
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 報告詳細 */
    suspend fun reportDetail(reportId: Int): ApiResult<ReportDto> = try {
        ApiResult.Success(apiProvider().reportDetail(reportId))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 報告に添付されたスクリーンショットをバイト列として取得する */
    suspend fun reportScreenshotBytes(reportId: Int): ApiResult<ByteArray> = try {
        val res = apiProvider().reportScreenshot(reportId)
        if (res.isSuccessful) {
            val bytes = withContext(Dispatchers.IO) { res.body()?.bytes() }
            if (bytes != null) ApiResult.Success(bytes) else ApiResult.Failure("画像の取得に失敗しました。")
        } else {
            ApiResult.Failure("画像の取得に失敗しました（${res.code()}）。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 報告の対応ステータスを変更する */
    suspend fun updateReportStatus(reportId: Int, status: ReportStatus): ApiResult<Unit> = try {
        handleAction(apiProvider().updateReportStatus(reportId, UpdateReportStatusRequest(status.apiValue)))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** 有給休暇・休暇取得状況。start/endを省略すると当月分、departmentで課を絞り込める（"yyyy-MM-dd"） */
    suspend fun leaves(start: String? = null, end: String? = null, department: String? = null): ApiResult<LeavesResponse> = try {
        ApiResult.Success(apiProvider().leaves(start, end, department))
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }

    /** この受注内の担当者割り当て済みの未完了工程を、まとめて未割り当てに戻す */
    suspend fun unassignAll(orderId: Int): ApiResult<UnassignAllResponse> = try {
        val response = apiProvider().unassignAll(orderId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) ApiResult.Success(body) else ApiResult.Failure("未割り当てへの変更に失敗しました。")
        } else {
            val raw = response.errorBody()?.string()
            val message = raw?.let {
                runCatching { errorJson.decodeFromString<ActionResponse>(it).message }.getOrNull()
            }
            ApiResult.Failure(message ?: "未割り当てへの変更に失敗しました（${response.code()}）。")
        }
    } catch (e: Throwable) {
        ApiResult.Failure(e.toUserMessage())
    }
}

/** 休日／例外稼働日の日付集合（"yyyy-MM-dd"） */
data class HolidaySet(
    val holidays: Set<String>,
    val overrides: Set<String>,
)
