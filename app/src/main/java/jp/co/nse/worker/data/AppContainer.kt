package jp.co.nse.worker.data

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * アプリ全体で共有する依存関係を手動で組み立てる軽量コンテナ。
 * ベースURLが変更された場合は [rebuildApi] でRetrofitを作り直す。
 */
class AppContainer(context: Context) {

    val settings = SettingsStore(context)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    init {
        // 起動時に永続化済みのトークン・ベースURLをメモリへ展開する。
        // 以前はrunBlockingでメインスレッドを同期的にブロックしていたが、Application.onCreate()内で
        // 実行されるためDataStoreの読み込みが遅い端末・タイミングでは起動直後にANR（白画面フリーズ）
        // を招く恐れがあった。cachedToken/cachedBaseUrlは未取得時も安全な既定値を返すため、
        // 読み込みは非同期にし、完了後にrebuildApi()でRetrofitへ反映する。
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val token = settings.tokenFlow.first()
            val baseUrl = settings.baseUrlFlow.first()
            settings.primeCache(token, baseUrl)
            rebuildApi()
        }
    }

    @Volatile
    private var api: ApiService = buildApi()

    /** 常に最新のApiServiceを返すプロバイダ（ベースURL変更に追従） */
    val apiProvider: () -> ApiService = { api }

    val authRepository = AuthRepository(apiProvider, settings)
    val workerRepository = WorkerRepository(apiProvider)
    val managerRepository = ManagerRepository(apiProvider)
    val updateRepository = UpdateRepository(apiProvider)
    val notificationCenter = NotificationCenter(workerRepository)

    /**
     * 工程IDを渡すと作業詳細画面へ遷移するコールバック。AppNavが起動時に設定する。
     * 通知一覧など、NavControllerを直接持たないUIから画面遷移するための橋渡し。
     */
    var openTask: ((processId: Int) -> Unit)? = null

    /** マイページ（作業実績）を開くコールバック。AppNavが起動時に設定する */
    var openMyPage: (() -> Unit)? = null

    /** 設定（メインカラー・文字の見た目）を開くコールバック。AppNavが起動時に設定する */
    var openSettings: (() -> Unit)? = null

    /** ダッシュボード画面を開くコールバック。AppNavが起動時に設定する */
    var openDashboard: (() -> Unit)? = null

    /** 担当工程マスタ画面を開くコールバック。AppNavが起動時に設定する */
    var openProcessAssignments: (() -> Unit)? = null

    /** 在庫画面を開くコールバック。AppNavが起動時に設定する */
    var openInventory: (() -> Unit)? = null

    /** 受注照会画面を開くコールバック。AppNavが起動時に設定する */
    var openOrderInquiry: (() -> Unit)? = null

    /** スキャンデータ画面を開くコールバック。AppNavが起動時に設定する */
    var openScanData: (() -> Unit)? = null

    /** 不具合・要望の報告画面を開くコールバック。AppNavが起動時に設定する */
    var openReport: (() -> Unit)? = null

    /** 届いた報告の一覧画面を開くコールバック。AppNavが起動時に設定する */
    var openReportList: (() -> Unit)? = null

    /**
     * ヘッダーのロゴタップで作業一覧（ホーム）まで戻るコールバック。AppNavが起動時に設定する。
     * ホーム画面内のタブ（作業一覧・割り当て・受注一覧・出荷カレンダー）自体は
     * このコールバックを使わず、タブ切替（pagerState）で直接作業一覧タブへ移動する。
     */
    var openHome: (() -> Unit)? = null

    /**
     * ヘッダーメニューのタブ切替項目（作業一覧・割り当て・受注一覧・在庫・出荷カレンダー）から、
     * ホーム画面の指定タブへ遷移するコールバック。AppNavが起動時に設定する。
     * タブ名は[jp.co.nse.worker.ui.HomeTab.name]の文字列（例: "TASKS"）。
     * ホーム画面内のタブ自体はこのコールバックを使わず、タブ切替（pagerState）で直接移動する。
     */
    var openHomeTab: ((tabKey: String) -> Unit)? = null

    fun rebuildApi() {
        api = buildApi()
    }

    private fun buildApi(): ApiService {
        val authInterceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
            settings.cachedToken?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(settings.cachedBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiService::class.java)
    }
}
