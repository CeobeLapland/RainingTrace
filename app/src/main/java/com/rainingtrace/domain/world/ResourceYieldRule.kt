package com.rainingtrace.domain.world

import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType

/**
 * 地点动作的产出规则：**世界状态 → 产出**的唯一通道（GDD §07/§09）。
 *
 * 之前产出硬编码在用例里（观察必得"观察记录"），所以"雨天湖边掉湖泊记忆碎片"
 * 这类内容没有可写的位置。现在一律走这张表：用例只负责
 * 「取世界状态快照 → 过规则 → 结算」，加内容只加规则、不动逻辑。
 *
 * 与 GDD §21 对齐：规则是纯数据（条件可枚举、可序列化），
 * 将来搬进开发者编辑器即可，无需改核心代码。
 */
data class ResourceYieldRule(
    val id: String,
    val resourceId: String,
    val action: PlaceActionType,
    /** null = 任意地点类型。 */
    val placeType: PlaceType? = null,
    /** 全部满足才出这个产出；空 = 无条件（保底规则）。 */
    val conditions: List<WorldCondition> = emptyList(),
    val amount: Int = 1,
    /** 同一地点同一规则的重试间隔；每条规则各自冷却，罕见机会不被保底规则挡住。 */
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    init {
        require(id.isNotBlank()) { "rule id must not be blank" }
        require(resourceId.isNotBlank()) { "resourceId must not be blank" }
        require(amount > 0) { "amount must be positive, got $amount" }
        require(cooldownMs >= 0) { "cooldown must not be negative, got $cooldownMs" }
    }

    /**
     * 规则具体度：条件越多越优先，其次"绑定了地点类型"的优先于泛用的。
     * 加权（条件×2 + 地点）让"雨天限定的湖边苔痕"稳稳压过泛用保底规则，
     * 加内容时不需要回头调优先级数值。
     */
    val specificity: Int get() = conditions.size * 2 + if (placeType != null) 1 else 0

    fun matches(place: Place, state: WorldState): Boolean {
        if (placeType != null && place.type != placeType) return false
        if (action !in place.actions) return false
        return conditions.allSatisfiedBy(state)
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 10 * 60 * 1000L
    }
}

/** 产出规则目录：Fake/内存实现，P1 起随内容一起由服务端下发。 */
interface ResourceYieldRuleCatalog {
    fun rulesFor(action: PlaceActionType): List<ResourceYieldRule>
}

