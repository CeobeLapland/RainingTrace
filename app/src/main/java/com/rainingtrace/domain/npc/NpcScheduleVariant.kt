package com.rainingtrace.domain.npc

import com.rainingtrace.domain.world.WorldCondition
import com.rainingtrace.domain.world.WorldState
import java.time.DayOfWeek

/**
 * 作息变体：同一套作息在不同日子/天气下的"另一版"。
 *
 * 这是"生病、旅游、出行、放假"这类动态变化的**通用底座**，不需要为每种情况
 * 单独写系统：
 * - **雨天**：`conditions = [WeatherIn(RAINY)]`，周野不跑广场改去食堂屋檐下；
 * - **周末**：`weekdays = [SATURDAY, SUNDAY]`，不排课、去自由地点；
 * - **旅游/外出**：`fromDateKey..toDateKey` + `schedule = []`（空 = 这段时间不在校）；
 * - **生病**：`fromDateKey..toDateKey` + 只留宿舍的作息，配一句"今天不太舒服"的活动文案。
 *
 * 三条口径（与 spawn / 主动消息同一思路）：
 * 1. **世界状态只做门控**：条件决定"这一版今天生不生效"，不参与随机挑选；
 * 2. **同一天同结果**：同一时刻永远选出同一个变体（priority → id 字典序）；
 * 3. **变体替换基础作息，承诺覆盖仍在最上层**——答应的事高于一切日程。
 */
data class NpcScheduleVariant(
    val id: String,
    /** 给玩家看的短标签（"雨天""外出"），档案页作息预览会用到。 */
    val label: String,
    /**
     * 这一版作息。**空列表 = 这段时间他不在**（旅游/住院），
     * 地图上不出现、消息里也会如实说找不到他。
     */
    val schedule: List<NpcScheduleEntry> = emptyList(),
    /** 生效起始日期（yyyy-MM-dd，含端点）；null = 不限。 */
    val fromDateKey: String? = null,
    /** 生效结束日期（yyyy-MM-dd，含端点）；null = 不限。 */
    val toDateKey: String? = null,
    /** 生效星期（空 = 不限）。 */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** 世界条件，全部满足才生效（AND）；空 = 不限。 */
    val conditions: List<WorldCondition> = emptyList(),
    /** 越大越优先；并列时按 id 字典序取大者（确定性）。 */
    val priority: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "variant id must not be blank" }
        require(label.isNotBlank()) { "variant label must not be blank: $id" }
    }

    /** 这一版此刻生不生效（只做门控，不含优先级比较）。 */
    fun appliesAt(state: WorldState): Boolean {
        val dateKey = state.localDate.toString()
        if (fromDateKey != null && dateKey < fromDateKey) return false
        if (toDateKey != null && dateKey > toDateKey) return false
        if (weekdays.isNotEmpty() && state.localDate.dayOfWeek !in weekdays) return false
        return conditions.all { it.isSatisfiedBy(state) }
    }
}

/**
 * 选出此刻生效的那一版；没有匹配的返回 null（用基础作息）。
 *
 * 确定性：priority 降序 → id 字典序**大者优先**，与 `SpawnPlanner` / 主动消息规则同一口径，
 * 所以内容里两条变体同时命中时不会"每次刷新换一个"。
 */
fun selectScheduleVariant(profile: NpcProfile, state: WorldState): NpcScheduleVariant? =
    profile.scheduleVariants
        .filter { it.appliesAt(state) }
        .maxWithOrNull(compareBy({ it.priority }, { it.id }))
