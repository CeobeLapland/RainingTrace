package com.rainingtrace.data.repository

import com.rainingtrace.data.content.ShippedContent
import com.rainingtrace.domain.npc.resolveSchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NPC 仓储的交叉一致性。
 *
 * 内容可以半成品：作息为空（人还没安排进地图）是正常状态。
 * 所以这里只要求"填了的部分自洽"，不要求每个人都排进地图。
 */
class FakeNpcRepositoryTest {

    private val npcs = FakeNpcRepository(ShippedContent.npcs)

    @Test
    fun `npc ids are unique`() = runTest {
        val ids = npcs.all().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /**
     * 只要内容里有作息，就必须至少有一位 NPC 在某个时刻走路——
     * 否则说明所有作息都被悬空地点吃掉了（那种坏法没有任何报错）。
     * 全部作息为空时放行（半成品内容）。
     */
    @Test
    fun `at least one npc is walking at some point of the day`() = runTest {
        val scheduled = npcs.all().filter { it.schedule.isNotEmpty() }
        if (scheduled.isEmpty()) return@runTest

        val places = ShippedContent.placeById
        val schedules = scheduled.map { resolveSchedule(it, places) }

        val anyWalking = (0 until 24 * 60).any { minute ->
            schedules.any { it.presenceAt(minute)?.walking == true }
        }

        assertTrue("有作息内容，却没有任何 NPC 在任何时刻走路（地点可能全悬空）", anyWalking)
    }
}