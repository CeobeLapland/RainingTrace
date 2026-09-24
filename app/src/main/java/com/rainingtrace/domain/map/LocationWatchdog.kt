package com.rainingtrace.domain.map

/** 定位静默太久时，下一步该做什么。 */
enum class LocationWatchdogAction {
    /** 还正常，接着等。 */
    WAIT,

    /** 先探一次系统缓存的最后位置（便宜，常常立刻就有）。 */
    PROBE_LAST_KNOWN,

    /** 重发一次采集请求（同一个引擎，绝不降级换源）。 */
    RE_REQUEST,

    /** 补救都没用了：明确告诉玩家去放行后台定位。 */
    WARN,
}

/** 超过这个时长没回调就开始补救。 */
const val LOCATION_PROBE_AFTER_MS = 90_000L

/** 探过缓存位置之后还是没动静，就重发请求。 */
const val LOCATION_RE_REQUEST_AFTER_MS = 180_000L

/** 到这一步就不是"偶然稀疏"了，得让玩家知道。 */
const val LOCATION_WARN_AFTER_MS = 300_000L

/**
 * 定位看门狗的决策（纯函数）。
 *
 * 只处理一个现实：MagicOS 之类的省电策略会把后台定位掐掉，回调变得极稀。
 * 代码能做的不是"修好系统"，而是**发现它、试着救一次、然后如实告诉玩家**。
 *
 * 判定**必须按优先级短路**（而不是按区间匹配）：同一个静默区间里每分钟 tick 一次，
 * 区间匹配会让每个 tick 都返回同一个动作。所以"已经做过"由调用方用标志位表达
 * （见 [LocationWatchdogState]），而不是由时长区间表达。
 */
fun locationWatchdogAction(
    silentForMs: Long,
    probeDone: Boolean,
    reRequestDone: Boolean,
): LocationWatchdogAction {
    if (silentForMs < LOCATION_PROBE_AFTER_MS) return LocationWatchdogAction.WAIT
    if (!probeDone) return LocationWatchdogAction.PROBE_LAST_KNOWN
    if (silentForMs < LOCATION_RE_REQUEST_AFTER_MS) return LocationWatchdogAction.WAIT
    if (!reRequestDone) return LocationWatchdogAction.RE_REQUEST
    if (silentForMs < LOCATION_WARN_AFTER_MS) return LocationWatchdogAction.WAIT
    return LocationWatchdogAction.WARN
}

/**
 * 一轮静默期里的"已经试过什么"。放在 domain 是为了能单测：
 * 接线处（`AndroidLocationProvider`）只需要在**收到 fix / stop / 切出低频档**时调 [reset]。
 *
 * 用 [consume] 而不是裸调 [locationWatchdogAction]：它会在返回动作的同时把标志置位，
 * 所以调用方忘了手动置位也不会重复执行（这正是最容易出的那个 bug）。
 */
class LocationWatchdogState {

    var probeDone: Boolean = false
        private set

    var reRequestDone: Boolean = false
        private set

    /** 收到任何一次定位、或者停采/换档之后调用：下一轮静默期从头补救。 */
    fun reset() {
        probeDone = false
        reRequestDone = false
    }

    /** 取一次决策并推进状态。 */
    fun consume(silentForMs: Long): LocationWatchdogAction {
        val action = locationWatchdogAction(silentForMs, probeDone, reRequestDone)
        when (action) {
            LocationWatchdogAction.PROBE_LAST_KNOWN -> probeDone = true
            LocationWatchdogAction.RE_REQUEST -> reRequestDone = true
            LocationWatchdogAction.WAIT, LocationWatchdogAction.WARN -> Unit
        }
        return action
    }
}