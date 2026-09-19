package com.rainingtrace.platform.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rainingtrace.MainActivity
import com.rainingtrace.R
import com.rainingtrace.RainingTraceApplication
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.TrackingSettings
import com.rainingtrace.domain.track.TRACK_ZONE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 足迹记录前台服务（显式开关 + 低频 + 只写库）。
 *
 * **后台只做一件事**：按用户配置的间隔拿到位置 → RecordTrackPointUseCase 去噪 → 写 track_points。
 *
 * 明确不做（这是省电的边界，也是设计约束）：
 * 开雾、地图渲染、附近地点、记忆/图鉴查询、世界状态、天气与 NPC 交互。
 * 这些回到前台由 MapViewModel 按"迷雾水位"一次性补算，后台绝不持续运行。
 *
 * 生命周期：开关打开时由 AppContainer 拉起（应用还在前台，满足 Android 14 起
 * 不允许后台启动 location 类型前台服务的限制）；开关关闭 / 切到 Fake 模式 / 失去权限时自停。
 * 进程被杀后按 START_STICKY 重启，重启也走同一套自检。
 */
class TrackRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var settings = TrackingSettings()

    private var watchJob: Job? = null

    private val container get() = (application as RainingTraceApplication).appContainer

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // 通知里的"停止记录"：把开关写回 false，界面与监督者随之收敛。
            scope.launch {
                val current = container.settingsRepository.currentTracking()
                container.settingsRepository.setTracking(current.copy(enabled = false))
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // Android 要求 startForegroundService 后 5 秒内 startForeground。
        // 若升格失败（例如被系统从后台重启、限制了前台服务），必须立刻自停，
        // 否则会因超时被框架判成崩溃。
        if (!promoteToForeground(settings)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startWatching()
        return START_STICKY
    }

    override fun onDestroy() {
        watchJob?.cancel()
        scope.cancel()
        // 恢复前台节奏，别把低频档留给下次前台使用。
        runCatching { container.locationProvider.setPassiveIntervalMs(null) }
        super.onDestroy()
    }

    private fun startWatching() {
        watchJob?.cancel()
        watchJob = scope.launch {
            if (!canRecord()) {
                stopSelf()
                return@launch
            }
            // 1) 配置/定位模式变化：决定去留、频率、通知文案
            launch {
                combine(
                    container.settingsRepository.tracking,
                    container.settingsRepository.locationMode,
                ) { tracking, mode -> tracking to mode }
                    .collect { (tracking, mode) ->
                        settings = tracking
                        if (!tracking.enabled || mode != LocationMode.GPS) {
                            stopSelf()
                            return@collect
                        }
                        applyCadence()
                        updateNotification(tracking)
                    }
            }
            // 2) 前后台切换：切采集频率档
            launch {
                container.foregroundState.isForeground.collect { applyCadence() }
            }
            // 3) 记录（唯一写库路径）：仅后台 + 仅白天窗口内
            container.locationProvider.updates.collect { fix ->
                if (container.foregroundState.isForeground.value) return@collect
                if (!settings.allowsRecordingAt(localMinuteOfDay())) return@collect
                container.recordTrackPoint(fix)
            }
        }
    }

    /**
     * 采集频率跟随前后台：
     * - 前台：交回默认节奏（记录由地图侧处理，服务不抢活）；
     * - 后台 + 白天：用户配置的低频档；
     * - 后台 + 夜间：极稀疏档，只为维持服务，且不写任何点（白天窗口在写库前拦下）。
     */
    private fun applyCadence() {
        val foreground = container.foregroundState.isForeground.value
        val interval = when {
            foreground -> null
            settings.allowsRecordingAt(localMinuteOfDay()) -> settings.backgroundInterval.millis
            else -> NIGHT_INTERVAL_MS
        }
        container.locationProvider.setPassiveIntervalMs(interval)
    }

    private suspend fun canRecord(): Boolean {
        val tracking = runCatching { container.settingsRepository.currentTracking() }
            .getOrDefault(settings)
        val mode = runCatching { container.settingsRepository.currentLocationMode() }
            .getOrDefault(LocationMode.FAKE)
        settings = tracking
        return tracking.enabled && mode == LocationMode.GPS && hasLocationPermission()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun localMinuteOfDay(): Int {
        val now = container.clock.now().atZone(TRACK_ZONE)
        return now.hour * 60 + now.minute
    }

    private fun promoteToForeground(tracking: TrackingSettings): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        return runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(tracking), type)
        }.isSuccess
    }

    private fun updateNotification(tracking: TrackingSettings) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, buildNotification(tracking)) }
    }

    private fun buildNotification(tracking: TrackingSettings): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TrackRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val windowSuffix = if (tracking.daytimeOnly) {
            "（仅 ${tracking.dayWindow.label}）"
        } else {
            "（全天）"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rain_mark)
            .setContentTitle("雨迹 · 正在记录足迹")
            .setContentText(
                "回到桌面后每 ${tracking.backgroundInterval.label} 记一次位置$windowSuffix，只存本机",
            )
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .addAction(0, "停止记录", stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "足迹记录",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "后台低频记录位置，只在本机保存"
            },
        )
    }

    companion object {
        const val ACTION_STOP = "com.rainingtrace.action.STOP_TRACKING"

        private const val CHANNEL_ID = "rt_tracking"
        private const val NOTIFICATION_ID = 1001

        /** 夜间（白天窗口外且退到后台）的极稀疏档：维持服务，但不写点。 */
        private const val NIGHT_INTERVAL_MS = 15 * 60 * 1000L

        fun intent(context: Context): Intent = Intent(context, TrackRecordingService::class.java)
    }
}