package com.rainingtrace.data.repository

import com.rainingtrace.domain.content.ContentIndex
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.inventory.ResourceDefinition
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.npc.NpcProfile
import com.rainingtrace.domain.npc.NpcProactiveRule
import com.rainingtrace.domain.npc.NpcProactiveRuleCatalog
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.world.ResourceYieldRule
import com.rainingtrace.domain.world.ResourceYieldRuleCatalog

/**
 * 读 [ContentIndex] 的仓储：内容外置之后运行时用的就是它们。
 *
 * 每次都从 `index()` 取当前快照，所以 `ContentStore.reload()` 之后地图、设置、
 * 聊天立刻看到新内容——不需要任何"通知仓储刷新"的机制。
 */
class ContentPlaceRepository(
    private val index: () -> ContentIndex,
) : PlaceRepository {

    override suspend fun placeById(id: String): Place? = index().placeById[id]

    override suspend fun all(): List<Place> = index().places

    override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
        index().places
            .map { it to coordinate.distanceMetersTo(it.coordinate) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .sortedBy { (_, distance) -> distance }
            .map { (place, _) -> place }
}

/** 资源/图鉴目录。 */
class ContentResourceCatalog(
    private val index: () -> ContentIndex,
) : ResourceCatalog {

    override fun definition(resourceId: String): ResourceDefinition? = index().resourceById[resourceId]

    override fun all(): List<ResourceDefinition> = index().resources
}

/** 产出规则表。 */
class ContentYieldRuleCatalog(
    private val index: () -> ContentIndex,
) : ResourceYieldRuleCatalog {

    override fun rulesFor(action: PlaceActionType): List<ResourceYieldRule> =
        index().yieldRulesByAction[action].orEmpty()
}

/** NPC 档案。 */
class ContentNpcRepository(
    private val index: () -> ContentIndex,
) : NpcRepository {

    override suspend fun all(): List<NpcProfile> = index().npcs

    override suspend fun byId(id: String): NpcProfile? = index().npcById[id]
}

/** NPC 主动消息规则表。 */
class ContentNpcProactiveRuleCatalog(
    private val index: () -> ContentIndex,
) : NpcProactiveRuleCatalog {

    override val rules: List<NpcProactiveRule> get() = index().npcProactiveRules
}