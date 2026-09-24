package com.rainingtrace.feature.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.inventory.TransferItemUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WarehouseEntry(
    val definition: ResourceDefinition,
    val quantity: Int,
)

data class WarehouseUiState(
    val entries: List<WarehouseEntry> = emptyList(),
)

/**
 * 仓库页：只做一件事——把东西搬回背包。
 *
 * 不做软容量：软上限管的是**随身**背包（见 `InventoryCapacity`），
 * 仓库本来就是"放得下的地方"，给它也设上限只会让两套规则打架。
 */
class WarehouseViewModel(
    private val transferItem: TransferItemUseCase,
    warehouseRepository: InventoryRepository,
    private val resourceCatalog: ResourceCatalog,
) : ViewModel() {

    val uiState: StateFlow<WarehouseUiState> = warehouseRepository.observeState()
        .map { state ->
            WarehouseUiState(
                entries = state.items.values
                    .mapNotNull { item ->
                        resourceCatalog.definition(item.resourceId)
                            ?.let { WarehouseEntry(it, item.quantity) }
                    }
                    .sortedWith(compareBy({ it.definition.category.ordinal }, { it.definition.name })),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WarehouseUiState(),
        )

    /** 取出整叠：搬完列表自己就会变，所以不需要额外的成功提示。 */
    fun takeAllToBackpack(resourceId: String, quantity: Int) {
        if (quantity <= 0) return
        viewModelScope.launch { transferItem.take(resourceId, quantity) }
    }
}