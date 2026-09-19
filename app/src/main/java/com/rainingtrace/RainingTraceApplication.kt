package com.rainingtrace

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        registerActivityLifecycleCallbacks(ForegroundCallbacks(appContainer))
    }

    /**
     * 前后台状态驱动源：地图的处理流与后台记录服务都按它决定"该不该干活"。
     * 用 started/stopped 而不是 resumed/paused——可见性才是省电边界。
     */
    private class ForegroundCallbacks(
        private val container: AppContainer,
    ) : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) =
            container.foregroundState.onActivityStarted()

        override fun onActivityStopped(activity: Activity) =
            container.foregroundState.onActivityStopped()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
