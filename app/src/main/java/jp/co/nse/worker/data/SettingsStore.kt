package jp.co.nse.worker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.co.nse.worker.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nse_worker_settings")

/** 個人の色を選んでいない場合のメイン色（出荷カレンダー画面と同じブルー） */
private const val DEFAULT_ACCENT_HEX = "#4338CA"

/**
 * 認証トークン・接続先ベースURL・ユーザー名を永続化する。
 * トークンとベースURLはインターセプタ／Retrofit構築から同期的に参照するため、
 * メモリ上にもキャッシュする。
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val TOKEN = stringPreferencesKey("auth_token")
        val BASE_URL = stringPreferencesKey("base_url")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_ROLE = stringPreferencesKey("user_role")
        val CAN_ASSIGN = booleanPreferencesKey("can_assign")
        val CAN_VIEW_SHIPPING = booleanPreferencesKey("can_view_shipping")
        val CAN_MANAGE_PROCESS_ASSIGNMENTS = booleanPreferencesKey("can_manage_process_assignments")
        val ACCENT_COLOR = stringPreferencesKey("accent_color_hex")
    }

    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedBaseUrl: String = BuildConfig.DEFAULT_API_BASE_URL
        private set

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }
    val baseUrlFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.BASE_URL] ?: BuildConfig.DEFAULT_API_BASE_URL }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_NAME] }
    val userRoleFlow: Flow<String?> = context.dataStore.data.map { it[Keys.USER_ROLE] }
    val canAssignFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.CAN_ASSIGN] ?: false }
    val canViewShippingFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.CAN_VIEW_SHIPPING] ?: false }
    val canManageProcessAssignmentsFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CAN_MANAGE_PROCESS_ASSIGNMENTS] ?: false }

    /** 現在ログイン中アカウントのマイページ設定色（"#RRGGBB"）。未設定時はアプリの既定色 */
    val accentColorFlow: Flow<String> =
        context.dataStore.data.map { it[Keys.ACCENT_COLOR] ?: DEFAULT_ACCENT_HEX }

    suspend fun saveToken(
        token: String,
        userName: String,
        userRole: String?,
        canAssign: Boolean,
        canViewShipping: Boolean,
        canManageProcessAssignments: Boolean,
        accentColorHex: String?,
    ) {
        cachedToken = token
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.USER_NAME] = userName
            if (userRole != null) it[Keys.USER_ROLE] = userRole else it.remove(Keys.USER_ROLE)
            it[Keys.CAN_ASSIGN] = canAssign
            it[Keys.CAN_VIEW_SHIPPING] = canViewShipping
            it[Keys.CAN_MANAGE_PROCESS_ASSIGNMENTS] = canManageProcessAssignments
            it[Keys.ACCENT_COLOR] = accentColorHex ?: DEFAULT_ACCENT_HEX
        }
    }

    /** マイページでメイン色を変更した直後、再ログインなしで反映するための更新 */
    suspend fun saveAccentColor(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_COLOR] = hex }
    }

    suspend fun clearToken() {
        cachedToken = null
        context.dataStore.edit {
            it.remove(Keys.TOKEN)
            it.remove(Keys.USER_NAME)
            it.remove(Keys.USER_ROLE)
            it.remove(Keys.CAN_ASSIGN)
            it.remove(Keys.CAN_VIEW_SHIPPING)
            it.remove(Keys.CAN_MANAGE_PROCESS_ASSIGNMENTS)
            it.remove(Keys.ACCENT_COLOR)
        }
    }

    suspend fun saveBaseUrl(url: String) {
        cachedBaseUrl = normalize(url)
        context.dataStore.edit { it[Keys.BASE_URL] = cachedBaseUrl }
    }

    /** メモリキャッシュへ反映（起動時の同期用） */
    fun primeCache(token: String?, baseUrl: String) {
        cachedToken = token
        cachedBaseUrl = baseUrl
    }

    /** 末尾スラッシュを保証する（Retrofitのベースは末尾スラッシュ必須） */
    private fun normalize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
