package com.rainingtrace.domain.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentMergeTest {

    private data class Item(val id: String, val value: String)

    private fun merge(
        builtIn: List<Item>,
        overlay: List<Item> = emptyList(),
        removedIds: Set<String> = emptySet(),
        report: ContentReport = ContentReport(),
    ) = ContentMerge.byId(
        builtIn = builtIn,
        overlay = overlay,
        removedIds = removedIds,
        file = "overlay.json",
        idOf = { it.id },
        report = report,
    )

    @Test
    fun `没有覆盖层时原样返回内置内容`() {
        val builtIn = listOf(Item("a", "1"), Item("b", "2"))
        assertEquals(builtIn, merge(builtIn))
    }

    @Test
    fun `覆盖层命中 id 时原位替换，顺序不变`() {
        val merged = merge(
            builtIn = listOf(Item("a", "1"), Item("b", "2"), Item("c", "3")),
            overlay = listOf(Item("b", "改过")),
        )

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("改过", merged[1].value)
    }

    @Test
    fun `覆盖层新 id 追加到末尾并记一笔`() {
        val report = ContentReport()
        val merged = merge(
            builtIn = listOf(Item("a", "1")),
            overlay = listOf(Item("new", "2")),
            report = report,
        )

        assertEquals(listOf("a", "new"), merged.map { it.id })
        assertEquals(0, report.errorCount)
        assertEquals(1, report.items.size)
    }

    @Test
    fun `removedIds 能删掉内置条目`() {
        val merged = merge(
            builtIn = listOf(Item("a", "1"), Item("b", "2")),
            removedIds = setOf("a"),
        )

        assertEquals(listOf("b"), merged.map { it.id })
    }

    @Test
    fun `removedIds 里不存在的 id 只记一笔，不算错`() {
        val report = ContentReport()
        val merged = merge(builtIn = listOf(Item("a", "1")), removedIds = setOf("nope"), report = report)

        assertEquals(listOf("a"), merged.map { it.id })
        assertEquals(0, report.errorCount)
        assertEquals(1, report.items.size)
    }

    @Test
    fun `内置层同 id 重复时后者胜且报错`() {
        val report = ContentReport()
        val merged = merge(
            builtIn = listOf(Item("a", "先"), Item("a", "后")),
            report = report,
        )

        assertEquals(1, merged.size)
        assertEquals("后", merged.first().value)
        assertTrue(report.errorCount >= 1)
    }
}