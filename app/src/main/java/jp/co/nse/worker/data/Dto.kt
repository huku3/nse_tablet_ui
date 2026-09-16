package jp.co.nse.worker.data

import kotlinx.serialization.Serializable

/**
 * APIとやり取りするデータ転送オブジェクト。
 * Laravel側 app/Http/Controllers/Api 配下のレスポンス形状に対応する。
 */

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: Int,
    val name: String,
    val email: String? = null,
    val role: String? = null,
    val permissions: List<String>? = null,
    val color: String? = null,
)

// ===== マイページ（メイン色） =====

@Serializable
data class UpdateColorRequest(
    val color: String,
)

@Serializable
data class UpdateColorResponse(
    val ok: Boolean? = null,
    val color: String? = null,
    val message: String? = null,
)

// ===== アカウント選択ログイン =====

@Serializable
data class LoginUsersResponse(
    val users: List<LoginUserDto> = emptyList(),
)

@Serializable
data class LoginUserDto(
    val id: Int,
    val name: String,
    val color: String? = null,
    val role: String? = null,
)

@Serializable
data class LoginByIdRequest(
    val user_id: Int,
    val employee_number: String,
)

// ===== 作業一覧 =====

@Serializable
data class TasksResponse(
    val active: List<TaskItemDto> = emptyList(),
    val completed_today: List<CompletedTaskDto> = emptyList(),
)

@Serializable
data class TaskItemDto(
    val id: Int,
    val process_name: String,
    val status: String,
    val sort_order: Int = 0,
    val started_at: String? = null,
    val worker: String? = null,
    val processing_count: Int? = null,
    val completed_count: Int = 0,
    val total_count: Int = 0,
    val defect_count: Int? = null,
    val process_deadline: String? = null,
    val notes: String? = null,
    val order: OrderBriefDto,
    val all_processes: List<ProcessBriefDto> = emptyList(),
)

@Serializable
data class OrderBriefDto(
    val id: Int,
    val po_number: String? = null,
    val part_name: String? = null,
    val part_number: String? = null,
    val delivery_date: String? = null,
    val status: String? = null,
    val quantity: Int? = null,
    val order_type: String? = null,
)

@Serializable
data class ProcessBriefDto(
    val id: Int,
    val process_name: String,
    val status: String,
    val sort_order: Int = 0,
    val worker: String? = null,
    val process_deadline: String? = null,
)

@Serializable
data class CompletedTaskDto(
    val id: Int,
    val process_name: String,
    val status: String,
    val started_at: String? = null,
    val completed_at: String? = null,
    val work_minutes: Int? = null,
    val order: OrderBriefDto,
)

// ===== 作業実績（履歴） =====

@Serializable
data class HistoryResponse(
    val items: List<HistoryItemDto> = emptyList(),
)

@Serializable
data class HistoryItemDto(
    val id: Int,
    val process_name: String,
    val status: String,
    val started_at: String? = null,
    val completed_at: String? = null,
    val work_minutes: Int? = null,
    val defect_count: Int? = null,
    val order: OrderBriefDto,
)

// ===== アプリ更新 =====

@Serializable
data class AppUpdateDto(
    val version_code: Int = 0,
    val version_name: String = "",
    val apk_url: String? = null,
    val notes: String = "",
    val mandatory: Boolean = false,
)

// ===== バーコード検索 =====

@Serializable
data class BarcodeResultDto(
    val process_id: Int,
    val order_id: Int,
    val process_name: String? = null,
    val part_name: String? = null,
    val part_number: String? = null,
    val po_number: String? = null,
    val customer_order_number: String? = null,
    val delivery_date: String? = null,
    val order_status: String? = null,
    val process_status: String? = null,
    val worker: String? = null,
)

// ===== 作業割当（管理者向け） =====

@Serializable
data class WorkersResponse(
    val workers: List<WorkerDto> = emptyList(),
)

@Serializable
data class WorkerDto(
    val id: Int,
    val name: String,
    val color: String? = null,
)

