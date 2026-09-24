package com.rainingtrace.domain.inventory

import com.rainingtrace.core.time.FakeWorldClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TransferItemUseCaseTest {

    private val clock = FakeWorldClock(Instant.parse("2026-09-24T08:00:00Z"))
    private val add = AddItemToInventoryUseCase(clock)
    private val remove = RemoveItemFromInventoryUseCase()

    private class FakeContainer(
        var state: InventoryState = InventoryState(),
    ) : InventoryRepository {
        override fun observeState(): Flow<InventoryState> = flowOf(state)
        override suspend fun loadState(): InventoryState = state
        override suspend fun saveState(state: InventoryState) {
            this.state = state
        }
    }

    /**
     * 仓库假实现：记下 [replaceBoth] 收到的那份背包状态与调用次数，
     * 这样测试能验证"两边一次事务写完"，而不只是各自的最终值。
     */
    private class FakeWarehouse(
        var state: InventoryState = InventoryState(),
    ) : WarehouseRepository {
        var replaceBothCalls = 0
        var lastInventory: InventoryState? = null

        override fun observeState(): Flow<InventoryState> = flowOf(state)
        override suspend fun loadState(): InventoryState = state
        override suspend fun saveState(state: InventoryState) {
            this.state = state
        }

        override suspend fun replaceBoth(inventory: InventoryState, warehouse: InventoryState) {
            replaceBothCalls++
            lastInventory = inventory
            this.state = warehouse
        }
    }

    private fun stateWith(vararg items: Pair<String, Int>) = InventoryState(
        items = items.associate { (id, quantity) -> id to InventoryItem(id, quantity, 0L, 0L) },
    )

    private fun transfer(backpack: FakeContainer, warehouse: FakeWarehouse) =
        TransferItemUseCase(backpack, warehouse, add, remove)

    @Test
    fun `存入仓库时两边一次事务写完`() = runTest {
        val backpack = FakeContainer(stateWith("res.moss" to 5))
        val warehouse = FakeWarehouse(stateWith("res.rope" to 4))

        val result = transfer(backpack, warehouse).store("res.moss", 3)

        result as TransferResult.Moved
        assertEquals(2, result.remainingInSource)
        assertEquals("背包的新状态要一起写进事务", 2, warehouse.lastInventory?.quantityOf("res.moss"))
        assertEquals(3, warehouse.state.quantityOf("res.moss"))
        assertEquals("仓库原有的东西不能被抹掉或串味", 4, warehouse.state.quantityOf("res.rope"))
        assertEquals("只允许一次事务写入，不能连调两次 saveState", 1, warehouse.replaceBothCalls)
    }

    @Test
    fun `从仓库取出时只加取回的 不把仓库其余的东西带过来`() = runTest {
        val backpack = FakeContainer(stateWith("res.moss" to 2))
        val warehouse = FakeWarehouse(stateWith("res.rope" to 4, "res.drift_wood" to 1))

        val result = transfer(backpack, warehouse).take("res.rope", 4)

        result as TransferResult.Moved
        assertEquals(0, result.remainingInSource)
        assertEquals(2, warehouse.lastInventory?.quantityOf("res.moss"))
        assertEquals(4, warehouse.lastInventory?.quantityOf("res.rope"))
        assertEquals("仓库里没被取走的还要在", 1, warehouse.state.quantityOf("res.drift_wood"))
        assertEquals(0, warehouse.state.quantityOf("res.rope"))
        assertEquals(1, warehouse.replaceBothCalls)
    }

    @Test
    fun `数量不够时整笔拒绝且不写库`() = runTest {
        val backpack = FakeContainer(stateWith("res.moss" to 2))
        val warehouse = FakeWarehouse()

        val result = transfer(backpack, warehouse).store("res.moss", 3)

        assertEquals(
            TransferRejectReason.INSUFFICIENT_QUANTITY,
            (result as TransferResult.Rejected).reason,
        )
        assertEquals(0, warehouse.replaceBothCalls)
        assertEquals(2, backpack.state.quantityOf("res.moss"))
    }

    @Test
    fun `来源里根本没有这种资源时拒绝`() = runTest {
        val result = transfer(FakeContainer(), FakeWarehouse()).take("res.rope", 1)

        assertEquals(TransferRejectReason.NOT_HELD, (result as TransferResult.Rejected).reason)
    }

    @Test
    fun `非正数量被拒绝`() = runTest {
        val backpack = FakeContainer(stateWith("res.moss" to 2))
        val warehouse = FakeWarehouse()

        val result = transfer(backpack, warehouse).store("res.moss", 0)

        assertEquals(
            TransferRejectReason.NON_POSITIVE_AMOUNT,
            (result as TransferResult.Rejected).reason,
        )
        assertTrue(warehouse.replaceBothCalls == 0)
    }
}