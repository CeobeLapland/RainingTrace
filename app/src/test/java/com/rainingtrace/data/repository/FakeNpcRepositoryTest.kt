package com.rainingtrace.data.repository

import com.rainingtrace.data.content.ShippedContent
import com.rainingtrace.domain.npc.resolveSchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NPC 作息与地点的**交叉一致性**。
 *
 * 作息指向尚未落地地点（`places_design.md` 的坐标待填）在过渡期是预期状态，
 * 运行时 `resolveSchedule` 会逐条剔除悬空条目；但核心 5 人若任何时刻都解析不出
 * 位置，等于"地图上没人"——那是没有任何报错的坏法，此处的断言就是它的看门人。
 */
class FakeNpcRepositoryTest {

    private val npcs = FakeNpcRepository(ShippedContent.npcs)

    /** 原版 5 人是既有存档与地图的锚点，任何时刻都必须解析得出位置。 */
    @Test
    fun `core five npcs resolve a position at some point of the day`() = runTest {
        val places = ShippedContent.placeById
        val anchors = listOf("npc.bit.lin", "npc.bit.zhou", "npc.bit.xu", "npc.bit.he", "npc.bit.qi")
        val anchorProfiles = npcs.all().filter { it.id in anchors }
        assertEquals("核心 5 人必须都在内容里", 5, anchorProfiles.size)

        val ghost = anchorProfiles.filter { profile ->
            val schedule = resolveSchedule(profile, places)
            (0 until 24 * 60).none { schedule.presenceAt(it) != null }
        }.map { it.id }

        assertTrue("核心 NPC 任何时刻都解析不出位置（地图上看不见）：$ghost", ghost.isEmpty())
    }

    @Test
    fun `npc ids are unique`() = runTest {
        val ids = npcs.all().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `at least one npc is walking at some point of the day`() = runTest {
        val places = ShippedContent.placeById
        val schedules = npcs.all().map { resolveSchedule(it, places) }

        val anyWalking = (0 until 24 * 60).any { minute ->
            schedules.any { it.presenceAt(minute)?.walking == true }
        }

        assertTrue("没有任何 NPC 会在一天中的任何时刻走路", anyWalking)
    }
}