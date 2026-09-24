package com.rainingtrace.domain.craft

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InventoryItem
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.InventoryState
import com.rainingtrace.domain.inventory.RemoveItemFromInventoryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CraftUseCaseTest {

    private val clock = FakeWorldClock(Instant.parse("2026-09-24T08:00:00Z"))
    private val add = AddItemToInventoryUseCase(clock)
    private val remove = RemoveItemFromInventoryUseCase()

    private class FakeInventoryRepository(
        initial: InventoryState = InventoryState(),
    ) : InventoryRepository {
        var state = initial
        override fun observeState(): Flow<InventoryState> = flowOf(state)
        override suspend fun loadState(): InventoryState = state
        override suspend fun saveState(state: InventoryState) {
            this.state = state
        }
    }

    /** 草绳 = 干草 ×2 + 芦苇叶 ×1。 */
    private val ropeRecipe = Recipe(
        id = "recipe.rope",
        inputs = listOf(RecipeInput("res.dry_grass", 2), RecipeInput("res.reed_leaf", 1)),
        output = RecipeOutput("res.rope", 1),
    )
    private val catalog = InMemoryRecipeCatalog(listOf(ropeRecipe))

    private fun stateWith(vararg items: Pair<String, Int>) = InventoryState(
        items = items.associate { (id, quantity) -> id to InventoryItem(id, quantity, 0L, 0L) },
    )

    private fun useCase(
        inventory: FakeInventoryRepository,
        warehouse: FakeInventoryRepository? = null,
    ) = CraftUseCase(inventory, catalog, add, remove, warehouse)

    @Test
    fun `材料齐全时扣输入并发放输出`() = runTest {
        val inventory = FakeInventoryRepository(stateWith("res.dry_grass" to 3, "res.reed_leaf" to 2))

        val result = useCase(inventory)("recipe.rope")

        assertTrue(result is CraftResult.Success)
        result as CraftResult.Success
        assertEquals("res.rope", result.outputResourceId)
        assertEquals(1, result.newQuantity)
        assertEquals(1, inventory.state.quantityOf("res.dry_grass"))
        assertEquals(1, inventory.state.quantityOf("res.reed_leaf"))
        assertEquals(1, inventory.state.quantityOf("res.rope"))
    }

    @Test
    fun `缺料时整笔拒绝且背包不变`() = runTest {
        val inventory = FakeInventoryRepository(stateWith("res.dry_grass" to 1, "res.reed_leaf" to 1))

        val result = useCase(inventory)("recipe.rope")

        assertEquals(
            CraftRejectReason.MISSING_MATERIALS(listOf("res.dry_grass")),
            (result as CraftResult.Rejected).reason,
        )
        assertEquals(1, inventory.state.quantityOf("res.dry_grass"))
        assertEquals(0, inventory.state.quantityOf("res.rope"))
    }

    @Test
    fun `材料在仓库里时给出可行动的提示`() = runTest {
        val inventory = FakeInventoryRepository(stateWith("res.reed_leaf" to 1))
        val warehouse = FakeInventoryRepository(stateWith("res.dry_grass" to 9))

        val result = useCase(inventory, warehouse)("recipe.rope")

        assertEquals(
            CraftRejectReason.MATERIALS_IN_WAREHOUSE(listOf("res.dry_grass")),
            (result as CraftResult.Rejected).reason,
        )
    }

    @Test
    fun `仓库未接线时缺料只报缺料`() = runTest {
        val inventory = FakeInventoryRepository(stateWith("res.reed_leaf" to 1))

        val result = useCase(inventory)("recipe.rope")

        assertEquals(
            CraftRejectReason.MISSING_MATERIALS(listOf("res.dry_grass")),
            (result as CraftResult.Rejected).reason,
        )
    }

    @Test
    fun `未知配方被拒绝`() = runTest {
        val inventory = FakeInventoryRepository()

        val result = useCase(inventory)("recipe.nope")

        assertEquals(CraftRejectReason.UNKNOWN_RECIPE, (result as CraftResult.Rejected).reason)
    }
}