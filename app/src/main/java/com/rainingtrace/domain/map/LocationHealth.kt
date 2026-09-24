package com.rainingtrace.domain.map

import kotlinx.coroutines.flow.StateFlow

/**
 * 定位"卡住了"的可见信号，给 UI 用。
 *
 * 单独一个接口而不是塞进 [LocationProvider]：Fake 实现没有这个概念，
 * 不该被迫实现一个恒为 false 的属性。MagicOS 把后台定位掐掉时这里会亮起来，
 * 界面据此告诉玩家去系统设置放行——**代码修不了系统的省电策略，但能如实说出来**。
 */
interface LocationHealth {

    /** true = 采集还在跑，但很久没有回调，已经试过补救。 */
    val stalled: StateFlow<Boolean>
}