/** GET /api/orders はLaravelのページネーション形式。data のみ利用 */
@Serializable
data class OrdersPage(
    val data: List<OrderAssignDto> = emptyList(),
    val current_page: Int = 1,
    val last_page: Int = 1,
)

@Serializable
data class OrderAssignDto(
    val id: Int,
    val po_number: String? = null,
    val customer_order_number: String? = null,
    val customer_name: String? = null,
    val part_name: String? = null,
    val part_number: String? = null,
    val delivery_date: String? = null,
    val quantity: Int? = null,
    val status: String? = null,
    val order_type: String? = null,
    val processes: List<AssignProcessDto> = emptyList(),
    // status="material_waiting"の間は、材料の到着予定日（生産管理システム側で日次バッチが
    // material_arrived_at <= 今日 になった時点でstatusをmaterial_arrived_dateへ自動遷移させる
    // ため、材料待ちの間はまだ未来の予定日として扱える）。ダッシュボードの「材料待ち」カードで、
    // 今日・翌稼働日・翌々稼働日に到着予定の件数を出すのに使う
    val material_arrived_at: String? = null,
    // ダッシュボードの材料入荷状況カードで表示する材料情報（現状 /orders エンドポイントの
    // レスポンスに未追加の場合はnullのままになるため、表示するにはAPI側の対応が別途必要）
    val material_name: String? = null,
    val material_size: String? = null,
    val material_supplier: String? = null,
)

@Serializable
data class AssignProcessDto(
    val id: Int,
    val process_name: String,
    val status: String? = null,
    val sort_order: Int = 0,
    val worker: String? = null,
    val process_deadline: String? = null,
    val process_deadline_start: String? = null,
)

@Serializable
data class AssignWorkerRequest(
    val worker: String? = null,
)

@Serializable
data class UpdateDeadlineRequest(
    val process_deadline: String? = null,
    val process_deadline_start: String? = null,
)

/** GET /api/holidays のレスポンス。日付キー（"Y-m-d"）の集合だけ使う */
@Serializable
data class HolidayCalendarResponse(
    val holidayMap: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val overrideMap: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
)

// ===== 作業詳細 =====

@Serializable
data class TaskDetailDto(
    val process: ProcessDetailDto,
    val order: OrderDetailDto,
    val all_processes: List<ProcessBriefDto> = emptyList(),
    val can_start: Boolean = true,
    val blocking_process: String? = null,
    val blocking_worker: String? = null,
    val needs_material_check: Boolean = false,
)

@Serializable
data class ProcessDetailDto(
    val id: Int,
    val process_name: String,
    val status: String,
    val sort_order: Int = 0,
    val worker: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
    val processing_count: Int? = null,
    val defect_count: Int? = null,
    val process_deadline: String? = null,
    val notes: String? = null,
    val pause_reason: String? = null,
)

@Serializable
data class OrderDetailDto(
    val id: Int,
    val po_number: String? = null,
    val customer_order_number: String? = null,
    val part_name: String? = null,
    val part_number: String? = null,
    val delivery_date: String? = null,
    val quantity: Int? = null,
    val status: String? = null,
    val material_arrived_at: String? = null,
    val has_drawing: Boolean = false,
    val drawing_filename: String? = null,
)

// ===== アクション =====

@Serializable
data class UpdateStatusRequest(
    val status: String,
    val pause_reason: String? = null,
)

@Serializable
data class DefectRequest(
    val count: Int,
)

/** 成功時は工程オブジェクト、失敗時は {ok:false, message} が返るため両対応 */
@Serializable
data class ActionResponse(
    val ok: Boolean? = null,
    val message: String? = null,
)

@Serializable
data class AutoAssignResponse(
    val ok: Boolean? = null,
    val assigned_count: Int = 0,
    val skipped_count: Int = 0,
    val message: String? = null,
)

@Serializable
data class UnassignAllResponse(
    val ok: Boolean? = null,
    val unassigned_count: Int = 0,
    val message: String? = null,
)

// ===== 通知 =====

