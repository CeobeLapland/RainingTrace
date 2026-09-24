package com.rainingtrace.feature.crafting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.craft.CraftResult
import com.rainingtrace.domain.craft.CraftRejectReason
import com.rainingtrace.domain.craft.CraftUseCase
import com.rainingtrace.domain.craft.Recipe
import com.rainingtrace.domain.craft.RecipeCatalog
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.InventoryState
import com.rainingtrace.domain.inventory.ResourceCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 配方的一行材料：需要多少、背包里有几个。 */
data class RecipeInputLine(
    val resourceId: String,
    val name: String,
    val required: Int,
    val owned: Int,
) {
    val enough: Boolean get() = owned >= required
}

/** 一条配方此刻能不能做。 */
data class RecipeLine(
    val recipe: Recipe,
    val outputName: String,
    val inputs: List<RecipeInputLine>,
) {
    val craftable: Boolean get() = inputs.all { it.enough }
}

data class CraftUiState(
    val lines: List<RecipeLine> = emptyList(),
    /** 上一次制作的结果（成功/失败都说清楚）；下一次制作时清掉。 */
    val message: String? = null,
)

/**
 * 制作页：配方列表 + 材料够不够 + 一键制作。
 *
 * 材料只从**背包**取（仓库里的材料会由用例单独报出来），所以这里只订阅背包。
 */
class CraftViewModel(
    private val craftItem: CraftUseCase,
    private val recipeCatalog: RecipeCatalog,
    private val inventoryRepository: InventoryRepository,
    private val resourceCatalog: ResourceCatalog,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CraftUiState> = combine(
        inventoryRepository.observeState(),
        message,
    ) { inventory, currentMessage ->
        CraftUiState(
            lines = recipeCatalog.all().map { it.toLine(inventory) },
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CraftUiState(),
    )

    fun craft(recipeId: String) {
        viewModelScope.launch {
            message.value = null
            message.value = when (val result = craftItem(recipeId)) {
                is CraftResult.Success ->
                    "做好了：${nameOf(result.outputResourceId)} × ${result.amount}"

                is CraftResult.Rejected -> rejectText(result.reason)
            }
        }
    }

    private fun rejectText(reason: CraftRejectReason): String = when (reason) {
        CraftRejectReason.UNKNOWN_RECIPE -> "这条配方已经不存在了。"
        is CraftRejectReason.MISSING_MATERIALS ->
            "还缺：" + reason.resourceIds.joinToString("、") { nameOf(it) }

        is CraftRejectReason.MATERIALS_IN_WAREHOUSE ->
            reason.resourceIds.joinToString("、") { nameOf(it) } + " 在仓库里，先去取出来。"

        CraftRejectReason.CRAFT_FAILED -> "制作失败，再试一次。"
    }

    private fun Recipe.toLine(inventory: InventoryState) = RecipeLine(
        recipe = this,
        outputName = nameOf(output.resourceId),
        inputs = inputs.map { input ->
            RecipeInputLine(
                resourceId = input.resourceId,
                name = nameOf(input.resourceId),
                required = input.amount,
                owned = inventory.quantityOf(input.resourceId),
            )
        },
    )

    private fun nameOf(resourceId: String): String =
        resourceCatalog.definition(resourceId)?.name ?: resourceId
}