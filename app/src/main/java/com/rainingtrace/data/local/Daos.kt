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