@Serializable
data class NotificationDataDto(
    val type: String? = null,
    val process_id: Int? = null,
    val process_name: String? = null,
    val order_id: Int? = null,
    val part_name: String? = null,
    val po_number: String? = null,
    val worker: String? = null,
    val reported_by: String? = null,
    val assigned_by: String? = null,
    val process_deadline: String? = null,
)

@Serializable
data class NotificationDto(
    val id: String,
    val data: NotificationDataDto,
    val at: String,
)

@Serializable
data class NotificationsResponse(
    val notifications: List<NotificationDto> = emptyList(),
)

// ===== 出荷カレンダー =====

@Serializable
data class ShippingCalendarResponse(
    val calMonths: List<ShippingCalendarMonthDto> = emptyList(),
)

@Serializable
data class ShippingCalendarMonthDto(
    val label: String,
    val year: Int,
    val month: Int,
    val weeks: List<List<ShippingCalendarDayDto>> = emptyList(),
)

@Serializable
data class ShippingCalendarDayDto(
    val date: String,
    val inMonth: Boolean = false,
    val isToday: Boolean = false,
    val orders: List<ShippingCalendarOrderDto> = emptyList(),
)

@Serializable
data class ShippingCalendarOrderDto(
    val id: Int,
    val po_number: String? = null,
    val part_name: String? = null,
    val part_number: String? = null,
    val quantity: Int? = null,
    val status: String? = null,
    val customer_name: String? = null,
)

/** 出荷カレンダーのバーコード検索結果 */
@Serializable
data class ShippingBarcodeResponse(
    val found: Boolean = false,
    val message: String? = null,
    val order: ShippingBarcodeOrderDto? = null,
)

@Serializable
data class ShippingBarcodeOrderDto(
    val id: Int,
    val po_number: String? = null,
    val customer_order_number: String? = null,
    val customer_name: String? = null,
    val part_number: String? = null,
    val part_name: String? = null,
    val delivery_date: String? = null,
    val quantity: Int? = null,
    val status: String? = null,
    val status_label: String? = null,
    val already_shipped: Boolean = false,
    val is_shipping_wait: Boolean = false,
)

// ===== 工程管理チェックシート（閲覧専用） =====

@Serializable
data class CheckSheetResponse(
    val order: CheckSheetOrderDto,
)

@Serializable
data class CheckSheetPartyDto(
    val name: String? = null,
)

@Serializable
data class CheckSheetProductDto(
    val material_name: String? = null,
    val material_size: String? = null,
    val material_supplier_type: String? = null,
    val supplier: CheckSheetPartyDto? = null,
    val customer: CheckSheetPartyDto? = null,
)

@Serializable
data class CheckSheetOrderDto(
    val id: Int,
    val status: String? = null,
    val order_type: String? = null,
    val customer_name: String? = null,
    val part_number: String? = null,
    val part_name: String? = null,
    val material_supplier: String? = null,
    val material_size: String? = null,
    val notes: String? = null,
    val po_number: String? = null,
    val customer_order_number: String? = null,
    val delivery_date: String? = null,
    val quantity: Int? = null,
    val defect_count: Int? = null,
    val material_arrived_at: String? = null,
    val created_at: String? = null,
    val has_drawing: Boolean = false,
    val product: CheckSheetProductDto? = null,
    val processes: List<CheckSheetProcessDto> = emptyList(),
)

@Serializable
data class CheckSheetProcessDto(
    val id: Int,
    val process_name: String,
    val status: String,
    val sort_order: Int = 0,
    val process_date: String? = null,
    val worker: String? = null,
    val process_deadline: String? = null,
    val process_deadline_start: String? = null,
    val notes: String? = null,
    val defect_count: Int? = null,
)

// ===== 担当工程マスタ =====

@Serializable
data class ProcessMasterLiteDto(
    val id: Int,
    val name: String,
)

@Serializable
data class WorkerProcessAssignmentDto(
    val user_id: Int,
    val process_master_id: Int,
    val is_default: Boolean = false,
)

