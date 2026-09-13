package com.rainingtrace.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 探索状态：每行一个已知 cell。
 */
@Entity(tableName = "exploration_cells")
data class ExplorationCellEntity(
    @PrimaryKey val cellId: String,
    val fogState: String,
    val updatedAtEpochMs: Long,
)

/**
 * 足迹事件：append-only。
 */
@Entity(tableName = "footprint_events")
data class FootprintEventEntity(
    @PrimaryKey val id: String,
    val timestampEpochMs: Long,
    val cellId: String,
    val eventType: String,
    val visibility: String,
    val payloadKeyValues: String = "",
)

/**
 * 记忆节点。
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    val cellId: String,
    val text: String,
    val mood: String?,
    val tags: String,
    val mediaRefs: String,
    val sourceEventId: String?,
)

/**
 * 库存：每行一种资源。
 */
@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val resourceId: String,
    val quantity: Int,
    val firstAcquiredAtEpochMs: Long,
    val lastAcquiredAtEpochMs: Long,
)
