package com.rainingtrace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 本地优先（01_技术栈 §5）：轨迹/探索/足迹/记忆/库存全部先落 Room。
 * schema 导出到 app/schemas，版本化演进。
 *
 * v2：战争迷雾架构（track_points 成为空间真相，记忆/足迹坐标化，
 * fog 主键带格子档位）。开发期数据为测试数据，v1→v2 走 destructive。
 */
@Database(
    entities = [
        ExplorationCellEntity::class,
        TrackPointEntity::class,
        FootprintEventEntity::class,
        MemoryEntity::class,
        InventoryItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RainingTraceDatabase : RoomDatabase() {
    abstract fun explorationDao(): ExplorationDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun footprintDao(): FootprintDao
    abstract fun memoryDao(): MemoryDao
    abstract fun inventoryDao(): InventoryDao

    companion object {
        const val NAME = "rainingtrace.db"

        fun create(context: Context): RainingTraceDatabase =
            Room.databaseBuilder(context, RainingTraceDatabase::class.java, NAME)
                // v1→v2 结构不兼容；用户已确认开发期删库重来。
                // 正式有用户数据后必须改为显式 Migration（Hard Stop 规则）。
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
