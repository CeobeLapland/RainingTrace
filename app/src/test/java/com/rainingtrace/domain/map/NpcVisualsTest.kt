package com.rainingtrace.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcVisualsTest {

    @Test
    fun `walking and staying use different styles`() {
        val staying = npcStyle(walking = false)
        val walking = npcStyle(walking = true)

        assertNotEquals(staying.argbColor, walking.argbColor)
        assertNotEquals(staying.glyph, walking.glyph)
        assertTrue(staying.glyph.isNotBlank())
        assertTrue(walking.glyph.isNotBlank())
    }

    @Test
    fun `visual carries the name and coordinate`() {
        val coordinate = WorldCoordinate(39.0, 116.0)

        val visual = NpcVisual(
            npcId = "npc.bit.lin",
            npcName = "林",
            coordinate = coordinate,
            walking = true,
        )

        assertEquals("npc.bit.lin", visual.npcId)
        assertEquals("林", visual.npcName)
        assertEquals(coordinate, visual.coordinate)
        assertTrue(visual.walking)
    }
}
