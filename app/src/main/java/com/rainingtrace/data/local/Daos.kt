package com.rainingtrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExplorationDao {
    // :prefix 是档位前缀（如 "gm:"），SQL 侧拼 % 做前缀匹配。
    @Query("SELECT * FROM exploration_cells WHERE cellKey LIKE :prefix || '%'")
    fun observeLevel(prefix: String): Flow<List<ExplorationCellEntity>>

    @Query("SELECT * FROM exploration_cells WHERE cellKey LIKE :prefix || '%'")
    suspend fun levelCells(prefix: String): List<ExplorationCellEntity>

    @Query("SELECT * FROM exploration_cells WHERE cellKey IN (:cellKeys)")
    suspend fun byKeys(cellKeys: List<String>): List<ExplorationCellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cells: List<ExplorationCellEntity>)

    @Query("DELETE FROM exploration_cells WHERE cellKey LIKE :prefix || '%'")
    suspend fun deleteLevel(prefix: String)

    @Query("SELECT COUNT(*) FROM exploration_cells WHERE cellKey LIKE :prefix || '%'")
    suspend fun countLevel(prefix: String): Int
}

/** 轨迹按本地日汇总的一行（轨迹日历用），不加载具体点。 */
data class TrackDayRow(
    val dayIndex: Long,
    val pointCount: Int,
    val firstEpochMs: Long,
    val lastEpochMs: Long,
)

@Dao
interface TrackPointDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: TrackPointEntity)

    @Query(
        "SELECT * FROM track_points " +
            "WHERE timestampEpochMs BETWEEN :fromMs AND :toMs " +
            "ORDER BY timestampEpochMs ASC",
    )
    suspend fun between(fromMs: Long, toMs: Long): List<TrackPointEntity>

    /**
     * 按本地日分桶的汇总（日期倒序）。86400000 = 一天的毫秒数。
     * 时间戳是 UTC，所以先加时区偏移再除以一天；中国无夏令时，固定偏移是精确的。
     */
    @Query(
        "SELECT (timestampEpochMs + :zoneOffsetMs) / 86400000 AS dayIndex, " +
            "COUNT(*) AS pointCount, " +
            "MIN(timestampEpochMs) AS firstEpochMs, " +
            "MAX(timestampEpochMs) AS lastEpochMs " +
            "FROM track_points GROUP BY dayIndex ORDER BY dayIndex DESC",
    )
    suspend fun daySummaries(zoneOffsetMs: Long): List<TrackDayRow>

    @Query("SELECT * FROM track_points ORDER BY timestampEpochMs ASC")
    suspend fun all(): List<TrackPointEntity>

    @Query("SELECT * FROM track_points ORDER BY timestampEpochMs DESC LIMIT 1")
    suspend fun latest(): TrackPointEntity?

    @Query("SELECT COUNT(*) FROM track_points")
    suspend fun count(): Int
}

@Dao
interface FootprintDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun append(event: FootprintEventEntity): Long

    @Query(
        "SELECT * FROM footprint_events " +
            "WHERE timestampEpochMs BETWEEN :fromMs AND :toMs " +
            "ORDER BY timestampEpochMs ASC",
    )
    suspend fun eventsBetween(fromMs: Long, toMs: Long): List<FootprintEventEntity>

    @Query(
        "SELECT * FROM footprint_events " +
            "WHERE eventType = :eventType " +
            "ORDER BY timestampEpochMs ASC",
    )
    suspend fun eventsOfType(eventType: String): List<FootprintEventEntity>

    @Query("SELECT COUNT(*) FROM footprint_events")
    suspend fun count(): Int
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<MemoryEntity>
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items")
    fun observeAll(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items")
    suspend fun getAll(): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE resourceId = :resourceId")
    suspend fun delete(resourceId: String)

    /**
     * 原子替换库存（结算后整体写入）。
     * MVP 库存以本地为真相；P1 服务端确认后同样走此入口。
     */
    @Transaction
    suspend fun replaceAll(items: List<InventoryItemEntity>) {
        deleteAll()
        items.forEach { upsert(it) }
    }

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAll()
}

/**
 * 仓库（v7）：与 [InventoryDao] 同形，另加一个**跨两表的事务**，
 * 让"背包 ↔ 仓库"的搬运要么全成、要么全不成。
 *
 * 它同时声明 `inventory_items` 的写入：Room 允许一个 DAO 碰任意表，
 * 而事务边界必须落在同一个 DAO 上。
 */
@Dao
interface WarehouseDao {
    @Query("SELECT * FROM warehouse_items")
    fun observeAll(): Flow<List<WarehouseItemEntity>>

    @Query("SELECT * FROM warehouse_items")
    suspend fun getAll(): List<WarehouseItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WarehouseItemEntity>)

    @Query("DELETE FROM warehouse_items")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryItems(items: List<InventoryItemEntity>)

    @Query("DELETE FROM inventory_items")
    suspend fun deleteAllInventoryItems()

    /** 原子替换仓库（同 [InventoryDao.replaceAll] 的口径）。 */
    @Transaction
    suspend fun replaceAll(items: List<WarehouseItemEntity>) {
        deleteAll()
        upsertAll(items)
    }

    /** 一次事务写完背包与仓库：搬运中途崩溃不会丢东西、也不会凭空多出来。 */
    @Transaction
    suspend fun replaceBoth(
        inventory: List<InventoryItemEntity>,
        warehouse: List<WarehouseItemEntity>,
    ) {
        deleteAllInventoryItems()
        upsertInventoryItems(inventory)
        deleteAll()
        upsertAll(warehouse)
    }
}

@Dao
interface NpcMessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: NpcMessageEntity)

    /**
     * 全量消息（时间升序）。会话列表用它一次性取回后在领域侧分组——
     * 只有一个 Flow、没有失效漏报风险；NPC 只有几个、消息量级在数百条，
     * 等真到万级再改成 GROUP BY 汇总。
     */
    @Query("SELECT * FROM npc_messages ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<NpcMessageEntity>>

    /** 某个 NPC 的最近 [limit] 条（时间倒序，领域侧再反转）。 */
    @Query(
        "SELECT * FROM npc_messages WHERE npcId = :npcId " +
            "ORDER BY createdAtEpochMs DESC LIMIT :limit",
    )
    fun observeThread(npcId: String, limit: Int): Flow<List<NpcMessageEntity>>

    /** 未读总数（只算 NPC 发来的），给底栏红点。 */
    @Query("SELECT COUNT(*) FROM npc_messages WHERE isRead = 0 AND speaker = 'NPC'")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE npc_messages SET isRead = 1 WHERE npcId = :npcId AND isRead = 0")
    suspend fun markRead(npcId: String)
}

@Dao
interface NpcStateDao {
    @Query("SELECT * FROM npc_states")
    fun observeAll(): Flow<List<NpcStateEntity>>

    @Query("SELECT * FROM npc_states WHERE npcId = :npcId")
    suspend fun byId(npcId: String): NpcStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: NpcStateEntity)
}

@Dao
interface NpcCommitmentDao {
    /** 表很小（同时只有几条 AGREED），全量取回在领域侧筛，不做增量查询。 */
    @Query("SELECT * FROM npc_commitments")
    suspend fun all(): List<NpcCommitmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(commitment: NpcCommitmentEntity)
}
