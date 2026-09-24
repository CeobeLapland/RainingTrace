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

/** 资源定义目录：运行时由 `ContentResourceCatalog` 读内容索引；这个实现留给测试。 */
interface ResourceCatalog {
    fun definition(resourceId: String): ResourceDefinition?
    fun all(): List<ResourceDefinition>
}

/**
 * 内存实现：资源定义全部来自构造参数。
 *
 * 内容本体住在 `assets/content/resources.json`（+ 私有目录覆盖层）。
 */
class InMemoryResourceCatalog(
    definitions: List<ResourceDefinition>,
) : ResourceCatalog {
    private val byId: Map<String, ResourceDefinition> = definitions.associateBy { it.id }

    override fun definition(resourceId: String): ResourceDefinition? = byId[resourceId]

    override fun all(): List<ResourceDefinition> = byId.values.sortedBy { it.id }
}
