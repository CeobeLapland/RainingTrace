package com.rainingtrace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 本地优先（01_技术栈 §5）：探索/足迹/记忆/库存全部先落 Room。
 * schema 导出到 app/schemas，版本化演进。
 */
@Database(
    entities = [
        ExplorationCellEntity::class,
        FootprintEventEntity::class,
        MemoryEntity::class,
        InventoryItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RainingTraceDatabase : RoomDatabase() {
    abstract fun explorationDao(): ExplorationDao
    abstract fun footprintDao(): FootprintDao
    abstract fun memoryDao(): MemoryDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        const val NAME = "rainingtrace.db"

        fun create(context: Context): RainingTraceDatabase =
            Room.databaseBuilder(context, RainingTraceDatabase::class.java, NAME)
                .build()
    }
}
