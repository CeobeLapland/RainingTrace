package com.rainingtrace.domain.exploration

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.InventoryState
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.FakeWorldStateProvider
import com.rainingtrace.domain.world.InMemoryResourceYieldRuleCatalog
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ObservePlaceUseCaseTest {

    private val lake = Place(
        id = "place.lake",
        name = "北湖",
        type = PlaceType.LAKE,
        coordinate = WorldCoordinate(39.7326, 116.1712),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val library = Place(
        id = "place.library",
        name = "图书馆",
        type = PlaceType.LIBRARY,
        coordinate = WorldCoordinate(39.7326, 116.1712),
        actions = setOf(PlaceActionType.OBSERVE),
    )

    private val clock = FakeWorldClock(Instant.parse("2026-09-13T08:00:00Z"))

    private class FakeInventoryRepository : InventoryRepository {
        var state = InventoryState()
        override fun observeState(): Flow<InventoryState> = flowOf(state)
        override suspend fun loadState(): InventoryState = state
        override suspend fun saveState(state: InventoryState) {
            this.state = state
        }
    }

    private class FakeFootprintRepository : FootprintRepository {
        val events = mutableListOf<FootprintEvent>()
        override suspend fun append(event: FootprintEvent) {
            events.add(event)
        }
        override suspend fun eventsBetween(fromEpochMs: Long, toEpochMs: Long): List<FootprintEvent> =
            events.filter { it.timestampEpochMs in fromEpochMs..toEpochMs }
    }

    /** 默认晴天的世界状态；需要别的天气/时段时用 [worldOf] 覆盖。 */
    private fun worldOf(
        kind: WeatherKind = WeatherKind.CLEAR,
        instant: Instant = clock.now(),
    ) = FakeWorldStateProvider(
        deriveWorldState(instant = instant, weather = WeatherState(kind)),
    )

    private fun useCase(
        inventory: FakeInventoryRepository = FakeInventoryRepository(),
        footprint: FakeFootprintRepository = FakeFootprintRepository(),
        world: FakeWorldStateProvider = worldOf(),
        rules: InMemoryResourceYieldRuleCatalog = InMemoryResourceYieldRuleCatalog(
            InMemoryResourceYieldRuleCatalog.DEFAULT,
        ),
    ) = Triple(
        ObservePlaceUseCase(
            clock = clock,
            inventoryRepository = inventory,
            addItem = AddItemToInventoryUseCase(clock),
            footprintRepository = footprint,
            resourceCatalog = InMemoryResourceCatalog(InMemoryResourceCatalog.DEFAULT),
            worldState = world,
            rules = rules,
        ),
        inventory,
        footprint,
    )

    @Test
    fun `observe in range grants resource and logs footprint`() = runTest {
        val (observe, inventory, footprint) = useCase()
        val result = observe(lake.coordinate, lake)

        assertTrue(result is ObserveResult.Success)
        result as ObserveResult.Success
        assertEquals(1, result.newQuantity)
        assertEquals(1, inventory.state.quantityOf("res.observation_record"))
        assertEquals(1, footprint.events.size)
        assertEquals(FootprintEventType.PLACE_OBSERVED, footprint.events.first().eventType)
        assertEquals("place.lake", footprint.events.first().payload["placeId"])
        assertEquals("rule.observe.base", footprint.events.first().payload["ruleId"])
        // 位置是连续坐标，不再是格子
        assertEquals(lake.coordinate, footprint.events.first().coordinate)
        // 当时的世界状态一起留档
        assertEquals("CLEAR", footprint.events.first().payload["weather"])
        assertEquals("DAY", footprint.events.first().payload["timeOfDay"])
    }

    @Test
    fun `observe too far rejected`() = runTest {
        val (observe, inventory, footprint) = useCase()
        val far = WorldCoordinate(39.7426, 116.1712) // 约 1.1km 北
        val result = observe(far, lake)

        assertEquals(ObserveRejectReason.TOO_FAR, (result as ObserveResult.Rejected).reason)
        assertEquals(0, inventory.state.totalKinds())
        assertTrue(footprint.events.isEmpty())
    }

    @Test
    fun `second observe within cooldown rejected`() = runTest {
        val (observe, _, footprint) = useCase()
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        clock.advanceSeconds(60)
        val second = observe(lake.coordinate, lake)
        assertEquals(ObserveRejectReason.ON_COOLDOWN, (second as ObserveResult.Rejected).reason)
        assertEquals(1, footprint.events.size)
    }

    @Test
    fun `observe again after cooldown succeeds`() = runTest {
        val (observe, inventory, _) = useCase()
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        clock.advanceSeconds(11 * 60)
        val second = observe(lake.coordinate, lake)
        assertTrue(second is ObserveResult.Success)
        assertEquals(2, (second as ObserveResult.Success).newQuantity)
        assertEquals(2, inventory.state.quantityOf("res.observation_record"))
    }

    @Test
    fun `cooldown survives use case recreation as it is persisted in footprints`() = runTest {
        val inventory = FakeInventoryRepository()
        val footprint = FakeFootprintRepository()
        val world = worldOf()
        val observe = useCase(inventory = inventory, footprint = footprint, world = world).first
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        clock.advanceSeconds(30)
        // 模拟重启：新 UseCase，但足迹仓储里的历史还在
        val afterRestart = useCase(inventory = inventory, footprint = footprint, world = world).first
        val result = afterRestart(lake.coordinate, lake)

        assertEquals(ObserveRejectReason.ON_COOLDOWN, (result as ObserveResult.Rejected).reason)
    }

    @Test
    fun `observe at edge of range succeeds`() = runTest {
        val (observe, _, _) = useCase()
        // 约 110m 北，在 120m 范围内
        val edge = WorldCoordinate(39.73359, 116.1712)
        assertTrue(observe(edge, lake) is ObserveResult.Success)
    }

    // ---- 世界状态影响产出（条件规则） ----

    @Test
    fun `rainy lake yields the conditional resource instead of the base one`() = runTest {
        val (observe, inventory, footprint) = useCase(world = worldOf(WeatherKind.LIGHT_RAIN))
        val result = observe(lake.coordinate, lake)

        result as ObserveResult.Success
        assertEquals("res.lake_memory_fragment", result.resourceId)
        assertEquals(1, inventory.state.quantityOf("res.lake_memory_fragment"))
        assertEquals(0, inventory.state.quantityOf("res.observation_record"))
        assertEquals("rule.observe.rainy_lake", footprint.events.first().payload["ruleId"])
        assertEquals("LIGHT_RAIN", footprint.events.first().payload["weather"])
    }

    @Test
    fun `rainy library still yields only the base resource`() = runTest {
        val (observe, inventory, _) = useCase(world = worldOf(WeatherKind.HEAVY_RAIN))
        val result = observe(library.coordinate, library)

        result as ObserveResult.Success
        assertEquals("res.observation_record", result.resourceId)
        assertEquals(0, inventory.state.quantityOf("res.lake_memory_fragment"))
    }

    @Test
    fun `conditional rule cooling down does not block the base rule`() = runTest {
        val (observe, inventory, _) = useCase(world = worldOf(WeatherKind.LIGHT_RAIN))
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        // 11 分钟后保底规则已冷却好，但碎片规则还要等 30 分钟
        clock.advanceSeconds(11 * 60)
        val second = observe(lake.coordinate, lake)

        second as ObserveResult.Success
        assertEquals("res.observation_record", second.resourceId)
        assertEquals(2, inventory.state.totalKinds())
    }

    @Test
    fun `weathered lake stops yielding the conditional resource once weather clears`() = runTest {
        val world = worldOf(WeatherKind.LIGHT_RAIN)
        val (observe, inventory, _) = useCase(world = world)
        assertTrue(observe(lake.coordinate, lake) is ObserveResult.Success)

        // 天晴了：碎片规则不再命中，保底规则照旧（冷却已过）
        world.set(worldOf(WeatherKind.CLEAR).current())
        clock.advanceSeconds(31 * 60)
        val second = observe(lake.coordinate, lake)

        second as ObserveResult.Success
        assertEquals("res.observation_record", second.resourceId)
        assertEquals(1, inventory.state.quantityOf("res.observation_record"))
    }

    @Test
    fun `no matching rule reports nothing here rather than cooldown`() = runTest {
        val empty = InMemoryResourceYieldRuleCatalog(emptyList())
        val (observe, inventory, footprint) = useCase(rules = empty)
        val result = observe(lake.coordinate, lake)

        assertEquals(ObserveRejectReason.NOTHING_HERE, (result as ObserveResult.Rejected).reason)
        assertEquals(0, inventory.state.totalKinds())
        assertTrue(footprint.events.isEmpty())
    }
}
