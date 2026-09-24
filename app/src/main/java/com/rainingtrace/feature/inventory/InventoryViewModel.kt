package com.rainingtrace.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.craft.RecipeCatalog
import com.rainingtrace.domain.inventory.InventoryFill
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.LoadResourceDiscoveriesUseCase
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.inventory.ResourceSource
import com.rainingtrace.domain.inventory.TransferItemUseCase
import com.rainingtrace.domain.inventory.inventoryFill
import com.rainingtrace.domain.inventory.resourceSourcesFor
import com.rainingtrace.domain.world.ResourceYieldRuleCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 背包条目：一种**当前持有**的资源与数量（会变、会被消耗）。 */
data class BagEntry(
    val definition: ResourceDefinition,
    val quantity: Int,
)

data class BagUiState(
    val entries: List<BagEntry> = emptyList(),
    /** 软容量：只提示不阻止。 */
    val fill: InventoryFill = inventoryFill(0),
)

/**
 * 图鉴条目：一种资源的**永久**档案（只增不减），与持有量无关。
 *
 * [discovered] 来自足迹事件（见 `LoadResourceDiscoveriesUseCase`），
 * 所以把材料用掉、搬进仓库都不会让条目变灰。
 */
data class CodexEntry(
    val definition: ResourceDefinition,
    val discovered: Boolean,
    val firstAtEpochMs: Long? = null,
    val timesAcquired: Int = 0,
    /** 能在地点动作里采到的来源。 */
    val sources: List<ResourceSource> = emptyList(),
    /** 是不是某条配方的产出（只能做出来，采不到）。 */
    val crafted: Boolean = false,
)

data class CodexUiState(
    val entries: List<CodexEntry> = emptyList(),
    val discoveredKinds: Int = 0,
    val totalKinds: Int = 0,
)

/**
 * 收藏页的两个页签各有各的真相，所以拆成两个 state：
 *
 * - **背包**跟着库存实时变（采集/制作/存取都会改它）；
 * - **图鉴**按页签刷新一次即可——它的真相是足迹事件，实时订阅等于每次采集全量重算。
 */
class InventoryViewModel(
    inventoryRepository: InventoryRepository,
    private val catalog: ResourceCatalog,
    private val yieldRules: ResourceYieldRuleCatalog,
    private val recipes: RecipeCatalog,
    private val loadDiscoveries: LoadResourceDiscoveriesUseCase,
    private val transferItem: TransferItemUseCase,
) : ViewModel() {

    val bagUiState: StateFlow<BagUiState> = inventoryRepository.observeState()
        .map { state ->
            val entries = state.items.values
                .mapNotNull { item ->
                    catalog.definition(item.resourceId)?.let { BagEntry(it, item.quantity) }
                }
                .sortedWith(compareBy({ it.definition.category.ordinal }, { it.definition.name }))
            BagUiState(entries = entries, fill = inventoryFill(entries.size))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BagUiState(),
        )

    private val _codexUiState = MutableStateFlow(CodexUiState())
    val codexUiState: StateFlow<CodexUiState> = _codexUiState.asStateFlow()

    private var codexLoading = false

    /** 把整叠存进仓库：背包列表自己会变，不需要额外提示。 */
    fun storeToWarehouse(resourceId: String, amount: Int) {
        if (amount <= 0) return
        viewModelScope.launch { transferItem.store(resourceId, amount) }
    }

    /** 切到图鉴页签时调一次（也用于制作完之后手动刷新）。 */
    fun refreshCodex() {
        if (codexLoading) return
        codexLoading = true
        viewModelScope.launch {
            try {
                val discoveries = loadDiscoveries()
                val rules = yieldRules.all()
                val craftedIds = recipes.all().map { it.output.resourceId }.toSet()
                val entries = catalog.all()
                    .map { definition ->
                        val discovery = discoveries[definition.id]
                        CodexEntry(
                            definition = definition,
                            discovered = discovery != null,
                            firstAtEpochMs = discovery?.firstAtEpochMs,
                            timesAcquired = discovery?.timesAcquired ?: 0,
                            sources = resourceSourcesFor(definition.id, rules),
                            crafted = definition.id in craftedIds,
                        )
                    }
                    // 已发现的排前面：图鉴的第一眼应该是"我有什么"，不是"我还缺什么"。
                    .sortedWith(
                        compareBy(
                            { !it.discovered },
                            { it.definition.category.ordinal },
                            { it.definition.name },
                        ),
                    )
                _codexUiState.value = CodexUiState(
                    entries = entries,
                    discoveredKinds = entries.count { it.discovered },
                    totalKinds = entries.size,
                )
            } finally {
                codexLoading = false
            }
        }
    }
}