@Serializable
data class ProcessAssignmentsResponse(
    val workers: List<WorkerDto> = emptyList(),
    val process_masters: List<ProcessMasterLiteDto> = emptyList(),
    val assignments: List<WorkerProcessAssignmentDto> = emptyList(),
)

@Serializable
data class ProcessAssignmentRequest(
    val user_id: Int,
    val process_master_id: Int,
)

@Serializable
data class ToggleProcessAssignmentResponse(
    val assigned: Boolean,
)

// ===== 支給品在庫 =====

@Serializable
data class MaterialInventoryDto(
    val id: Int,
    val material_name: String,
    val material_size: String? = null,
    val customer_name: String? = null,
    val quantity: Int,
    val allocated_quantity: Int,
    val part_numbers: List<String> = emptyList(),
    val allocations: List<MaterialAllocationDto> = emptyList(),
    val transactions: List<MaterialTransactionDto> = emptyList(),
)

@Serializable
data class MaterialAllocationDto(
    val id: Int,
    val order_id: Int,
    val customer_name: String? = null,
    val part_name: String? = null,
    val quantity: Int,
    val note: String? = null,
    val created_by: String? = null,
    val created_at: String? = null,
)

@Serializable
data class MaterialTransactionDto(
    val id: Int,
    val type: String,
    val quantity: Int,
    val note: String? = null,
    val created_by: String? = null,
    val created_at: String,
)

@Serializable
data class MaterialCandidateDto(
    val order_id: Int,
    val customer_name: String? = null,
    val part_name: String? = null,
    val order_quantity: Int? = null,
    val delivery_date: String? = null,
    val status: String? = null,
)

@Serializable
data class RestockRequest(
    val quantity: Int,
    val note: String? = null,
)

@Serializable
data class AllocateRequest(
    val order_id: Int,
    val quantity: Int,
    val note: String? = null,
)

// ===== 不具合・要望の報告 =====

/** 報告フォームのカテゴリ。[apiValue] はサーバーに送信する値、[label] は画面表示用 */
enum class ReportCategory(val apiValue: String, val label: String) {
    UI_IMPROVEMENT("ui_improvement", "UI改善"),
    BUG("bug", "不具合"),
    OTHER("other", "その他"),
}

/** 報告一覧・詳細（管理者向け）。一覧では body 等は null のまま届く */
@Serializable
data class ReportDto(
    val id: Int,
    val category: String,
    val category_label: String? = null,
    val title: String,
    val status: String,
    val status_label: String? = null,
    val reporter_name: String? = null,
    val has_screenshot: Boolean = false,
    val created_at: String? = null,
    val body: String? = null,
    val screen_name: String? = null,
    val app_version: String? = null,
    val os_version: String? = null,
    val device_model: String? = null,
    val resolver_name: String? = null,
    val resolved_at: String? = null,
)

@Serializable
data class UpdateReportStatusRequest(
    val status: String,
)

/** 報告の対応ステータス。[apiValue] はサーバーに送信する値、[label] は画面表示用 */
enum class ReportStatus(val apiValue: String, val label: String) {
    OPEN("open", "未対応"),
    IN_PROGRESS("in_progress", "対応中"),
    RESOLVED("resolved", "対応済み"),
    WONTFIX("wontfix", "対応しない"),
    ;

    companion object {
        fun fromApiValue(value: String): ReportStatus = entries.find { it.apiValue == value } ?: OPEN
    }
}

// ===== 有給休暇・休暇取得状況（長島勤怠システム連携） =====

@Serializable
data class LeavesResponse(
    val period: LeavePeriodDto = LeavePeriodDto(),
    val departments: List<String> = emptyList(),
    val days: List<LeaveDayDto> = emptyList(),
)

@Serializable
data class LeavePeriodDto(
    val start: String? = null,
    val end: String? = null,
)

@Serializable
data class LeaveDayDto(
    val date: String,
    val leaves: List<LeaveEntryDto> = emptyList(),
)

@Serializable
data class LeaveEntryDto(
    val user_name: String,
    val department_name: String? = null,
    val category_name: String? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val am_pm: Int? = null,
)
