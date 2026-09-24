package com.rainingtrace.domain.inventory

/**
 * 背包 ↔ 仓库 的搬运。
 *
 * 先把两边的新状态**算成纯数据**（[AddItemToInventoryUseCase] /
 * [RemoveItemFromInventoryUseCase] 都是状态到状态的纯函数，可单测），
 * 再交给 [WarehouseRepository.replaceBoth] 一次性落库——不连调两次 `saveState`。
 */
class TransferItemUseCase(
    private val inventoryRepository: InventoryRepository,
    private val warehouseRepository: WarehouseRepository,
    private val addItem: AddItemToInventoryUseCase,
    private val removeItem: RemoveItemFromInventoryUseCase,
) {

    /** 背包 → 仓库。 */
    suspend fun store(resourceId: String, amount: Int): TransferResult =
        move(resourceId, amount, fromBackpack = true)

    /** 仓库 → 背包。 */
    suspend fun take(resourceId: String, amount: Int): TransferResult =
        move(resourceId, amount, fromBackpack = false)

    private suspend fun move(
        resourceId: String,
        amount: Int,
        fromBackpack: Boolean,
    ): TransferResult {
        if (amount <= 0) return TransferResult.Rejected(TransferRejectReason.NON_POSITIVE_AMOUNT)

        val inventory = inventoryRepository.loadState()
        val warehouse = warehouseRepository.loadState()
        // 来源与目标各自算各自的**新状态**：不能拿来源的状态去 addItem，
        // 那会把来源余下的东西一起带进目标容器（两边独立，不是一个大状态）。
        val source = if (fromBackpack) inventory else warehouse
        val target = if (fromBackpack) warehouse else inventory

        val removed = removeItem(source, resourceId, amount)
        if (removed !is RemoveItemResult.Success) {
            val reason = (removed as RemoveItemResult.Rejected).reason
            return TransferResult.Rejected(
                when (reason) {
                    RemoveItemResult.Reason.UNKNOWN_RESOURCE -> TransferRejectReason.NOT_HELD
                    else -> TransferRejectReason.INSUFFICIENT_QUANTITY
                },
            )
        }

        val added = addItem(target, resourceId, amount)
        if (added !is AddItemResult.Success) {
            return TransferResult.Rejected(TransferRejectReason.TRANSFER_FAILED)
        }

        warehouseRepository.replaceBoth(
            inventory = if (fromBackpack) removed.state else added.state,
            warehouse = if (fromBackpack) added.state else removed.state,
        )
        return TransferResult.Moved(
            resourceId = resourceId,
            amount = amount,
            remainingInSource = removed.remainingQuantity,
        )
    }
}

sealed interface TransferResult {
    data class Moved(
        val resourceId: String,
        val amount: Int,
        /** 搬完之后来源容器里还剩几个。 */
        val remainingInSource: Int,
    ) : TransferResult

    data class Rejected(val reason: TransferRejectReason) : TransferResult
}

enum class TransferRejectReason {
    NON_POSITIVE_AMOUNT,
    /** 来源容器里根本没有这种资源。 */
    NOT_HELD,
    INSUFFICIENT_QUANTITY,
    /** 结算失败的兜底（与 `CraftRejectReason.CRAFT_FAILED` 同口径）。 */
    TRANSFER_FAILED,
}