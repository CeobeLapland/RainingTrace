package com.rainingtrace.domain.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryCapacityTest {

    @Test
    fun `未到软上限就不算满`() {
        val fill = inventoryFill(ownedKinds = 5, softLimit = 24)
        assertEquals(5, fill.usedKinds)
        assertEquals(24, fill.softLimit)
        assertFalse(fill.isOver)
    }

    @Test
    fun `达到软上限即提示满了 但仍不阻止`() {
        assertTrue(inventoryFill(ownedKinds = 24, softLimit = 24).isOver)
        assertTrue(inventoryFill(ownedKinds = 30, softLimit = 24).isOver)
    }

    @Test
    fun `负数种类被夹到零 不会让状态不合法`() {
        assertEquals(0, inventoryFill(ownedKinds = -3, softLimit = 24).usedKinds)
    }

    @Test
    fun `默认软上限是宽松的`() {
        assertEquals(BACKPACK_SOFT_KIND_LIMIT, inventoryFill(0).softLimit)
    }
}