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
 * 内容外置之后，"作息里写 `FakePlaceRepository.XXX.id` 常量"那种编译期绑定的保证
 * 没有了；这条断言就是它的替代品（对外置内容而言，`ShippedContentTest` 里
 * 也有同一件事的检查，两层都留着不嫌多——写错 placeId 只会让那个人整天不出现，
 * 是完全没有报错的坏法）。
 */
class FakeNpcRepositoryTest {

    private val npcs = FakeNpcRepository(ShippedContent.npcs)

    @Test
    fun `every scheduled place exists in the place repository`() = runTest {
        val placeIds = ShippedContent.places.map { it.id }.toSet()

        npcs.all().forEach { npc ->
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