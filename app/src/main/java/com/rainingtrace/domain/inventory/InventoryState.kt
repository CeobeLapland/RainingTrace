package com.rainingtrace.domain.inventory

import com.rainingtrace.core.time.WorldClock

/**
 * RT-DOM-005: 库存条目与库存状态。
 *
 * Inventory = 当前真相；ItemAcquiredEvent = 历史（05_领域模型 §4）。
 * MVP 阶段客户端本地持有；P1 接服务端后奖励由服务器确认。
 */
data class InventoryItem(
    val resourceId: String,
    val quantity: Int,
    val firstAcquiredAtEpochMs: Long,
    val lastAcquiredAtEpochMs: Long,
) {
    init {
        require(quantity > 0) { "quantity must be positive, got $quantity" }
    }
}

data class InventoryState(
    val items: Map<String, InventoryItem> = emptyMap(),
) {
    fun totalKinds(): Int = items.size

    fun quantityOf(resourceId: String): Int = items[resourceId]?.quantity ?: 0
}

sealed interface AddItemResult {
    data class Success(val state: InventoryState, val newQuantity: Int) : AddItemResult
    data class Rejected(val reason: Reason) : AddItemResult

    enum class Reason { NON_POSITIVE_QUANTITY, UNKNOWN_RESOURCE }
}

sealed interface RemoveItemResult {
    data class Success(val state: InventoryState, val remainingQuantity: Int) : RemoveItemResult
    data class Rejected(val reason: Reason) : RemoveItemResult

    enum class Reason {
        NON_POSITIVE_QUANTITY,
        INSUFFICIENT_QUANTITY,
        /** 不允许"悄悄删掉一个玩家从未拥有过的资源"以外的未知项；保留以便调用方校验。 */
        UNKNOWN_RESOURCE,
    }
}

/**
 * 加入物品。
 *
 * 库存本身不校验资源是否存在（定义来自 [ResourceCatalog]），
 * 因此这里接受任意 resourceId；调用方若需要白名单校验，先查 [ResourceCatalog]。
 */
class AddItemToInventoryUseCase(
    private val clock: WorldClock,
) {
    operator fun invoke(
        state: InventoryState,
        resourceId: String,
        quantity: Int,
    ): AddItemResult {
        if (quantity <= 0) return AddItemResult.Rejected(AddItemResult.Reason.NON_POSITIVE_QUANTITY)
        val now = clock.now().toEpochMilli()
        val existing = state.items[resourceId]
        val merged = if (existing == null) {
            InventoryItem(resourceId, quantity, now, now)
        } else {
            existing.copy(
                quantity = existing.quantity + quantity,
                lastAcquiredAtEpochMs = now,
            )
        }
        return AddItemResult.Success(state.copy(items = state.items + (resourceId to merged)), merged.quantity)
    }
}

/** 移除物品；数量不足时整笔拒绝，不做部分扣除。 */
class RemoveItemFromInventoryUseCase {
    operator fun invoke(
        state: InventoryState,
        resourceId: String,
        quantity: Int,
    ): RemoveItemResult {
        if (quantity <= 0) {
            return RemoveItemResult.Rejected(RemoveItemResult.Reason.NON_POSITIVE_QUANTITY)
        }
        val existing = state.items[resourceId]
            ?: return RemoveItemResult.Rejected(RemoveItemResult.Reason.UNKNOWN_RESOURCE)
        if (existing.quantity < quantity) {
            return RemoveItemResult.Rejected(RemoveItemResult.Reason.INSUFFICIENT_QUANTITY)
        }
        val remaining = existing.quantity - quantity
        val items = if (remaining == 0) {
            state.items - resourceId
        } else {
            state.items + (resourceId to existing.copy(quantity = remaining))
        }
        return RemoveItemResult.Success(state.copy(items = items), remaining)
    }
}

/** 资源定义目录：Fake 实现，P1 起由服务端内容下发。 */
interface ResourceCatalog {
    fun definition(resourceId: String): ResourceDefinition?
    fun all(): List<ResourceDefinition>
}

