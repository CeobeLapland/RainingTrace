package com.rainingtrace

import android.app.Application
import com.rainingtrace.core.common.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.maplibre.android.MapLibre

class RainingTraceApplication : Application() {

    /** 应用级协程作用域：定位提供方热切换等长生命周期任务。 */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 托管原型瓦片不需要 API key；自定义 style URI 走 demotiles
        MapLibre.getInstance(this)
        appContainer = AppContainer(this, applicationScope)
    }
}
