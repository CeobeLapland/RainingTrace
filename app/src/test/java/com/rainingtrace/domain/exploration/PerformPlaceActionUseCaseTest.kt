package com.rainingtrace.domain.exploration

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.footprint.FootprintEvent
import com.rainingtrace.domain.footprint.FootprintEventType
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.data.content.ShippedContent
import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.InventoryState
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.world.FakeWorldStateProvider
import com.rainingtrace.domain.world.InMemoryResourceYieldRuleCatalog
import com.rainingtrace.domain.world.Season
import com.rainingtrace.domain.world.WeatherKind
import com.rainingtrace.domain.world.WeatherState
import com.rainingtrace.domain.world.deriveWorldState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformPlaceActionUseCaseTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    /** 湖：观察 + 采集（雨天/夜晚/雪天等条件内容都挂在这）。 */
    private val lake = Place(
        id = "place.lake",
        name = "北湖",
        type = PlaceType.LAKE,
        coordinate = origin,
        actions = setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT),
    )

    /** 花园：季节与时段限定的采集内容挂在这。 */
    private val garden = Place(
        id = "place.garden",
        name = "湖心花园",
        type = PlaceType.GARDEN,
        coordinate = origin,
        actions = setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT),
    )

    private val library = Place(
        id = "place.library",
        name = "图书馆",
        type = PlaceType.LIBRARY,
        coordinate = origin,
        actions = setOf(PlaceActionType.OBSERVE, PlaceActionType.COLLECT),
    )

    /** 只支持观察：验证"动作不可用"。 */
    private val observeOnly = Place(
        id = "place.observe_only",
        name = "只有观察的地点",
        type = PlaceType.OTHER,
        coordinate = origin,
        actions = setOf(PlaceActionType.OBSERVE),
    )

    /** 自然资源点：只给采集动作。 */
    private val mushroomPatch = Place(
        id = "place.mushroom_patch",
        name = "菌丛",
        type = PlaceType.MUSHROOM_PATCH,
        coordinate = origin,
        actions = setOf(PlaceActionType.COLLECT),
    )

    private val orchard = Place(
        id = "place.orchard",
        name = "果林",
        type = PlaceType.ORCHARD,
        coordinate = origin,
        actions = setOf(PlaceActionType.COLLECT),
    )

    // 16:00 (+08:00) → DAY
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
        override suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent> =
            events.filter { it.eventType == type }
    }

    /** 一次动作所需的一切：用例 + 两个假仓储，避免测试里重复拼装。 */
    private class Fixture(
        val perform: PerformPlaceActionUseCase,
        val inventory: FakeInventoryRepository,
        val footprint: FakeFootprintRepository,
    ) {
        suspend fun observe(place: Place, coordinate: WorldCoordinate = place.coordinate) =
            perform(coordinate, place, PlaceActionType.OBSERVE)

        suspend fun collect(place: Place, coordinate: WorldCoordinate = place.coordinate) =
            perform(coordinate, place, PlaceActionType.COLLECT)
    }

    /** 按本地小时构造世界状态：默认白天、晴、季节未定。 */
    private fun worldOf(
        kind: WeatherKind = WeatherKind.CLEAR,
        season: Season? = null,
        hour: Int = 16,
    ) = FakeWorldStateProvider(
        deriveWorldState(
            instant = LocalDate.of(2026, 9, 13).atTime(hour, 0).toInstant(ZoneOffset.ofHours(8)),
            weather = WeatherState(kind),
            season = season,
        ),
    )

    private fun fixture(
        inventory: FakeInventoryRepository = FakeInventoryRepository(),
        footprint: FakeFootprintRepository = FakeFootprintRepository(),
        world: FakeWorldStateProvider = worldOf(),
        rules: InMemoryResourceYieldRuleCatalog = InMemoryResourceYieldRuleCatalog(
            ShippedContent.yieldRules,
        ),
    ) = Fixture(
        perform = PerformPlaceActionUseCase(
            clock = clock,
            inventoryRepository = inventory,
            addItem = AddItemToInventoryUseCase(clock),
            footprintRepository = footprint,
            resourceCatalog = InMemoryResourceCatalog(ShippedContent.resources),
            worldState = world,
            rules = rules,
        ),
        inventory = inventory,
        footprint = footprint,
    )

    // ---- 基础行为 ----

    @Test
    fun `observe in range grants resource and logs footprint with world state`() = runTest {
        val f = fixture()
        val result = f.observe(lake)

        result as PlaceActionResult.Success
        assertEquals(PlaceActionType.OBSERVE, result.action)
        assertEquals("res.observation_record", result.resourceId)
        assertEquals(1, f.inventory.state.quantityOf("res.observation_record"))
        assertEquals(1, f.footprint.events.size)
        assertEquals(FootprintEventType.PLACE_OBSERVED, f.footprint.events.first().eventType)
        assertEquals("place.lake", f.footprint.events.first().payload["placeId"])
        assertEquals("OBSERVE", f.footprint.events.first().payload["action"])
        assertEquals("rule.observe.base", f.footprint.events.first().payload["ruleId"])
        // 位置是连续坐标，不是格子
        assertEquals(lake.coordinate, f.footprint.events.first().coordinate)
        // 当时的世界状态一起留档
        assertEquals("CLEAR", f.footprint.events.first().payload["weather"])
        assertEquals("DAY", f.footprint.events.first().payload["timeOfDay"])
    }

    @Test
    fun `observe too far rejected`() = runTest {
        val f = fixture()
        val far = WorldCoordinate(39.7426, 116.1712) // 约 1.1km 北

        assertEquals(
            PlaceActionRejectReason.TOO_FAR,
            (f.observe(lake, far) as PlaceActionResult.Rejected).reason,
        )
        assertEquals(0, f.inventory.state.totalKinds())
        assertTrue(f.footprint.events.isEmpty())
    }

    @Test
    fun `second observe within cooldown rejected`() = runTest {
        val f = fixture()
        assertTrue(f.observe(lake) is PlaceActionResult.Success)

        clock.advanceSeconds(60)
        assertEquals(
            PlaceActionRejectReason.ON_COOLDOWN,
            (f.observe(lake) as PlaceActionResult.Rejected).reason,
        )
        assertEquals(1, f.footprint.events.size)
    }

    @Test
    fun `observe again after cooldown succeeds`() = runTest {
        val f = fixture()
        assertTrue(f.observe(lake) is PlaceActionResult.Success)

        clock.advanceSeconds(11 * 60)
        val second = f.observe(lake)
        assertTrue(second is PlaceActionResult.Success)
        assertEquals(2, (second as PlaceActionResult.Success).newQuantity)
        assertEquals(2, f.inventory.state.quantityOf("res.observation_record"))
    }

    @Test
    fun `cooldown survives use case recreation as it is persisted in footprints`() = runTest {
        val inventory = FakeInventoryRepository()
        val footprint = FakeFootprintRepository()
        val world = worldOf()
        assertTrue(fixture(inventory, footprint, world).observe(lake) is PlaceActionResult.Success)

        clock.advanceSeconds(30)
        // 模拟重启：新 UseCase，但足迹仓储里的历史还在
        val afterRestart = fixture(inventory, footprint, world)
        assertEquals(
            PlaceActionRejectReason.ON_COOLDOWN,
            (afterRestart.observe(lake) as PlaceActionResult.Rejected).reason,
        )
    }

    @Test
    fun `observe at edge of its range succeeds`() = runTest {
        val f = fixture()
        // 约 110m 北：观察范围 120m 内，采集范围 60m 外
        assertTrue(f.observe(lake, WorldCoordinate(39.73359, 116.1712)) is PlaceActionResult.Success)
    }

    @Test
    fun `action not supported by the place is rejected`() = runTest {
        val f = fixture()

        val result = f.perform(origin, observeOnly, PlaceActionType.COLLECT)
        assertEquals(
            PlaceActionRejectReason.ACTION_NOT_AVAILABLE,
            (result as PlaceActionResult.Rejected).reason,
        )
        assertEquals(0, f.inventory.state.totalKinds())
        assertTrue(f.footprint.events.isEmpty())
    }

    @Test
    fun `no matching rule reports nothing here rather than cooldown`() = runTest {
        val f = fixture(rules = InMemoryResourceYieldRuleCatalog(emptyList()))

        assertEquals(
            PlaceActionRejectReason.NOTHING_HERE,
            (f.observe(lake) as PlaceActionResult.Rejected).reason,
        )
        assertEquals(0, f.inventory.state.totalKinds())
        assertTrue(f.footprint.events.isEmpty())
    }

    // ---- 观察：世界状态影响产出 ----

    @Test
    fun `rainy lake yields the conditional resource instead of the base one`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))

        f.observe(lake) as PlaceActionResult.Success
        assertEquals(1, f.inventory.state.quantityOf("res.lake_memory_fragment"))
        assertEquals(0, f.inventory.state.quantityOf("res.observation_record"))
        assertEquals("rule.observe.rainy_lake", f.footprint.events.first().payload["ruleId"])
    }

    @Test
    fun `rainy night lake yields the rarest anomaly first`() = runTest {
        val f = fixture(world = worldOf(kind = WeatherKind.HEAVY_RAIN, hour = 22))

        val result = f.observe(lake)
        result as PlaceActionResult.Success
        // 条件最多的规则优先：雨 + 夜 > 雨
        assertEquals("res.mirror_moon_fish_shadow", result.resourceId)
        assertEquals(1, f.inventory.state.quantityOf("res.mirror_moon_fish_shadow"))
        assertEquals(0, f.inventory.state.quantityOf("res.lake_memory_fragment"))
    }

    @Test
    fun `rainy library still yields only the base resource`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.HEAVY_RAIN))

        val result = f.observe(library)
        result as PlaceActionResult.Success
        assertEquals("res.observation_record", result.resourceId)
        assertEquals(0, f.inventory.state.quantityOf("res.lake_memory_fragment"))
    }

    @Test
    fun `conditional rule cooling down does not block the base rule`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))
        assertTrue(f.observe(lake) is PlaceActionResult.Success)

        // 11 分钟后保底规则已冷却好，但碎片规则还要等 30 分钟
        clock.advanceSeconds(11 * 60)
        val second = f.observe(lake)

        second as PlaceActionResult.Success
        assertEquals("res.observation_record", second.resourceId)
        assertEquals(2, f.inventory.state.totalKinds())
    }

    @Test
    fun `conditional resource stops appearing once the weather clears`() = runTest {
        val world = worldOf(WeatherKind.LIGHT_RAIN)
        val f = fixture(world = world)
        assertTrue(f.observe(lake) is PlaceActionResult.Success)

        world.set(worldOf(WeatherKind.CLEAR).current())
        clock.advanceSeconds(31 * 60)
        val second = f.observe(lake)

        second as PlaceActionResult.Success
        assertEquals("res.observation_record", second.resourceId)
        assertEquals(1, f.inventory.state.quantityOf("res.observation_record"))
    }

    // ---- 采集：动作隔离、距离、季节与时段 ----

    @Test
    fun `collect uses collect rules rather than observe rules`() = runTest {
        val f = fixture()

        val result = f.collect(garden)
        result as PlaceActionResult.Success
        // 花园白天的保底采集是苔痕，不是观察记录
        assertEquals("res.rain_moss", result.resourceId)
        assertEquals(0, f.inventory.state.quantityOf("res.observation_record"))
        assertEquals("COLLECT", f.footprint.events.last().payload["action"])
    }

    @Test
    fun `collect requires standing closer than observe`() = runTest {
        val f = fixture()
        // 约 100m 北：观察够（120m），采集不够（60m）
        val spot = WorldCoordinate(39.73350, 116.1712)

        assertTrue(f.observe(garden, spot) is PlaceActionResult.Success)
        assertEquals(
            PlaceActionRejectReason.TOO_FAR,
            (f.collect(garden, spot) as PlaceActionResult.Rejected).reason,
        )
    }

    @Test
    fun `rain multiplies the moss yield`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))

        val result = f.collect(garden)
        result as PlaceActionResult.Success
        assertEquals("res.rain_moss", result.resourceId)
        assertEquals(2, result.amount)
        assertEquals(2, f.inventory.state.quantityOf("res.rain_moss"))
    }

    @Test
    fun `season gated collection only fires in the matching season`() = runTest {
        val autumn = fixture(world = worldOf(season = Season.AUTUMN))
        val autumnResult = autumn.collect(garden)
        autumnResult as PlaceActionResult.Success
        assertEquals("res.pine_cone", autumnResult.resourceId)

        // 季节未确定（默认）：季节条件不满足，只剩保底苔痕
        val undetermined = fixture(world = worldOf(season = null))
        val undeterminedResult = undetermined.collect(garden)
        undeterminedResult as PlaceActionResult.Success
        assertEquals("res.rain_moss", undeterminedResult.resourceId)
    }

    @Test
    fun `dawn gated collection only fires at dawn`() = runTest {
        val dawn = fixture(world = worldOf(hour = 5))
        val dawnResult = dawn.collect(garden)
        dawnResult as PlaceActionResult.Success
        assertEquals("res.dew_grass", dawnResult.resourceId)

        val noon = fixture(world = worldOf(hour = 12))
        val noonResult = noon.collect(garden)
        noonResult as PlaceActionResult.Success
        assertEquals("res.rain_moss", noonResult.resourceId)
    }

    @Test
    fun `double gated anomaly wins over single condition content`() = runTest {
        val f = fixture(world = worldOf(kind = WeatherKind.SNOW, season = Season.WINTER))

        val result = f.collect(garden)
        result as PlaceActionResult.Success
        assertEquals("res.frost_pattern", result.resourceId)
    }

    @Test
    fun `night collection at the lake yields the night sound`() = runTest {
        val f = fixture(world = worldOf(hour = 22))

        val result = f.collect(lake)
        result as PlaceActionResult.Success
        assertEquals("res.night_water_sound", result.resourceId)
    }

    // ---- 自然资源点（手工配置的采集点） ----

    @Test
    fun `rain doubles the mushroom yield at a mushroom patch`() = runTest {
        val dry = fixture(world = worldOf(WeatherKind.CLEAR))
        val dryResult = dry.collect(mushroomPatch)
        dryResult as PlaceActionResult.Success
        assertEquals("res.wild_mushroom", dryResult.resourceId)
        assertEquals(1, dryResult.amount)

        val wet = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))
        val wetResult = wet.collect(mushroomPatch)
        wetResult as PlaceActionResult.Success
        assertEquals(2, wetResult.amount)
    }

    @Test
    fun `autumn doubles the apple yield at an orchard`() = runTest {
        val summer = fixture(world = worldOf(season = Season.SUMMER))
        val summerResult = summer.collect(orchard)
        summerResult as PlaceActionResult.Success
        assertEquals("res.green_apple", summerResult.resourceId)
        assertEquals(1, summerResult.amount)

        val autumn = fixture(world = worldOf(season = Season.AUTUMN))
        val autumnResult = autumn.collect(orchard)
        autumnResult as PlaceActionResult.Success
        assertEquals(2, autumnResult.amount)
    }

    @Test
    fun `resource nodes support collect only`() = runTest {
        val f = fixture()

        assertEquals(
            PlaceActionRejectReason.ACTION_NOT_AVAILABLE,
            (f.observe(mushroomPatch) as PlaceActionResult.Rejected).reason,
        )
    }

    // ---- 此刻产出预览（地点卡提示） ----

    @Test
    fun `preview reports the conditional resource in rain`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))

        val preview = f.perform.preview(lake, PlaceActionType.OBSERVE)
        preview as PlaceYieldPreview.Ready
        assertEquals("res.lake_memory_fragment", preview.resourceId)
    }

    @Test
    fun `preview is read only`() = runTest {
        val f = fixture()

        f.perform.preview(lake, PlaceActionType.OBSERVE)
        f.perform.preview(garden, PlaceActionType.COLLECT)

        assertEquals(0, f.inventory.state.totalKinds())
        assertTrue(f.footprint.events.isEmpty())
    }

    @Test
    fun `preview matches what the action actually yields`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))

        val preview = f.perform.preview(lake, PlaceActionType.OBSERVE) as PlaceYieldPreview.Ready
        val result = f.observe(lake) as PlaceActionResult.Success

        assertEquals(preview.resourceId, result.resourceId)
        assertEquals(preview.amount, result.amount)
    }

    @Test
    fun `preview falls back to the base rule once the conditional rule cools down`() = runTest {
        val f = fixture(world = worldOf(WeatherKind.LIGHT_RAIN))
        assertTrue(f.observe(lake) is PlaceActionResult.Success)

        // 碎片规则已进冷却，保底规则仍可预览到
        val preview = f.perform.preview(lake, PlaceActionType.OBSERVE)
        preview as PlaceYieldPreview.Ready
        assertEquals("res.observation_record", preview.resourceId)
    }

    @Test
    fun `preview reflects season gated content`() = runTest {
        val autumn = fixture(world = worldOf(season = Season.AUTUMN))
        val autumnPreview = autumn.perform.preview(garden, PlaceActionType.COLLECT)
        autumnPreview as PlaceYieldPreview.Ready
        assertEquals("res.pine_cone", autumnPreview.resourceId)

        // 季节未确定时季节条件不满足，只能预览到保底
        val undetermined = fixture(world = worldOf(season = null))
        val undeterminedPreview = undetermined.perform.preview(garden, PlaceActionType.COLLECT)
        undeterminedPreview as PlaceYieldPreview.Ready
        assertEquals("res.rain_moss", undeterminedPreview.resourceId)
    }

    @Test
    fun `preview reports unavailable when nothing matches`() = runTest {
        val f = fixture(rules = InMemoryResourceYieldRuleCatalog(emptyList()))

        val preview = f.perform.preview(lake, PlaceActionType.OBSERVE)
        preview as PlaceYieldPreview.Unavailable
        assertEquals(PlaceActionRejectReason.NOTHING_HERE, preview.reason)
    }

    @Test
    fun `preview reports unavailable for an action the place cannot do`() = runTest {
        val f = fixture()

        val preview = f.perform.preview(observeOnly, PlaceActionType.COLLECT)
        preview as PlaceYieldPreview.Unavailable
        assertEquals(PlaceActionRejectReason.ACTION_NOT_AVAILABLE, preview.reason)
    }
}