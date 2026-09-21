package com.rainingtrace.domain.npc

import com.rainingtrace.core.time.WORLD_ZONE
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.world.WorldStateProvider

/**
 * NPC 主动消息：一次检查最多发一条。
 *
 * **只在应用可见时运行**（引擎本身不判断前后台，由调用方的循环门控）。
 * 熄屏后台生成需要复用前台服务或 WorkManager，而 HANDOFF_4 §6 明确
 * "后台服务只写库"，所以这里不做——回前台补算一次即可，tick 本身幂等。
 *
 * 三重限流，避免变成骚扰：规则冷却（默认 4h）+ 全局最小间隔（2h）+
 * 每日上限（由玩家预设决定）+ 免打扰时段（07:00–23:00）。
 */
class NpcProactiveMessageUseCase(
    private val clock: WorldClock,
    private val npcRepository: NpcRepository,
    private val placeRepository: PlaceRepository,
    private val npcPresence: NpcPresenceUseCase,
    private val rules: NpcProactiveRuleCatalog,
    private val stateRepository: NpcStateRepository,
    /** 与片 3 共用的"写消息 + 留足迹"。 */
    private val messageWriter: NpcMessageWriter,
    private val footprintRepository: FootprintRepository,
    private val worldState: WorldStateProvider,
    private val settings: AppSettingsRepository,
) {

    /**
     * 上一次检查时的天气，用来判断"跃迁"。
     *
     * 冷启动是 null，**不把开 app 当成一次天气变化**——否则每次启动都会触发天气规则。
     */
    private var lastWeatherKind: com.rainingtrace.domain.world.WeatherKind? = null

    /**
     * 足迹水位：上次检查已看到的时间点。
     *
     * 只需内存：后台唯一写库的是 `TrackRecordingService`（只写轨迹点、不写足迹），
     * 所以进程重启后把水位置到 now 不会漏掉任何事件。与迷雾水位同一口径。
     */
    private var watermarkMs: Long = clock.now().toEpochMilli()

    /** 返回这次发出的消息；没有该发的就返回 null。 */
    suspend operator fun invoke(): NpcMessage? {
        val now = clock.now().toEpochMilli()
        val world = worldState.current()

        // 天气跃迁（先算，无论后面是否早退都要推进，否则会一直以为是"刚变"）。
        val weatherNow = world.weather.kind
        val weatherChangedTo = lastWeatherKind?.takeIf { it != weatherNow }?.let { weatherNow }
        lastWeatherKind = weatherNow

        // 水位：只处理上次之后新增的足迹。
        val recent = footprintRepository.eventsBetween(watermarkMs + 1, now)
        watermarkMs = now

        val messageSettings = settings.currentNpcMessages()
        if (messageSettings.proactiveLevel.dailyLimit <= 0) return null
        if (world.minuteOfDay !in QUIET_FROM_MINUTE until QUIET_UNTIL_MINUTE) return null
        if (dailySentCount(world, now) >= messageSettings.proactiveLevel.dailyLimit) return null
        if (!gapSinceLastMessageIsEnough(now)) return null

        val profilesById = npcRepository.all().associateBy { it.id }
        val placesById = placeRepository.all().associateBy { it.id }

        val candidate = rules.rules
            // 用 List 的 filter/mapNotNull（inline，可以调 suspend）而不是 asSequence。
            .filter { !onCooldown(it, now) }
            .mapNotNull { rule ->
                val profile = profilesById[rule.npcId] ?: return@mapNotNull null
                val presence = npcPresence.presenceOf(rule.npcId, world) ?: return@mapNotNull null
                val context = NpcTriggerContext(
                    npcId = rule.npcId,
                    profile = profile,
                    presence = presence,
                    worldState = world,
                    recentFootprints = recent,
                    weatherChangedTo = weatherChangedTo,
                    nowEpochMs = now,
                    state = stateRepository.stateOf(rule.npcId),
                    placesById = placesById,
                )
                if (!rule.condition.isSatisfiedBy(context)) return@mapNotNull null
                rule to context
            }
            // 一次只发一条：条件越具体越优先，并列时按 id 取字典序最后者（确定性优先）。
            .maxWithOrNull(
                compareBy({ it.first.specificity }, { it.first.id }),
            )
            ?: return null

        val (rule, context) = candidate
        val presence = context.presence ?: return null
        val text = fillSlots(
            rule.text,
            mapOf("place" to presence.placeName, "activity" to presence.activity),
        )
        // 规则文案也过一遍守门人：内容写错不该把承诺词发出去。
        if (!DialogueValidator.validate(text)) return null

        // 与片 3 共用同一套"写消息 + 留足迹"（冷却与每日上限都读这条足迹）。
        return messageWriter.write(
            npcId = rule.npcId,
            coordinate = presence.coordinate,
            text = text,
            eventType = FootprintEventType.NPC_MESSAGE_SENT,
            payload = mapOf("ruleId" to rule.id),
            nowEpochMs = now,
        )
    }

    /** 同一规则在冷却窗口内只发一次。 */
    private suspend fun onCooldown(rule: NpcProactiveRule, nowEpochMs: Long): Boolean {
        if (rule.cooldownMs <= 0L) return false
        return footprintRepository.eventsBetween(nowEpochMs - rule.cooldownMs, nowEpochMs)
            .any { event ->
                event.eventType == FootprintEventType.NPC_MESSAGE_SENT &&
                    event.payload["ruleId"] == rule.id
            }
    }

    private suspend fun dailySentCount(
        world: com.rainingtrace.domain.world.WorldState,
        nowEpochMs: Long,
    ): Int {
        val dayStart = world.localDate.atStartOfDay(WORLD_ZONE).toInstant().toEpochMilli()
        return footprintRepository.eventsBetween(dayStart, nowEpochMs)
            .count { it.eventType == FootprintEventType.NPC_MESSAGE_SENT }
    }

    /** 全局最小间隔：多规则同时命中也别连着轰炸。 */
    private suspend fun gapSinceLastMessageIsEnough(nowEpochMs: Long): Boolean =
        footprintRepository.eventsBetween(nowEpochMs - MIN_GAP_MS, nowEpochMs)
            .none { it.eventType == FootprintEventType.NPC_MESSAGE_SENT }

    private fun fillSlots(template: String, slots: Map<String, String>): String {
        var result = template
        slots.forEach { (key, value) -> result = result.replace("{$key}", value) }
        return result
    }

    private companion object {
        /** 免打扰：这个区间之外不发。 */
        const val QUIET_FROM_MINUTE = 7 * 60
        const val QUIET_UNTIL_MINUTE = 23 * 60

        /** 两条主动消息之间的全局最小间隔。 */
        const val MIN_GAP_MS = 2L * 60 * 60 * 1000
    }
}
