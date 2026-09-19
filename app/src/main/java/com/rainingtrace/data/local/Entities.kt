package com.rainingtrace.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 探索状态（战争迷雾投影）：每行一个已知 cell。
 * 主键带格子档位前缀（如 "gm:12:-7"），切换档位后各行互不混淆。
 */
@Entity(tableName = "exploration_cells")
data class ExplorationCellEntity(
    @PrimaryKey val cellKey: String,
    val fogState: String,
    val updatedAtEpochMs: Long,
)

/**
 * 稳定轨迹点（去噪后）：世界空间真相的唯一持久形态。
 * 迷雾、今日轨迹线都从这里派生。
 */
@Entity(
    tableName = "track_points",
    indices = [Index(value = ["timestampEpochMs"])],
)
data class TrackPointEntity(
    @PrimaryKey val id: String,
    val timestampEpochMs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Double,
    val source: String,
)

/**
 * 足迹事件：append-only；位置为连续坐标。
 */
@Entity(tableName = "footprint_events")
data class FootprintEventEntity(
    @PrimaryKey val id: String,
    val timestampEpochMs: Long,
    val lat: Double,
    val lng: Double,
    val eventType: String,
    val visibility: String,
    val payloadKeyValues: String = "",
)

/**
 * 记忆节点；位置为连续坐标，不依附格子。
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    val lat: Double,
    val lng: Double,
    val text: String,
    val mood: String?,
    val tags: String,
    val mediaRefs: String,
    /** 语音 URI；MVP 每条最多一段。v3 新增。 */
    val audioRef: String?,
    /** 创建时的天气类型名；v4 新增，老数据为 null（未知）。 */
    val weatherKind: String?,
    /** 创建时的季节名；v4 新增，老数据为 null（季节规则未定）。 */
    val season: String?,
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
