package jp.co.nse.worker.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Laravel routes/api.php に対応するRetrofitインターフェース。
 * ベースURL末尾は "/api/"（末尾スラッシュ必須）。
 */
interface ApiService {

    @POST("login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    // ===== アプリ更新（認証不要） =====

    @GET("app/latest")
    suspend fun appLatest(): AppUpdateDto

    @Streaming
    @GET("app/download")
    suspend fun downloadApk(): Response<ResponseBody>

    @GET("login/users")
    suspend fun loginUsers(): LoginUsersResponse

    @POST("login/by-id")
    suspend fun loginById(@Body body: LoginByIdRequest): AuthResponse

    @GET("user")
    suspend fun user(): UserDto

    /** マイページのメイン色を変更する（白・黒は422で拒否される） */
    @PATCH("user/color")
    suspend fun updateColor(@Body body: UpdateColorRequest): Response<UpdateColorResponse>

    @POST("logout")
    suspend fun logout(): Response<ResponseBody>

    @GET("tasks")
    suspend fun tasks(): TasksResponse

    @GET("tasks/history")
    suspend fun taskHistory(@Query("days") days: Int = 30): HistoryResponse

    @GET("tasks/barcode")
    suspend fun barcode(@Query("code") code: String): BarcodeResultDto

    @GET("tasks/{process}")
    suspend fun taskDetail(@Path("process") processId: Int): TaskDetailDto

    /** 図面PDFをダウンロード（認証ヘッダ付き）。@Streaming で大きいPDFもメモリに乗せ過ぎない */
    @Streaming
    @GET("tasks/{process}/drawing")
    suspend fun taskDrawing(@Path("process") processId: Int): Response<ResponseBody>

    @PATCH("orders/{order}/processes/{process}/status")
    suspend fun updateStatus(
        @Path("order") orderId: Int,
        @Path("process") processId: Int,
        @Body body: UpdateStatusRequest,
    ): Response<ActionResponse>

    @POST("orders/{order}/processes/{process}/defect")
    suspend fun reportDefect(
        @Path("order") orderId: Int,
        @Path("process") processId: Int,
        @Body body: DefectRequest,
    ): Response<ActionResponse>

    @PATCH("orders/{order}/material-confirm")
    suspend fun confirmMaterial(@Path("order") orderId: Int): Response<ActionResponse>

    /** 材料到着済みにする（material_arrived_date → material_arrived） */
    @PATCH("orders/{order}/material-arrived")
    suspend fun markMaterialArrived(@Path("order") orderId: Int): Response<ActionResponse>

    @GET("orders/{order}/checksheet")
    suspend fun checksheet(@Path("order") orderId: Int): CheckSheetResponse

    // ===== 割当（管理者向け） =====

    @GET("workers")
    suspend fun workers(): WorkersResponse

    // ===== 担当工程マスタ（管理者向け） =====

    @GET("worker-process-assignments")
    suspend fun processAssignments(): ProcessAssignmentsResponse

    @POST("worker-process-assignments/toggle")
    suspend fun toggleProcessAssignment(@Body body: ProcessAssignmentRequest): ToggleProcessAssignmentResponse

    @POST("worker-process-assignments/set-default")
    suspend fun setDefaultProcessAssignment(@Body body: ProcessAssignmentRequest): Response<ActionResponse>

    @POST("worker-process-assignments/unset-default")
    suspend fun unsetDefaultProcessAssignment(@Body body: ProcessAssignmentRequest): Response<ActionResponse>

    // ===== 支給品在庫（管理者向け） =====

    @GET("material-inventories")
    suspend fun materialInventories(): List<MaterialInventoryDto>

    @GET("orders/material-candidates")
    suspend fun materialCandidates(@Query("material_inventory_id") id: Int): List<MaterialCandidateDto>

    @POST("material-inventories/{id}/restock")
    suspend fun restockMaterial(@Path("id") id: Int, @Body body: RestockRequest): Response<ActionResponse>

    @POST("material-inventories/{id}/allocate")
    suspend fun allocateMaterial(@Path("id") id: Int, @Body body: AllocateRequest): Response<ActionResponse>

    @DELETE("material-inventory-allocations/{id}/deallocate")
    suspend fun deallocateMaterial(@Path("id") id: Int): Response<ActionResponse>

    @DELETE("material-inventory-transactions/{id}")
    suspend fun deleteMaterialTransaction(@Path("id") id: Int): Response<ActionResponse>

    @GET("orders")
    suspend fun orders(@Query("per_page") perPage: Int = 100): OrdersPage

    @GET("orders/{order}")
    suspend fun orderDetail(@Path("order") orderId: Int): OrderAssignDto

    @PATCH("orders/{order}/processes/{process}/worker")
    suspend fun assignWorker(
        @Path("order") orderId: Int,
        @Path("process") processId: Int,
        @Body body: AssignWorkerRequest,
    ): Response<ActionResponse>

    @PATCH("orders/{order}/processes/{process}/deadline")
    suspend fun updateDeadline(
        @Path("order") orderId: Int,
        @Path("process") processId: Int,
        @Body body: UpdateDeadlineRequest,
    ): Response<ActionResponse>

    /** 担当工程マスタを参照し、この受注内の未割り当て工程にデフォルト担当者を自動で割り振る */
    @POST("orders/{order}/auto-assign")
    suspend fun autoAssign(@Path("order") orderId: Int): Response<AutoAssignResponse>

    /** この受注内の担当者割り当て済みの未完了工程を、まとめて未割り当てに戻す */
    @POST("orders/{order}/unassign-all")
    suspend fun unassignAll(@Path("order") orderId: Int): Response<UnassignAllResponse>

    /** 休日カレンダー（fy=年度、4月始まり）。稼働日判定に使う */
    @GET("holidays")
    suspend fun holidays(@Query("fy") fiscalYear: Int): HolidayCalendarResponse

    /** 出荷カレンダー（納期ベースの月間受注一覧） */
    @GET("shipping-calendar")
    suspend fun shippingCalendar(
        @Query("year") year: Int,
        @Query("month") month: Int,
        @Query("months") months: Int = 1,
    ): ShippingCalendarResponse

    /** 出荷カレンダーのバーコード検索（発注番号/客先注文番号/受注ID → 受注情報） */
    @GET("shipping-calendar/barcode")
    suspend fun shippingBarcodeSearch(@Query("code") code: String): ShippingBarcodeResponse

    /** 出荷完了にする（出荷待ち以外は422で拒否される） */
    @POST("orders/{order}/ship")
    suspend fun shipOrder(@Path("order") orderId: Int): Response<ActionResponse>

    /** 出荷完了を取り消す（出荷待ちに戻す。消費した支給材料の引き当て・在庫も復元される） */
    @POST("orders/{order}/unship")
    suspend fun unshipOrder(@Path("order") orderId: Int): Response<ActionResponse>

    /** 未読通知一覧（工程の割り当て・次工程の順番が回ってきた等） */
    @GET("notifications/unread")
    suspend fun unreadNotifications(): NotificationsResponse

    /** 全通知を既読にする */
    @POST("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<ActionResponse>

    /** 通知を1件だけ既読にする */
    @POST("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<ActionResponse>
}
