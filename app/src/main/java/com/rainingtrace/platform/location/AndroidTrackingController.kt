package com.rainingtrace.platform.location

import android.content.Context
import androidx.core.content.ContextCompat
import com.rainingtrace.domain.track.TrackingController

/**
 * 用 location 类型前台服务实现足迹记录的启停。
 *
 * start 必须发生在应用可见时（Android 14 起禁止从后台启动 location 前台服务），
 * 调用方（AppContainer 的监督者）负责只在开关打开且处于前台时调用。
 */
class AndroidTrackingController(
    private val context: Context,
) : TrackingController {

    override fun start() {
        runCatching {
            ContextCompat.startForegroundService(context, TrackRecordingService.intent(context))
        }
    }

    override fun stop() {
        runCatching { context.stopService(TrackRecordingService.intent(context)) }
    }
}