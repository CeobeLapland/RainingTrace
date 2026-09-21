package com.rainingtrace.domain.npc

import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WorldCondition
import com.rainingtrace.domain.world.WorldState

/** 一次判定所需的一切，一次给全，条件实现不必再查库。 */
data class NpcTriggerContext(
    val npcId: String,
    val profile: NpcProfile,
    val presence: NpcPresence?,
    val worldState: WorldState,
    /** 自上次检查以来新增的足迹事件（水位窗口内）。 */
    val recentFootprints: List<FootprintEvent>,
    /** 天气**跃迁**到哪一种；null = 本次没有跃迁（含冷启动）。 */
    val weatherChangedTo: WeatherKind?,
    val nowEpochMs: Long,
    /** 这个 NPC 与玩家的当前关系/情绪状态。 */
    val state: NpcState,
    /** 解析足迹 payload 里的 placeId 用。 */
    val placesById: Map<String, Place>,
)

/**
 * 主动消息的触发条件。
 *
 * 时段/季节/天气的判定**复用 [WorldCondition]**（包一层 [World]），
 * 只新增"跃迁"与"玩家行为"这几类本作特有的条件，不重写已有的规则。
 */
sealed interface NpcTriggerCondition {

    /** 条件越具体越优先，与 `ResourceYieldRule.specificity` 同一思路。 */
    val specificity: Int

    fun isSatisfiedBy(context: NpcTriggerContext): Boolean

    /** 复用现成的世界状态条件（时段 / 季节 / 天气 / 时间窗 / 组合）。 */
    data class World(val condition: WorldCondition) : NpcTriggerCondition {
        override val specificity: Int get() = 1
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean =
            condition.isSatisfiedBy(context.worldState)
    }

    /** 天气**刚刚**变成这几种之一（不是"此刻在下雨"）。 */
    data class WeatherBecame(val kinds: Set<WeatherKind>) : NpcTriggerCondition {
        init {
            require(kinds.isNotEmpty()) { "WeatherBecame needs at least one kind" }
        }

        override val specificity: Int get() = 2
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean =
            context.weatherChangedTo != null && context.weatherChangedTo in kinds
    }

    /**
     * 玩家刚去过某类地点（水位窗口内）。
     *
     * 用 [PlaceType] 而不是 placeId：条件写的是"他刚去过湖边"这种内容语义，
     * 也不必让 domain 记住具体地点 id。
     */
    data class PlayerEnteredPlace(val placeType: PlaceType) : NpcTriggerCondition {
        override val specificity: Int get() = 2
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean =
            context.recentFootprints.any { event ->
                event.eventType == FootprintEventType.PLACE_OBSERVED &&
                    context.placesById[event.payload["placeId"]]?.type == placeType
            }
    }

    /** 玩家刚遇见某个 NPC。 */
    data class PlayerMetNpc(val npcId: String) : NpcTriggerCondition {
        override val specificity: Int get() = 2
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean =
            context.recentFootprints.any { event ->
                event.eventType == FootprintEventType.NPC_MET &&
                    event.payload["npcId"] == npcId
            }
    }

    /** 玩家已经 [days] 天没跟这个 NPC 说过话。 */
    data class PlayerIdleFor(val days: Int) : NpcTriggerCondition {
        init {
            require(days > 0) { "days must be positive: $days" }
        }

        override val specificity: Int get() = 1
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean {
            val last = context.state.lastInteractionAtEpochMs ?: return false
            return context.nowEpochMs - last >= days * DAY_MS
        }
    }

    /**
     * 玩家上一次约好了却没来（片 3）。
     *
     * 这条让"你没去"有了后果：他下次会提一句，而不是当作没发生过。
     */
    data class PlayerMissedCommitment(val npcId: String) : NpcTriggerCondition {
        override val specificity: Int get() = 2
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean =
            context.recentFootprints.any { event ->
                event.eventType == FootprintEventType.NPC_COMMITMENT_MISSED &&
                    event.payload["npcId"] == npcId
            }
    }

    /** 全部满足（AND）。 */
    data class All(val conditions: List<NpcTriggerCondition>) : NpcTriggerCondition {
        override val specificity: Int get() = conditions.sumOf { it.specificity }
        override fun isSatisfiedBy(context: NpcTriggerContext): Boolean =
            conditions.all { it.isSatisfiedBy(context) }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * 一条主动消息规则：条件命中 → 由 [npcId] 发一条 [text]。
 *
 * [text] 里可以用 `{place}` / `{activity}` 两个槽位，值来自他此刻的真实位置——
 * 所以"我在图书馆"这种话永远是真的。
 */
data class NpcProactiveRule(
    val id: String,
    val npcId: String,
    val condition: NpcTriggerCondition,
    val text: String,
    /** 同一规则多久内不重复；默认 4 小时。 */
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    /** 同条件组合下的额外优先级（确定性优先，并列时按 id 取字典序最后者）。 */
    val priority: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "rule id must not be blank" }
        require(npcId.isNotBlank()) { "rule npcId must not be blank" }
        require(text.isNotBlank()) { "rule text must not be blank" }
    }

    val specificity: Int get() = condition.specificity * 2 + priority

    companion object {
        const val DEFAULT_COOLDOWN_MS = 4L * 60 * 60 * 1000
    }
}

/** 规则表：加内容只加规则，不改引擎。 */
interface NpcProactiveRuleCatalog {
    val rules: List<NpcProactiveRule>
}
