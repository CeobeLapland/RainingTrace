package com.rainingtrace.domain.inventory

import com.rainingtrace.core.time.FakeWorldClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class InventoryUseCaseTest {

    private val clock = FakeWorldClock(Instant.parse("2026-09-13T08:00:00Z"))
    private val add = AddItemToInventoryUseCase(clock)
    private val remove = RemoveItemFromInventoryUseCase()

    @Test
    fun `add new item creates entry`() {
        val result = add(InventoryState(), "res.observation_record", 1)
        assertTrue(result is AddItemResult.Success)
        result as AddItemResult.Success
        assertEquals(1, result.newQuantity)
        assertEquals(1, result.state.quantityOf("res.observation_record"))
    }

    @Test
    fun `add stacks quantity`() {
        var state = InventoryState()
        state = (add(state, "res.a", 2) as AddItemResult.Success).state
        val result = add(state, "res.a", 3) as AddItemResult.Success
        assertEquals(5, result.newQuantity)
        assertEquals(1, result.state.totalKinds())
    }

    @Test
    fun `add non-positive quantity rejected`() {
        val result = add(InventoryState(), "res.a", 0)
        assertEquals(AddItemResult.Reason.NON_POSITIVE_QUANTITY, (result as AddItemResult.Rejected).reason)
    }

    @Test
    fun `remove partial leaves remainder`() {
        var state = (add(InventoryState(), "res.a", 5) as AddItemResult.Success).state
        val result = remove(state, "res.a", 2) as RemoveItemResult.Success
        assertEquals(3, result.remainingQuantity)
        assertEquals(3, result.state.quantityOf("res.a"))
    }

    @Test
    fun `remove all deletes entry`() {
        val state = add(InventoryState(), "res.a", 2) as AddItemResult.Success
        val result = remove(state.state, "res.a", 2) as RemoveItemResult.Success
        assertEquals(0, result.remainingQuantity)
        assertEquals(0, result.state.totalKinds())
    }

    @Test
    fun `remove insufficient rejected without partial deduction`() {
        val state = (add(InventoryState(), "res.a", 2) as AddItemResult.Success).state
        val result = remove(state, "res.a", 3)
        assertEquals(RemoveItemResult.Reason.INSUFFICIENT_QUANTITY, (result as RemoveItemResult.Rejected).reason)
        assertEquals(2, state.quantityOf("res.a"))
    }

    @Test
    fun `remove from never-owned resource rejected`() {
        val result = remove(InventoryState(), "res.unknown", 1)
        assertEquals(RemoveItemResult.Reason.UNKNOWN_RESOURCE, (result as RemoveItemResult.Rejected).reason)
    }

    @Test
    fun `first acquired timestamp preserved across stacks`() {
        val first = (add(InventoryState(), "res.a", 1) as AddItemResult.Success).state
        clock.advanceSeconds(3600)
        val second = (add(first, "res.a", 1) as AddItemResult.Success).state
        val item = second.items.getValue("res.a")
        assertEquals(Instant.parse("2026-09-13T08:00:00Z").toEpochMilli(), item.firstAcquiredAtEpochMs)
        assertEquals(Instant.parse("2026-09-13T09:00:00Z").toEpochMilli(), item.lastAcquiredAtEpochMs)
    }
}
