package com.rainingtrace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地优先（01_技术栈 §5）：轨迹/探索/足迹/记忆/库存全部先落 Room。
 * schema 导出到 app/schemas，版本化演进。
 *
 * v2：战争迷雾架构（track_points 成为空间真相，记忆/足迹坐标化，
 * fog 主键带格子档位）。开发期数据为测试数据，v1→v2 走 destructive。
 * v3：记忆支持一段语音（memories.audioRef）。**走显式迁移，不删库**。
 * v4：记忆带当时的世界状态（memories.weatherKind / season）。
 * v5：NPC 消息与关系/情绪状态（npc_messages / npc_states）。
 * v6：约定（npc_commitments）——他答应了就真的会去。
 * v7：仓库（warehouse_items）——家的存储与随身背包分表。
 */
@Database(
    entities = [
        ExplorationCellEntity::class,
        TrackPointEntity::class,
        FootprintEventEntity::class,
        MemoryEntity::class,
        InventoryItemEntity::class,
        WarehouseItemEntity::class,
        NpcMessageEntity::class,
        NpcStateEntity::class,
        NpcCommitmentEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class RainingTraceDatabase : RoomDatabase() {
    abstract fun explorationDao(): ExplorationDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun footprintDao(): FootprintDao
    abstract fun memoryDao(): MemoryDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun npcMessageDao(): NpcMessageDao
    abstract fun npcStateDao(): NpcStateDao
    abstract fun npcCommitmentDao(): NpcCommitmentDao

    companion object {
        const val NAME = "rainingtrace.db"

        /** v3：记忆新增可空语音列；已有记忆保持不变（音频为 null）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memories ADD COLUMN audioRef TEXT")
            }
        }

        /**
         * v4：记忆落档创建时的天气/季节。
         * 老数据的这两列只能是 null（当时没记），不做回填——回填等于编造历史。
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memories ADD COLUMN weatherKind TEXT")
                db.execSQL("ALTER TABLE memories ADD COLUMN season TEXT")
            }
        }

        /**
         * v5：NPC 消息与关系/情绪状态。
         *
         * SQL 是照 KSP 生成的 `schemas/.../5.json` 里的 `createSql` **逐字抄**的
         * （表名/列名带反引号、Boolean 是 INTEGER NOT NULL、可空列不带 NOT NULL）。
         * 改动实体后必须重新生成 schema 再回来核对，否则老库升级会抛
         * "Migration didn't properly handle"。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `npc_messages` (`id` TEXT NOT NULL, " +
                        "`npcId` TEXT NOT NULL, `speaker` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, `topic` TEXT, `ruleId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_npc_messages_npcId_createdAtEpochMs` " +
                        "ON `npc_messages` (`npcId`, `createdAtEpochMs`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `npc_states` (`npcId` TEXT NOT NULL, " +
                        "`affection` INTEGER NOT NULL, `mood` TEXT NOT NULL, " +
                        "`moodSinceEpochMs` INTEGER NOT NULL, `lastInteractionAtEpochMs` INTEGER, " +
                        "`todayAffectionGain` INTEGER NOT NULL, `todayDateKey` TEXT NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`npcId`))",
                )
            }
        }

        /**
         * v6：约定表（片 3）。同样照 `schemas/.../6.json` 的 createSql 逐字抄。
         * 无索引（表很小）。
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `npc_commitments` (`id` TEXT NOT NULL, " +
                        "`npcId` TEXT NOT NULL, `placeId` TEXT NOT NULL, `dateKey` TEXT NOT NULL, " +
                        "`startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, " +
                        "`travelMinutes` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `resolvedAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * v7：仓库表。同样照 `schemas/.../7.json` 的 createSql 逐字抄。
         * 无索引（每行一种资源，量级与资源目录同阶）。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `warehouse_items` (`resourceId` TEXT NOT NULL, " +
                        "`quantity` INTEGER NOT NULL, `firstAcquiredAtEpochMs` INTEGER NOT NULL, " +
                        "`lastAcquiredAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`resourceId`))",
                )
            }
        }

        fun create(context: Context): RainingTraceDatabase =
            Room.databaseBuilder(context, RainingTraceDatabase::class.java, NAME)
                // v1→v2 结构不兼容；用户已确认开发期删库重来。
                // v2→v3 起改为显式 Migration：正式有用户数据后不允许再 destructive。
                .addMigrations(
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .fallbackToDestructiveMigration(false)
                .build()
    }
}
