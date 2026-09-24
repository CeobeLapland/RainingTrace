package com.rainingtrace.domain.world

/**
 * 上一次成功拿到的真实天气，用来跨冷启动。
 *
 * 为什么值得落盘：不缓存的话，冷启动到首次拉取成功之间只能显示占位值——
 * 断网时就**永远**停在那里。更要紧的是，天气如果比 NPC 引擎的首个 tick 到得晚，
 * "天气跃迁"会把这次变化当成一次真实跃迁，冒出一条凭空的"天气变了"消息；
 * 命中缓存正好消掉这个边界。
 *
 * 这是**系统记账**，不是用户偏好，所以没有并进 `AppSettingsRepository`。
 */
data class WeatherCacheEntry(
    val state: WeatherState,
    /** 拿到这个值的时刻；UI 要显示"上次更新 HH:mm"。 */
    val fetchedAtEpochMs: Long,
)

interface WeatherCache {

    suspend fun load(): WeatherCacheEntry?

    suspend fun save(entry: WeatherCacheEntry)

    companion object {
        /** 不缓存的实现（测试与"还没接存储"的场景）。 */
        val NONE = object : WeatherCache {
            override suspend fun load(): WeatherCacheEntry? = null

            override suspend fun save(entry: WeatherCacheEntry) = Unit
        }
    }
}