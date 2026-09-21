package com.rainingtrace.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcClockOffsetTest {

    @Test
    fun `unknown key falls back to real time`() {
        assertEquals(NpcClockOffset.NONE, NpcClockOffset.fromKey("nonsense"))
        assertEquals(NpcClockOffset.NONE, NpcClockOffset.fromKey(""))
    }

    @Test
    fun `every option round trips through its key`() {
        NpcClockOffset.entries.forEach { offset ->
            assertEquals(offset, NpcClockOffset.fromKey(offset.key))
        }
    }

    @Test
    fun `only real time leaves the clock alone`() {
        assertEquals(0, NpcClockOffset.NONE.minutes)
        NpcClockOffset.entries
            .filterNot { it == NpcClockOffset.NONE }
            .forEach { assertTrue("${it.key} 应该真的挪动时间", it.minutes != 0) }
    }
}
