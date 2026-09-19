package com.rainingtrace.domain.track

/**
 * 足迹记录的启停。实现是 location 类型的前台服务（platform 层）。
 *
 * 只做两件事：把服务拉起来 / 停掉；记录与否的唯一真相始终是
 * [com.rainingtrace.domain.settings.TrackingSettings.enabled]。
 */
interface TrackingController {
    fun start()
    fun stop()
}