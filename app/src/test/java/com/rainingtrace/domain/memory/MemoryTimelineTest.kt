package com.rainingtrace.domain.memory

import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MemoryTimelineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val origin = WorldCoordinate(39.7326, 116.1712)

    private fun memory(id: String, isoInstant: String) = MemoryNode(
        id = id,
        createdAtEpochMs = Instant.parse(isoInstant).toEpochMilli(),
        coordinate = origin,
    )

    @Test
    fun `groups by local date with newest day first`() {
        val days = groupMemoriesByDay(
            listOf(
                memory("c", "2026-09-19T10:00:00Z"), // 北京 09-19 18:00
                memory("b", "2026-09-19T02:00:00Z"), // 北京 09-19 10:00
                memory("a", "2026-09-18T02:00:00Z"), // 北京 09-18 10:00
            ),
            zone,
        )

        assertEquals(2, days.size)
        assertEquals("2026-09-19", days[0].date.toString())
        assertEquals(listOf("c", "b"), days[0].memories.map { it.id })
        assertEquals("2026-09-18", days[1].date.toString())
        assertEquals(listOf("a"), days[1].memories.map { it.id })
    }

    @Test
    fun `late UTC evening belongs to next local day`() {
        // UTC 09-18 20:00 = 北京 09-19 04:00
        val days = groupMemoriesByDay(listOf(memory("x", "2026-09-18T20:00:00Z")), zone)

        assertEquals("2026-09-19", days.single().date.toString())
    }

    @Test
    fun `empty input yields empty timeline`() {
        assertEquals(0, groupMemoriesByDay(emptyList(), zone).size)
    }
}
