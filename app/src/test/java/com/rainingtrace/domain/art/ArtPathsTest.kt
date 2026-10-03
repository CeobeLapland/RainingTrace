package com.rainingtrace.domain.art

import com.rainingtrace.domain.map.PlaceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 命名约定是美术与代码之间的**唯一接口**，所以它自己也该被钉住：
 * 一旦这里改了拼法，已有的图会全部变成"读不到"，静默回退成占位。
 */
class ArtPathsTest {

    @Test
    fun `地点针路径是类型名的小写`() {
        assertEquals("art/pin/lake.png", ArtPaths.pin(PlaceType.LAKE))
        assertEquals("art/pin/mushroom_patch.png", ArtPaths.pin(PlaceType.MUSHROOM_PATCH))
    }

    @Test
    fun `方形图各自在自己的目录下`() {
        assertEquals("art/place/library.png", ArtPaths.place(PlaceType.LIBRARY))
        assertEquals("art/npc/npc.lin.png", ArtPaths.npcAvatar("npc.lin"))
        assertEquals("art/item/res.rope.png", ArtPaths.item("res.rope"))
    }

    @Test
    fun `固定名字与NPC两种姿态`() {
        assertEquals("art/pin/_unrevealed.png", ArtPaths.unrevealedPin())
        assertEquals("art/pin/npc_stay.png", ArtPaths.npcPin(walking = false))
        assertEquals("art/pin/npc_walk.png", ArtPaths.npcPin(walking = true))
    }

    @Test
    fun `id 里的点保持原样 不换成别的字符`() {
        assertTrue(ArtPaths.npcAvatar("npc.lin").endsWith("npc.lin.png"))
        assertTrue(ArtPaths.item("res.lake_memory_fragment").endsWith("res.lake_memory_fragment.png"))
    }

    @Test
    fun `每个地点类型的针与缩略图路径互不重复`() {
        val pins = PlaceType.entries.map { ArtPaths.pin(it) }
        val thumbs = PlaceType.entries.map { ArtPaths.place(it) }

        assertEquals(pins.size, pins.toSet().size)
        assertEquals(thumbs.size, thumbs.toSet().size)
    }
}