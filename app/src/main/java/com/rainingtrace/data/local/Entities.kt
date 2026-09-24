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

/**
 * 仓库（v7）：家的存储，与随身背包**同形但分表**。
 *
 * 分表而不是给 `inventory_items` 加一列 `container`：后者会让既有的
 * `replaceAll`（全表删除再写入）在写背包时把仓库行一起删掉，动的是既有结算路径；
 * 分表则一行既有代码都不用碰。
 */
@Entity(tableName = "warehouse_items")
data class WarehouseItemEntity(
    @PrimaryKey val resourceId: String,
    val quantity: Int,
    val firstAcquiredAtEpochMs: Long,
    val lastAcquiredAtEpochMs: Long,
)

/**
 * NPC 消息（append-only；只有未读标记会被更新）。
 *
 * 复合索引 `(npcId, createdAtEpochMs)` 同时服务两件事：会话列表按 npcId 分组取最后一条、
 * 聊天线程按 npcId 分页排序。单列 `npcId` 索引被它前缀覆盖，不要再建。
 */
@Entity(
    tableName = "npc_messages",
    indices = [Index(value = ["npcId", "createdAtEpochMs"])],
)
data class NpcMessageEntity(
    @PrimaryKey val id: String,
    val npcId: String,
    /** PLAYER / NPC。 */
    val speaker: String,
    val text: String,
    val createdAtEpochMs: Long,
    /** NPC 发来的未读消息（玩家自己发的一律已读）。 */
    val isRead: Boolean,
    /** PLAYER / TEMPLATE / AI：给将来的 LLM 留的口子。 */
    val source: String,
    /** 解析出的话题名，可空。 */
    val topic: String?,
    /** 主动消息是哪条规则发的，可空。 */
    val ruleId: String?,
)

/**
 * 玩家与某个 NPC 之间的**可变**状态（每个 NPC 一行）。
 *
 * "见过几次/上次在哪遇见"不在这里——那些从 `NPC_MET` / `NPC_TALKED` 足迹派生。
 */
@Entity(tableName = "npc_states")
data class NpcStateEntity(
    @PrimaryKey val npcId: String,
    val affection: Int,
    /** 最近一次**事件**情绪名；基线情绪不落库（由世界状态派生）。 */
    val mood: String,
    val moodSinceEpochMs: Long,
    val lastInteractionAtEpochMs: Long?,
    val todayAffectionGain: Int,
    val todayDateKey: String,
    val updatedAtEpochMs: Long,
)

/**
 * 约定（片 3）：玩家约了某个 NPC 见面，他答应了。
 *
 * 状态机就是 [status]（AGREED → KEPT/MISSED），所以不需要额外的去重记录。
 * 表很小（同时只有几条 AGREED），因此不加索引、也不做增量查询。
 */
@Entity(tableName = "npc_commitments")
data class NpcCommitmentEntity(
    @PrimaryKey val id: String,
    val npcId: String,
    val placeId: String,
    /** 约定日期（yyyy-MM-dd，世界时区）。 */
    val dateKey: String,
    val startMinute: Int,
    val endMinute: Int,
    val travelMinutes: Int,
    val status: String,
    val createdAtEpochMs: Long,
    val resolvedAtEpochMs: Long?,
)
