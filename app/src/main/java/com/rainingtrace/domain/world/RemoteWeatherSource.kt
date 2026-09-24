package com.rainingtrace.domain.world

import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.map.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * 真实天气来源：定期向 [WeatherApi] 拉一次，**手动覆盖优先**。
 *
 * 四条"别浪费"的规则（网络请求是这个 app 里最贵的东西）：
 * 1. **没有定位 fix 时不拉**——不知道问哪儿；
 * 2. **非前台时不拉**——与世界状态层同一个省电口径；
 * 3. **手动覆盖生效时不拉**——玩家已经自己定了天气，后台还在拉就是白烧配额；
 * 4. 拉取失败**保留上一次好值**，并把 [WeatherStatus.isRetrying] 置起来让人看见。
 *
 * 轮询写成**冷流**并作为 [weather] 的 upstream（`WhileSubscribed`），
 * 所以没人看的时候它会自然停下——不需要另建一个永不退出的 `launch`。
 */
class RemoteWeatherSource(
    private val api: WeatherApi,
    private val locationProvider: LocationProvider,
    /** 用 `StateFlow<Boolean>` 而不是 `AppForegroundState`：domain 不认识 core，且测试更好写。 */
    private val isForeground: StateFlow<Boolean>,
    private val clock: WorldClock,
    private val scope: CoroutineScope,
    private val cache: WeatherCache = WeatherCache.NONE,
    /**
     * 冷启动先用的值（调用方 `runBlocking` 从缓存读出）；null = 还从未成功拿到过真实天气。
     *
     * 命中缓存能让地图一开就是真值，也顺手消掉"天气比 NPC 引擎首个 tick 到得晚，
     * 结果冒出一条凭空的天气跃迁消息"那个边界。
     */
    private val initial: WeatherState? = null,
) : WeatherSource {

    private val _manualOverride = MutableStateFlow<WeatherState?>(null)
    override val manualOverride: StateFlow<WeatherState?> = _manualOverride.asStateFlow()

    private val _status = MutableStateFlow(
        WeatherStatus(
            lastUpdatedAtEpochMs = initial?.let { clock.now().toEpochMilli() },
            isRetrying = initial == null,
        ),
    )
    override val status: StateFlow<WeatherStatus> = _status.asStateFlow()

    /** 最近一次拿到的真实天气；还没成功过就用中性占位值（不是"晴"）。 */
    private val remote = MutableStateFlow(initial ?: PLACEHOLDER)

    override val weather: StateFlow<WeatherState> = combine(_manualOverride, pollRemote()) {
            override, real -> override ?: real
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = _manualOverride.value ?: remote.value,
    )

    override fun setOverride(state: WeatherState?) {
        _manualOverride.value = state
    }

    private fun pollRemote(): Flow<WeatherState> = flow {
        while (true) {
            val coordinate = locationProvider.latest?.coordinate
            val paused = _manualOverride.value != null || !isForeground.value
            val fetched = if (paused || coordinate == null) null else api.currentWeather(coordinate)
            // "暂停"不算失败——只有真的问了却没拿到才算。
            val failed = !paused && coordinate != null && fetched == null

            if (fetched != null) {
                remote.value = fetched
                _status.value = WeatherStatus(clock.now().toEpochMilli(), isRetrying = false)
                // 成功就把这一刻记下来，冷启动直接用，不用等下一次拉取。
                cache.save(
                    WeatherCacheEntry(state = fetched, fetchedAtEpochMs = clock.now().toEpochMilli()),
                )
            } else {
                _status.value = _status.value.copy(isRetrying = failed)
            }
            emit(remote.value)
            delay(if (fetched != null) REFRESH_MS else RETRY_MS)
        }
    }

    private companion object {
        /** 与 API 自己给的 `current.interval = 900` 对齐：天气不会比这更勤地变。 */
        const val REFRESH_MS = 15 * 60 * 1000L

        /** 失败或被暂停后的重试间隔。不做退避曲线：固定值够用，也更容易理解。 */
        const val RETRY_MS = 60 * 1000L

        const val SUBSCRIPTION_TIMEOUT_MS = 5 * 60 * 1000L

        /**
         * 还没拿到真实天气时的占位值：**多云**而不是晴。
         * "不知道"说成"晴"会让雨天才有的内容看起来像 bug；多云至少不说谎。
         */
        val PLACEHOLDER = weatherPreset(WeatherKind.CLOUDY)
    }
}