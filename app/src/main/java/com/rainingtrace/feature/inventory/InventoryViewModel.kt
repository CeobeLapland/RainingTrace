package com.rainingtrace.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.inventory.ResourceDefinition
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 图鉴条目：一条资源定义 + 当前拥有数量。quantity == 0 表示"未收集"。
 */
data class CollectionEntry(
    val definition: ResourceDefinition,
    val quantity: Int,
)

data class InventoryUiState(
    val entries: List<CollectionEntry> = emptyList(),
    val ownedKinds: Int = 0,
    val totalKinds: Int = 0,
)

/**
 * 背包/图鉴：把库存（拥有量）与资源目录（定义）合并成可见收藏。
 * 目录是静态的；拥有量来自 Room 实时流。
 */
class InventoryViewModel(
    inventoryRepository: InventoryRepository,
    catalog: ResourceCatalog,
) : ViewModel() {

    val uiState: StateFlow<InventoryUiState> = inventoryRepository.observeState()
        .map { state ->
            val entries = catalog.all().map { def ->
                CollectionEntry(def, state.quantityOf(def.id))
            }
            InventoryUiState(
                entries = entries,
                ownedKinds = entries.count { it.quantity > 0 },
                totalKinds = entries.size,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InventoryUiState(),
        )
}