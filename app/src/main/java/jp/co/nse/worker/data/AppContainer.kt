package jp.co.nse.worker.data

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        // 起動時に永続化済みのトークン・ベースURLをメモリへ展開する（同期）。
        runBlocking {
            val token = settings.tokenFlow.first()
            val baseUrl = settings.baseUrlFlow.first()
            settings.primeCache(token, baseUrl)
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

    /** マイページ（メイン色の変更等）を開くコールバック。AppNavが起動時に設定する */
    var openMyPage: (() -> Unit)? = null

    /** 担当工程マスタ画面を開くコールバック。AppNavが起動時に設定する */
    var openProcessAssignments: (() -> Unit)? = null

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
