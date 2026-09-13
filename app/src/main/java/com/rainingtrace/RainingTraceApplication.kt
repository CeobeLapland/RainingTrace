package com.rainingtrace

import android.app.Application
import com.rainingtrace.core.common.AppContainer
import org.maplibre.android.MapLibre

class RainingTraceApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 托管原型瓦片不需要 API key；自定义 style URI 走 demotiles
        MapLibre.getInstance(this)
        appContainer = AppContainer(this)
    }
}
