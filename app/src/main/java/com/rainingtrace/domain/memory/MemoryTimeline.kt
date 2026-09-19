package com.rainingtrace.domain.memory

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 日记时间线中的一天：同一天的记忆（保持传入顺序，即时间倒序）。 */
data class MemoryDay(
    val date: LocalDate,
    val memories: List<MemoryNode>,
)

/**
 * 把记忆按**本地日期**分组（GDD §06 记忆/日记）。
 *
 * 输入通常是 [MemoryRepository.latest] 的时间倒序结果；输出保持同样的倒序：
 * 最近的一天在最前，天内记忆也维持原有倒序。纯函数，便于单测。
 */
fun groupMemoriesByDay(
    memories: List<MemoryNode>,
    zone: ZoneId,
): List<MemoryDay> = memories
    .groupBy { Instant.ofEpochMilli(it.createdAtEpochMs).atZone(zone).toLocalDate() }
    .entries
    .sortedByDescending { it.key }
    .map { (date, items) -> MemoryDay(date, items) }
