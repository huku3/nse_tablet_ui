package jp.co.nse.worker

import android.app.Application
import android.content.Context
import jp.co.nse.worker.data.AppContainer

class NseApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Composable等から共有コンテナを取得するヘルパー */
val Context.appContainer: AppContainer
    get() = (applicationContext as NseApp).container
