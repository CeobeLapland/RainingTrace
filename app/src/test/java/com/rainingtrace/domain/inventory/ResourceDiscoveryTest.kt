package com.rainingtrace.domain.inventory

import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceDiscoveryTest {

    private val somewhere = WorldCoordinate(39.7326, 116.1712)

    private fun acquire(
        resourceId: String,
        atEpochMs: Long,
        type: FootprintEventType = FootprintEventType.PLACE_OBSERVED,
        payload: Map<String, String> = mapOf("resourceId" to resourceId),
        id: String = "e-$atEpochMs-$resourceId",
    ) = FootprintEvent(
        id = id,
        timestampEpochMs = atEpochMs,
        coordinate = somewhere,
        eventType = type,
        payload = payload,
    )

    @Test
    fun `归并同一资源的首次与最近时间`() {
        val result = resourceDiscoveries(
            listOf(
                acquire("res.moss", 3_000, id = "c"),
                acquire("res.moss", 1_000, id = "a"),
                acquire("res.moss", 2_000, id = "b"),
            ),
        )

        val moss = result.getValue("res.moss")
        assertEquals(1_000, moss.firstAtEpochMs)
        assertEquals(3_000, moss.lastAtEpochMs)
        assertEquals(3, moss.timesAcquired)
    }

    @Test
    fun `非采集类事件与缺少 resourceId 的事件都不算发现`() {
        val result = resourceDiscoveries(
            listOf(
                acquire("res.moss", 1_000, type = FootprintEventType.NPC_MET),
                acquire("res.moss", 2_000, payload = mapOf("placeId" to "place.bit.north_lake")),
                acquire("res.moss", 3_000, payload = mapOf("resourceId" to "  ")),
            ),
        )

        assertTrue("这些事件都不该产生发现：$result", result.isEmpty())
    }

    @Test
    fun `制作产出没有足迹 靠当前持有补上`() {
        // 草绳是"干草 + 芦苇叶"做出来的，从不写足迹事件。
        val result = resourceDiscoveries(
            events = emptyList(),
            holdings = mapOf("res.rope" to 5_000L),
        )

        val rope = result.getValue("res.rope")
        assertEquals(5_000, rope.firstAtEpochMs)
        assertEquals(5_000, rope.lastAtEpochMs)
        assertEquals(1, rope.timesAcquired)
    }

    @Test
    fun `持有的首次时间更早时覆盖事件的首次时间`() {
        val result = resourceDiscoveries(
            events = listOf(acquire("res.moss", 9_000)),
            holdings = mapOf("res.moss" to 4_000L),
        )

        val moss = result.getValue("res.moss")
        assertEquals("首次获得应该取更早的那个", 4_000, moss.firstAtEpochMs)
        assertEquals(9_000, moss.lastAtEpochMs)
        // 持有只是补首次时间，不是另一条获得记录，所以次数仍按事件算。
        assertEquals(1, moss.timesAcquired)
    }

    @Test
    fun `没有事件也没有持有就没有发现`() {
        val result = resourceDiscoveries(emptyList())
        assertNull(result["res.moss"])
    }
}