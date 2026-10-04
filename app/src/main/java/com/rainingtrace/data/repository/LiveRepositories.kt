package com.rainingtrace.data.repository

import com.rainingtrace.domain.content.ContentIndex
import com.rainingtrace.domain.craft.Recipe
import com.rainingtrace.domain.craft.RecipeCatalog
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
import com.rainingtrace.domain.spawn.SpawnPlanner
import com.rainingtrace.domain.spawn.SpawnRule
import com.rainingtrace.domain.spawn.SpawnRuleCatalog
import com.rainingtrace.domain.world.ResourceYieldRule
import com.rainingtrace.domain.world.ResourceYieldRuleCatalog
import com.rainingtrace.domain.world.WorldStateProvider

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

    override fun all(): List<ResourceYieldRule> = index().yieldRules
}

/** 加工配方表。 */
class ContentRecipeCatalog(
    private val index: () -> ContentIndex,
) : RecipeCatalog {

    override fun all(): List<Recipe> = index().recipes

    override fun byId(id: String): Recipe? = index().recipeById[id]
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

/** 资源点刷新规则表。 */
class ContentSpawnRuleCatalog(
    private val index: () -> ContentIndex,
) : SpawnRuleCatalog {

    override fun rules(): List<SpawnRule> = index().spawnRules
}

/**
 * 资源点刷新的合成层：静态地点 + 当天确定性算出来的 spawn 点。
 *
 * - `all()` **只返静态点**：它是给 NPC 作息按 id 解析坐标用的，塞进会过期、
 *   会随时间变的点会污染语义。
 * - `nearby()` / `placeById()` 才带 spawn 点（`placeById` 对刚过期的点有宽限，
 *   否则"图标还在、点下去没反应"）。
 *
 * spawn 点不落库，每次现算；几十条规则的量级是微秒级，不值得加缓存。
 */
class SpawnAwarePlaceRepository(
    private val authored: PlaceRepository,
    private val spawnRules: SpawnRuleCatalog,
    private val worldState: WorldStateProvider,
) : PlaceRepository {

    override suspend fun placeById(id: String): Place? =
        authored.placeById(id) ?: planned().firstOrNull { it.id == id }

    override suspend fun nearby(coordinate: WorldCoordinate, radiusMeters: Double): List<Place> =
        (authored.nearby(coordinate, radiusMeters) + planned())
            .map { it to coordinate.distanceMetersTo(it.coordinate) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .sortedBy { (_, distance) -> distance }
            .map { (place, _) -> place }

    override suspend fun all(): List<Place> = authored.all()

    private fun planned(): List<Place> =
        SpawnPlanner.plan(spawnRules.rules(), worldState.current())
}