class InMemoryResourceYieldRuleCatalog(
    rules: List<ResourceYieldRule>,
) : ResourceYieldRuleCatalog {

    private val byAction: Map<PlaceActionType, List<ResourceYieldRule>> = rules.groupBy { it.action }

    override fun rulesFor(action: PlaceActionType): List<ResourceYieldRule> =
        byAction[action].orEmpty()

    companion object {
        // ---- 观察 ----

        /** 保底：任何地点认真看一次 → 观察记录（MVP 原有行为）。 */
        val OBSERVE_BASE = ResourceYieldRule(
            id = "rule.observe.base",
            resourceId = InMemoryResourceCatalog.OBSERVATION_RECORD.id,
            action = PlaceActionType.OBSERVE,
        )

        /**
         * 第一个"世界状态影响产出"的例子（GDD §26 样例、11_MVP §3 的"雨后水面异常反光"）：
         * 雨天的湖边观察 → 湖泊记忆碎片。冷却比保底长，避免反复刷。
         */
        val OBSERVE_RAINY_LAKE = ResourceYieldRule(
            id = "rule.observe.rainy_lake",
            resourceId = InMemoryResourceCatalog.LAKE_MEMORY_FRAGMENT.id,
            action = PlaceActionType.OBSERVE,
            placeType = PlaceType.LAKE,
            conditions = listOf(RAINY_WEATHER),
            cooldownMs = 30 * 60 * 1000L,
        )

        /**
         * 异常示例（GDD §15/§26）：雨夜湖边的反光里多出一条鱼影。
         * 条件最多 → 优先于上面两条，玩家会先看到它。
         */
        val OBSERVE_MIRROR_MOON_FISH = ResourceYieldRule(
            id = "rule.observe.mirror_moon_fish",
            resourceId = InMemoryResourceCatalog.MIRROR_MOON_FISH_SHADOW.id,
            action = PlaceActionType.OBSERVE,
            placeType = PlaceType.LAKE,
            conditions = listOf(
                RAINY_WEATHER,
                WorldCondition.TimeOfDayIn(setOf(TimeOfDay.NIGHT)),
            ),
            cooldownMs = 120 * 60 * 1000L,
        )

        // ---- 采集：自然 ----

        val COLLECT_BASE = ResourceYieldRule(
            id = "rule.collect.base",
            resourceId = InMemoryResourceCatalog.RAIN_MOSS.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.GARDEN,
        )

        val COLLECT_RAIN_MOSS = ResourceYieldRule(
            id = "rule.collect.rain_moss",
            resourceId = InMemoryResourceCatalog.RAIN_MOSS.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.GARDEN,
            conditions = listOf(RAINY_WEATHER),
            amount = 2,
        )

        val COLLECT_REED = ResourceYieldRule(
            id = "rule.collect.reed",
            resourceId = InMemoryResourceCatalog.REED_LEAF.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.LAKE,
        )

        val COLLECT_PINE_CONE = ResourceYieldRule(
            id = "rule.collect.pine_cone",
            resourceId = InMemoryResourceCatalog.PINE_CONE.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.GARDEN,
            conditions = listOf(WorldCondition.SeasonIn(setOf(Season.AUTUMN))),
            amount = 2,
        )

        val COLLECT_PETAL = ResourceYieldRule(
            id = "rule.collect.petal",
            resourceId = InMemoryResourceCatalog.PETAL.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.GARDEN,
            conditions = listOf(WorldCondition.SeasonIn(setOf(Season.SPRING))),
            amount = 2,
        )

        /** 黎明限定：草叶上的露水过了早上就没了。 */
        val COLLECT_DEW_GRASS = ResourceYieldRule(
            id = "rule.collect.dew_grass",
            resourceId = InMemoryResourceCatalog.LAWN_DEW_GRASS.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.GARDEN,
            conditions = listOf(WorldCondition.TimeOfDayIn(setOf(TimeOfDay.DAWN))),
        )

        /** 雪天限定：只在冬天的雪面上结成。 */
        val COLLECT_FROST = ResourceYieldRule(
            id = "rule.collect.frost",
            resourceId = InMemoryResourceCatalog.FROST_PATTERN.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.GARDEN,
            conditions = listOf(
                WorldCondition.WeatherIn(setOf(WeatherKind.SNOW)),
                WorldCondition.SeasonIn(setOf(Season.WINTER)),
            ),
            cooldownMs = 60 * 60 * 1000L,
        )

        // ---- 采集：知识 / 文化 ----

        val COLLECT_SHELF_CARD = ResourceYieldRule(
            id = "rule.collect.shelf_card",
            resourceId = InMemoryResourceCatalog.LIBRARY_SHELF_CARD.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.LIBRARY,
        )

        /** 图书馆的阅读随记：只在自己也静下来读完一段之后才有（白天时段表达"坐下来读"）。 */
        val COLLECT_READING_NOTE = ResourceYieldRule(
            id = "rule.collect.reading_note",
            resourceId = InMemoryResourceCatalog.READING_NOTE.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.LIBRARY,
            conditions = listOf(WorldCondition.TimeOfDayIn(setOf(TimeOfDay.DAY, TimeOfDay.DUSK))),
        )

        val COLLECT_MENU_TICKET = ResourceYieldRule(
            id = "rule.collect.menu_ticket",
            resourceId = InMemoryResourceCatalog.CANTEEN_MENU_TICKET.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.CANTEEN,
        )

        val COLLECT_PLAZA_FLYER = ResourceYieldRule(
            id = "rule.collect.plaza_flyer",
            resourceId = InMemoryResourceCatalog.PLAZA_FLYER.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.PLAZA,
        )

        val COLLECT_DORM_SCRAP = ResourceYieldRule(
            id = "rule.collect.dorm_scrap",
            resourceId = InMemoryResourceCatalog.DORM_NOTE_SCRAP.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.DORM,
        )

        /** 夜水声：入夜后才听得出来。 */
        val COLLECT_NIGHT_WATER_SOUND = ResourceYieldRule(
            id = "rule.collect.night_water_sound",
            resourceId = InMemoryResourceCatalog.NIGHT_WATER_SOUND.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.LAKE,
            conditions = listOf(WorldCondition.TimeOfDayIn(setOf(TimeOfDay.NIGHT))),
            cooldownMs = 45 * 60 * 1000L,
        )

        // ---- 采集：自然资源点（林 / 丛 / 菌） ----

        val COLLECT_BERRY = ResourceYieldRule(
            id = "rule.collect.berry",
            resourceId = InMemoryResourceCatalog.WILD_BERRY.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.BERRY_BUSH,
        )

        /** 菌子的保底（干的天气也能翻出一两个）。 */
        val COLLECT_MUSHROOM = ResourceYieldRule(
            id = "rule.collect.mushroom",
            resourceId = InMemoryResourceCatalog.WILD_MUSHROOM.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.MUSHROOM_PATCH,
        )

        /** 雨后菌子多：同一处能翻出两份。 */
        val COLLECT_MUSHROOM_AFTER_RAIN = ResourceYieldRule(
            id = "rule.collect.mushroom_after_rain",
            resourceId = InMemoryResourceCatalog.WILD_MUSHROOM.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.MUSHROOM_PATCH,
            conditions = listOf(RAINY_WEATHER),
            amount = 2,
        )

        val COLLECT_APPLE = ResourceYieldRule(
            id = "rule.collect.apple",
            resourceId = InMemoryResourceCatalog.GREEN_APPLE.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.ORCHARD,
        )

        /** 秋天果子沉：一次能摘两个。 */
        val COLLECT_APPLE_AUTUMN = ResourceYieldRule(
            id = "rule.collect.apple_autumn",
            resourceId = InMemoryResourceCatalog.GREEN_APPLE.id,
            action = PlaceActionType.COLLECT,
            placeType = PlaceType.ORCHARD,
            conditions = listOf(WorldCondition.SeasonIn(setOf(Season.AUTUMN))),
            amount = 2,
        )

        val DEFAULT = listOf(
            OBSERVE_BASE,
            OBSERVE_RAINY_LAKE,
            OBSERVE_MIRROR_MOON_FISH,
            COLLECT_BASE,
            COLLECT_RAIN_MOSS,
            COLLECT_REED,
            COLLECT_PINE_CONE,
            COLLECT_PETAL,
            COLLECT_DEW_GRASS,
            COLLECT_FROST,
            COLLECT_SHELF_CARD,
            COLLECT_READING_NOTE,
            COLLECT_MENU_TICKET,
            COLLECT_PLAZA_FLYER,
            COLLECT_DORM_SCRAP,
            COLLECT_NIGHT_WATER_SOUND,
            COLLECT_BERRY,
            COLLECT_MUSHROOM,
            COLLECT_MUSHROOM_AFTER_RAIN,
            COLLECT_APPLE,
            COLLECT_APPLE_AUTUMN,
        )
    }
}