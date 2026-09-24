package com.rainingtrace.domain.craft

import com.rainingtrace.domain.inventory.AddItemResult
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.RemoveItemFromInventoryUseCase
import com.rainingtrace.domain.inventory.RemoveItemResult

/**
 * 制作（GDD §11 加工）：按配方把背包里的输入换成输出。
 *
 * **只从背包取材**——材料在仓库里就是"看着有、做不了"，所以那种情况会
 * 单独报 [CraftRejectReason.MATERIALS_IN_WAREHOUSE]，让提示可行动。
 *
 * 不写足迹事件：`FootprintEvent` 强制带坐标，为一次制作去接 `LocationProvider`
 * 不划算（产出仍会被 [com.rainingtrace.domain.inventory.resourceDiscoveries]
 * 通过"当前持有"补进图鉴）。
 */
class CraftUseCase(
    private val inventoryRepository: InventoryRepository,
    private val recipeCatalog: RecipeCatalog,
    private val addItem: AddItemToInventoryUseCase,
    private val removeItem: RemoveItemFromInventoryUseCase,
    /** 仓库（可选）：只用来把"真没有"和"放在仓库"说准，不参与取材。未接线时为 null。 */
    private val warehouseRepository: InventoryRepository? = null,
) {

    suspend operator fun invoke(recipeId: String): CraftResult {
        val recipe = recipeCatalog.byId(recipeId)
            ?: return CraftResult.Rejected(CraftRejectReason.UNKNOWN_RECIPE)

        val inventory = inventoryRepository.loadState()
        val missing = recipe.inputs.filter { inventory.quantityOf(it.resourceId) < it.amount }
        if (missing.isNotEmpty()) {
            return CraftResult.Rejected(missingReason(missing.map { it.resourceId }))
        }

        // 先扣输入，再加输出：任何一步失败都不落库，背包保持原样。
        var working = inventory
        recipe.inputs.forEach { input ->
            when (val removed = removeItem(working, input.resourceId, input.amount)) {
                is RemoveItemResult.Success -> working = removed.state
                is RemoveItemResult.Rejected ->
                    return CraftResult.Rejected(missingReason(listOf(input.resourceId)))
            }
        }
        val added = addItem(working, recipe.output.resourceId, recipe.output.amount)
        if (added !is AddItemResult.Success) {
            return CraftResult.Rejected(CraftRejectReason.CRAFT_FAILED)
        }
        inventoryRepository.saveState(added.state)

        return CraftResult.Success(
            recipeId = recipe.id,
            outputResourceId = recipe.output.resourceId,
            amount = recipe.output.amount,
            newQuantity = added.newQuantity,
        )
    }

    /** 缺料时细分：材料只是在仓库里 → 让提示变成"去仓库拿"，而不是"再去采"。 */
    private suspend fun missingReason(resourceIds: List<String>): CraftRejectReason {
        val warehouse = warehouseRepository?.loadState() ?: return CraftRejectReason.MISSING_MATERIALS(resourceIds)
        val stored = resourceIds.filter { warehouse.quantityOf(it) > 0 }
        return if (stored.isEmpty()) {
            CraftRejectReason.MISSING_MATERIALS(resourceIds)
        } else {
            CraftRejectReason.MATERIALS_IN_WAREHOUSE(stored)
        }
    }
}

sealed interface CraftResult {
    data class Success(
        val recipeId: String,
        val outputResourceId: String,
        /** 本次产出的数量（配方可以给多份）。 */
        val amount: Int,
        /** 产出之后该资源在背包里的数量。 */
        val newQuantity: Int,
    ) : CraftResult

    data class Rejected(val reason: CraftRejectReason) : CraftResult
}

sealed interface CraftRejectReason {
    /** 配方 id 不存在（内容被删/写错）。 */
    data object UNKNOWN_RECIPE : CraftRejectReason

    /** 背包里缺这些材料。 */
    data class MISSING_MATERIALS(val resourceIds: List<String>) : CraftRejectReason

    /** 材料其实有，但在仓库里——提示"去仓库取"。 */
    data class MATERIALS_IN_WAREHOUSE(val resourceIds: List<String>) : CraftRejectReason

    /** 结算失败（与 `PerformPlaceActionUseCase.REWARD_FAILED` 同口径的兜底）。 */
    data object CRAFT_FAILED : CraftRejectReason
}