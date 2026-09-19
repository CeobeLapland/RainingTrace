package com.rainingtrace.data.repository

import com.rainingtrace.data.local.ExplorationCellEntity
import com.rainingtrace.data.local.ExplorationDao
import com.rainingtrace.data.local.FootprintDao
import com.rainingtrace.data.local.FootprintEventEntity
import com.rainingtrace.data.local.InventoryDao
import com.rainingtrace.data.local.InventoryItemEntity
import com.rainingtrace.data.local.MemoryDao
import com.rainingtrace.data.local.MemoryEntity
import com.rainingtrace.data.local.TrackPointDao
import com.rainingtrace.data.local.TrackPointEntity
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.ExplorationState
import com.rainingtrace.domain.exploration.rank
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.footprint.TraceVisibility
import com.rainingtrace.domain.inventory.InventoryItem
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.InventoryState
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.memory.Mood
import com.rainingtrace.domain.track.TrackDay
import com.rainingtrace.domain.track.TrackPoint
import com.rainingtrace.domain.track.TrackRepository
import com.rainingtrace.domain.track.trackDayOf
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.WeatherKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 实现：本地优先，app 重启数据保留（MVP P0 Persistence 要求）。
 */

// 用控制字符分隔序列化 KV，避免与正文冲突；数字构造防止源码字面量被转义吞掉。
private val ENTRY_SEP = Char(1)
private val KV_SEP = Char(2)

private fun Map<String, String>.encodePayload(): String =
    entries.joinToString(ENTRY_SEP.toString()) { "${it.key}$KV_SEP${it.value}" }

private fun String.decodePayload(): Map<String, String> =
    if (isEmpty()) emptyMap() else split(ENTRY_SEP).mapNotNull { entry ->
        val kv = entry.split(KV_SEP, limit = 2)
        if (kv.size == 2) kv[0] to kv[1] else null
    }.toMap()

/**
 * 迷雾仓储：只读写当前格子档位 [level] 的行。
 * 域层只见 HexCellId；档位前缀不泄露到 domain。
 */
class RoomExplorationRepository(
    private val dao: ExplorationDao,
    private val gridManager: GridManager,
) : ExplorationRepository {

    private val level get() = gridManager.level

    override fun observeState(): Flow<ExplorationState> =
        dao.observeLevel(gridManager.level.cellKeyPrefix).map { rows -> rows.toExplorationState() }

    override suspend fun loadState(): ExplorationState =
        dao.levelCells(gridManager.level.cellKeyPrefix).toExplorationState()

    override suspend fun saveStates(states: Map<HexCellId, CellFogState>) {
        if (states.isEmpty()) return
        val now = System.currentTimeMillis()
        // DB 层防降级：内存快照可能过期，写入时与现有行取更高状态
        val existing = dao.levelCells(level.cellKeyPrefix).associate {
            parseCellKey(it.cellKey) to CellFogState.valueOf(it.fogState)
        }
        dao.upsertAll(
            states.map { (cell, state) ->
                val old = existing[cell]
                val merged = if (old != null && old.rank > state.rank) old else state
                ExplorationCellEntity(toCellKey(cell), merged.name, now)
            },
        )
    }

    /** 切换档位重建时清空本档位全部迷雾行（迷雾是轨迹点的可重建投影）。 */
    override suspend fun clearLevel() = dao.deleteLevel(level.cellKeyPrefix)

    private fun List<ExplorationCellEntity>.toExplorationState(): ExplorationState =
        ExplorationState(
            mapNotNull { row ->
                runCatching { parseCellKey(row.cellKey) to CellFogState.valueOf(row.fogState) }
                    .getOrNull()
            }.toMap(),
        )

    private fun toCellKey(cell: HexCellId): String =
        "${level.cellKeyPrefix}${cell.axialQ}:${cell.axialR}"

    private fun parseCellKey(key: String): HexCellId {
        val body = key.removePrefix(level.cellKeyPrefix)
        val parts = body.split(':')
        check(parts.size == 2) { "invalid fog cell key: $key" }
        return HexCellId(parts[0].toInt(), parts[1].toInt())
    }
}

class RoomTrackRepository(
    private val dao: TrackPointDao,
) : TrackRepository {

    override suspend fun append(point: TrackPoint) {
        dao.insert(
            TrackPointEntity(
                id = point.id,
                timestampEpochMs = point.timestampEpochMs,
                lat = point.coordinate.latDegrees,
                lng = point.coordinate.lngDegrees,
                accuracyMeters = point.accuracyMeters,
                source = point.source.name,
            ),
        )
    }

    override suspend fun latestPoint(): TrackPoint? = dao.latest()?.toDomain()

    override suspend fun between(fromEpochMs: Long, toEpochMs: Long): List<TrackPoint> =
        dao.between(fromEpochMs, toEpochMs).map { it.toDomain() }

    override suspend fun days(zoneOffsetMs: Long): List<TrackDay> =
        dao.daySummaries(zoneOffsetMs).map {
            trackDayOf(
                dayIndex = it.dayIndex,
                pointCount = it.pointCount,
                firstEpochMs = it.firstEpochMs,
                lastEpochMs = it.lastEpochMs,
            )
        }

    override suspend fun all(): List<TrackPoint> = dao.all().map { it.toDomain() }
}

