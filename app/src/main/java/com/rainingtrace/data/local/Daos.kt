package com.rainingtrace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExplorationDao {
    @Query("SELECT * FROM exploration_cells")
    fun observeAll(): Flow<List<ExplorationCellEntity>>

    @Query("SELECT * FROM exploration_cells WHERE cellId IN (:cellIds)")
    suspend fun byIds(cellIds: List<String>): List<ExplorationCellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cells: List<ExplorationCellEntity>)

    @Query("SELECT COUNT(*) FROM exploration_cells")
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

    @Query("SELECT * FROM memories WHERE cellId = :cellId ORDER BY createdAtEpochMs DESC")
    suspend fun byCell(cellId: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAtEpochMs DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<MemoryEntity>
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items")
    fun observeAll(): Flow<List<InventoryItemEntity>>

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
