package com.rainingtrace.data.repository

import com.rainingtrace.data.local.ExplorationCellEntity
import com.rainingtrace.data.local.ExplorationDao
import com.rainingtrace.data.local.FootprintDao
import com.rainingtrace.data.local.FootprintEventEntity
import com.rainingtrace.data.local.InventoryDao
import com.rainingtrace.data.local.InventoryItemEntity
import com.rainingtrace.data.local.MemoryDao
import com.rainingtrace.data.local.MemoryEntity
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
import com.rainingtrace.domain.map.HexCellId
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.memory.Mood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 实现：本地优先，app 重启数据保留（MVP P0 Persistence 要求）。
 */

private const val ENTRY_SEP = '\u0001'
private const val KV_SEP = '\u0002'

private fun Map<String, String>.encodePayload(): String =
    entries.joinToString(ENTRY_SEP.toString()) { "${it.key}$KV_SEP${it.value}" }

private fun String.decodePayload(): Map<String, String> =
    if (isEmpty()) emptyMap() else split(ENTRY_SEP).mapNotNull { entry ->
        val kv = entry.split(KV_SEP, limit = 2)
        if (kv.size == 2) kv[0] to kv[1] else null
    }.toMap()

class RoomExplorationRepository(
    private val dao: ExplorationDao,
) : ExplorationRepository {

    override fun observeState(): Flow<ExplorationState> =
        dao.observeAll().map { rows -> rows.toExplorationState() }

    override suspend fun loadState(): ExplorationState = dao.getAll().toExplorationState()

    override suspend fun saveStates(states: Map<HexCellId, CellFogState>) {
        if (states.isEmpty()) return
        val now = System.currentTimeMillis()
        // DB 层防降级：内存快照可能过期，写入时与现有行取更高状态
        val existing = dao.getAll().associate {
            HexCellId.fromStableString(it.cellId) to CellFogState.valueOf(it.fogState)
        }
        dao.upsertAll(
            states.map { (cell, state) ->
                val old = existing[cell]
                val merged = if (old != null && old.rank > state.rank) old else state
                ExplorationCellEntity(cell.toStableString(), merged.name, now)
            },
        )
    }

    private fun List<ExplorationCellEntity>.toExplorationState(): ExplorationState = ExplorationState(
        associate { HexCellId.fromStableString(it.cellId) to CellFogState.valueOf(it.fogState) },
    )
}

class RoomFootprintRepository(
    private val dao: FootprintDao,
) : FootprintRepository {

    override suspend fun append(event: FootprintEvent) {
        dao.append(
            FootprintEventEntity(
                id = event.id,
                timestampEpochMs = event.timestampEpochMs,
                cellId = event.cellId.toStableString(),
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
                cellId = HexCellId.fromStableString(it.cellId),
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
                cellId = memory.cellId.toStableString(),
                text = memory.text,
                mood = memory.mood?.name,
                tags = memory.tags.joinToString(TAG_SEP.toString()),
                mediaRefs = memory.mediaRefs.joinToString(TAG_SEP.toString()),
                sourceEventId = memory.sourceEventId,
            ),
        )
    }

    override suspend fun byCell(cellId: HexCellId): List<MemoryNode> =
        dao.byCell(cellId.toStableString()).map { it.toDomain() }

    override suspend fun latest(limit: Int): List<MemoryNode> =
        dao.latest(limit).map { it.toDomain() }

    private fun MemoryEntity.toDomain(): MemoryNode = MemoryNode(
        id = id,
        createdAtEpochMs = createdAtEpochMs,
        cellId = HexCellId.fromStableString(cellId),
        text = text,
        mood = mood?.let { Mood.valueOf(it) },
        tags = if (tags.isEmpty()) emptySet() else tags.split(TAG_SEP).toSet(),
        mediaRefs = if (mediaRefs.isEmpty()) emptyList() else mediaRefs.split(TAG_SEP),
        sourceEventId = sourceEventId,
    )

    private companion object {
        const val TAG_SEP = '\u0000'
    }
}