private fun TrackPointEntity.toDomain(): TrackPoint = TrackPoint(
    id = id,
    timestampEpochMs = timestampEpochMs,
    coordinate = WorldCoordinate(lat, lng),
    accuracyMeters = accuracyMeters,
    source = LocationSource.valueOf(source),
)

class RoomFootprintRepository(
    private val dao: FootprintDao,
) : FootprintRepository {

    override suspend fun append(event: FootprintEvent) {
        dao.append(
            FootprintEventEntity(
                id = event.id,
                timestampEpochMs = event.timestampEpochMs,
                lat = event.coordinate.latDegrees,
                lng = event.coordinate.lngDegrees,
                eventType = event.eventType.name,
                visibility = event.visibility.name,
                payloadKeyValues = event.payload.encodePayload(),
            ),
        )
    }

    override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
        dao.eventsBetween(fromEpochMs, toEpochMs).map {
            FootprintEvent(
                id = it.id,
                timestampEpochMs = it.timestampEpochMs,
                coordinate = WorldCoordinate(it.lat, it.lng),
                eventType = FootprintEventType.valueOf(it.eventType),
                payload = it.payloadKeyValues.decodePayload(),
                visibility = TraceVisibility.valueOf(it.visibility),
            )
        }
}

class RoomInventoryRepository(
    private val dao: InventoryDao,
) : InventoryRepository {

    override fun observeState(): Flow<InventoryState> =
        dao.observeAll().map { rows ->
            InventoryState(
                rows.associate {
                    it.resourceId to InventoryItem(
                        resourceId = it.resourceId,
                        quantity = it.quantity,
                        firstAcquiredAtEpochMs = it.firstAcquiredAtEpochMs,
                        lastAcquiredAtEpochMs = it.lastAcquiredAtEpochMs,
                    )
                },
            )
        }

    override suspend fun loadState(): InventoryState =
        dao.getAll().associateByTo(mutableMapOf()) { it.resourceId }
            .let { rows ->
                InventoryState(
                    rows.mapValues { (_, e) ->
                        InventoryItem(
                            resourceId = e.resourceId,
                            quantity = e.quantity,
                            firstAcquiredAtEpochMs = e.firstAcquiredAtEpochMs,
                            lastAcquiredAtEpochMs = e.lastAcquiredAtEpochMs,
                        )
                    },
                )
            }

    override suspend fun saveState(state: InventoryState) {
        dao.replaceAll(
            state.items.values.map {
                InventoryItemEntity(
                    resourceId = it.resourceId,
                    quantity = it.quantity,
                    firstAcquiredAtEpochMs = it.firstAcquiredAtEpochMs,
                    lastAcquiredAtEpochMs = it.lastAcquiredAtEpochMs,
                )
            },
        )
    }
}

class RoomMemoryRepository(
    private val dao: MemoryDao,
) : MemoryRepository {

    override suspend fun save(memory: MemoryNode) {
        dao.upsert(
            MemoryEntity(
                id = memory.id,
                createdAtEpochMs = memory.createdAtEpochMs,
                lat = memory.coordinate.latDegrees,
                lng = memory.coordinate.lngDegrees,
                text = memory.text,
                mood = memory.mood?.name,
                tags = memory.tags.joinToString(TAG_SEP.toString()),
                mediaRefs = memory.mediaRefs.joinToString(TAG_SEP.toString()),
                audioRef = memory.audioRef,
                weatherKind = memory.weather?.name,
                season = memory.season?.name,
                sourceEventId = memory.sourceEventId,
            ),
        )
    }

    override suspend fun latest(limit: Int): List<MemoryNode> =
        dao.latest(limit).map { it.toDomain() }

    private fun MemoryEntity.toDomain(): MemoryNode = MemoryNode(
        id = id,
        createdAtEpochMs = createdAtEpochMs,
        coordinate = WorldCoordinate(lat, lng),
        text = text,
        mood = mood?.let { Mood.valueOf(it) },
        tags = if (tags.isEmpty()) emptySet() else tags.split(TAG_SEP).toSet(),
        mediaRefs = if (mediaRefs.isEmpty()) emptyList() else mediaRefs.split(TAG_SEP),
        audioRef = audioRef,
        weather = weatherKind?.let { runCatching { WeatherKind.valueOf(it) }.getOrNull() },
        season = season?.let { runCatching { Season.valueOf(it) }.getOrNull() },
        sourceEventId = sourceEventId,
    )

    private companion object {
        val TAG_SEP = Char(0)
    }
}