class InMemoryResourceCatalog(
    definitions: List<ResourceDefinition>,
) : ResourceCatalog {
    private val byId: Map<String, ResourceDefinition> = definitions.associateBy { it.id }

    override fun definition(resourceId: String): ResourceDefinition? = byId[resourceId]

    override fun all(): List<ResourceDefinition> = byId.values.sortedBy { it.id }

    companion object {
        val OBSERVATION_RECORD = ResourceDefinition(
            id = "res.observation_record",
            name = "观察记录",
            category = ResourceCategory.KNOWLEDGE,
            rarity = Rarity.COMMON,
            tags = setOf("observe", "lake"),
            description = "在某个地点认真看过之后留下的记录。",
        )

        val LAKE_MEMORY_FRAGMENT = ResourceDefinition(
            id = "res.lake_memory_fragment",
            name = "湖泊记忆碎片",
            category = ResourceCategory.MEMORY,
            rarity = Rarity.UNCOMMON,
            tags = setOf("lake", "memory", "rain"),
            description = "关于北湖的一段记忆凝结成的碎片。雨天在水边最容易捡到。",
        )

        // ---- 自然（地点采集） ----

        val RAIN_MOSS = ResourceDefinition(
            id = "res.rain_moss",
            name = "雨生苔痕",
            category = ResourceCategory.NATURE,
            rarity = Rarity.COMMON,
            tags = setOf("moss", "rain", "garden"),
            description = "雨后才舒展的苔藓，摸上去凉而软。",
        )

        val REED_LEAF = ResourceDefinition(
            id = "res.reed_leaf",
            name = "芦苇叶",
            category = ResourceCategory.NATURE,
            rarity = Rarity.COMMON,
            tags = setOf("reed", "lake"),
            description = "湖岸的芦苇叶，晒干可以编点什么。",
        )

        val PINE_CONE = ResourceDefinition(
            id = "res.pine_cone",
            name = "松果",
            category = ResourceCategory.NATURE,
            rarity = Rarity.COMMON,
            tags = setOf("pine", "garden", "autumn"),
            description = "落在小径上的松果，秋天尤其多。",
        )

        val PETAL = ResourceDefinition(
            id = "res.petal",
            name = "花瓣",
            category = ResourceCategory.NATURE,
            rarity = Rarity.COMMON,
            tags = setOf("flower", "garden", "spring"),
            description = "刚落下不久的花瓣，颜色还很新。",
        )

        val LAWN_DEW_GRASS = ResourceDefinition(
            id = "res.dew_grass",
            name = "带露的草叶",
            category = ResourceCategory.NATURE,
            rarity = Rarity.COMMON,
            tags = setOf("grass", "dawn"),
            description = "天刚亮时草叶上挂着的露水，一碰就碎。",
        )

        val NIGHT_WATER_SOUND = ResourceDefinition(
            id = "res.night_water_sound",
            name = "夜水声",
            category = ResourceCategory.CULTURE,
            rarity = Rarity.UNCOMMON,
            tags = setOf("lake", "night", "sound"),
            description = "入夜后湖面才有的那种细碎水声，听过就记住了。",
        )

        // ---- 知识 / 文化（地点采集） ----

        val LIBRARY_SHELF_CARD = ResourceDefinition(
            id = "res.shelf_card",
            name = "书目卡",
            category = ResourceCategory.KNOWLEDGE,
            rarity = Rarity.COMMON,
            tags = setOf("library", "book"),
            description = "从书脊间抽出的一张旧书目卡，字迹已经发淡。",
        )

        val READING_NOTE = ResourceDefinition(
            id = "res.reading_note",
            name = "阅读随记",
            category = ResourceCategory.KNOWLEDGE,
            rarity = Rarity.COMMON,
            tags = setOf("library", "note"),
            description = "在图书馆坐下来读完一段之后写下的几行字。",
        )

        val CANTEEN_MENU_TICKET = ResourceDefinition(
            id = "res.menu_ticket",
            name = "今日菜签",
            category = ResourceCategory.CULTURE,
            rarity = Rarity.COMMON,
            tags = setOf("canteen", "food"),
            description = "食堂窗口挂着的小菜签，每天都不一样。",
        )

        val PLAZA_FLYER = ResourceDefinition(
            id = "res.plaza_flyer",
            name = "活动传单",
            category = ResourceCategory.CULTURE,
            rarity = Rarity.COMMON,
            tags = setOf("plaza", "event"),
            description = "广场角落落下的传单，写着某场社团活动。",
        )

        val DORM_NOTE_SCRAP = ResourceDefinition(
            id = "res.dorm_scrap",
            name = "宿舍便签",
            category = ResourceCategory.CULTURE,
            rarity = Rarity.COMMON,
            tags = setOf("dorm", "note"),
            description = "贴在桌边的一张便签，提醒自己别忘了带什么。",
        )

        // ---- 异常（条件罕见） ----

        val MIRROR_MOON_FISH_SHADOW = ResourceDefinition(
            id = "res.mirror_moon_fish_shadow",
            name = "镜月鱼影",
            category = ResourceCategory.ANOMALY,
            rarity = Rarity.ANOMALY,
            tags = setOf("lake", "night", "rain", "anomaly"),
            description = "雨夜里湖面反光里多出来的那条鱼影。说不清是什么。",
        )

        val FROST_PATTERN = ResourceDefinition(
            id = "res.frost_pattern",
            name = "霜纹",
            category = ResourceCategory.ANOMALY,
            rarity = Rarity.RARE,
            tags = setOf("winter", "snow", "garden", "anomaly"),
            description = "雪天窗面上结出的花纹，形状像是被人画上去的。",
        )

        val DEFAULT = listOf(
            OBSERVATION_RECORD,
            LAKE_MEMORY_FRAGMENT,
            RAIN_MOSS,
            REED_LEAF,
            PINE_CONE,
            PETAL,
            LAWN_DEW_GRASS,
            NIGHT_WATER_SOUND,
            LIBRARY_SHELF_CARD,
            READING_NOTE,
            CANTEEN_MENU_TICKET,
            PLAZA_FLYER,
            DORM_NOTE_SCRAP,
            MIRROR_MOON_FISH_SHADOW,
            FROST_PATTERN,
        )
    }
}
