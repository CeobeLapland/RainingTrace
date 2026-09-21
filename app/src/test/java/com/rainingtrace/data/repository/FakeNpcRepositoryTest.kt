package com.rainingtrace.data.repository

import com.rainingtrace.domain.npc.resolveSchedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNpcRepositoryTest {

    @Test
    fun `every scheduled place exists in the place repository`() = runTest {
        val placeIds = FakePlaceRepository().all().map { it.id }.toSet()

        FakeNpcRepository().all().forEach { npc ->
            npc.schedule.forEach { entry ->
                assertTrue(
                    "${npc.id} 的作息引用了不存在的地点 ${entry.placeId}",
                    entry.placeId in placeIds,
                )
            }
        }
    }

    @Test
    fun `npc ids are unique`() = runTest {
        val ids = FakeNpcRepository().all().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `at least one npc is walking at some point of the day`() = runTest {
        val places = FakePlaceRepository().all().associateBy { it.id }
        val schedules = FakeNpcRepository().all().map { resolveSchedule(it, places) }

        val anyWalking = (0 until 24 * 60).any { minute ->
            schedules.any { it.presenceAt(minute)?.walking == true }
        }

        assertTrue("没有任何 NPC 会在一天中的任何时刻走路", anyWalking)
    }
}
