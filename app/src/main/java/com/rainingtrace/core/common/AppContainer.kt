package com.rainingtrace.core.common

import android.content.Context
import com.rainingtrace.core.lifecycle.AppForegroundState
import com.rainingtrace.core.time.SystemWorldClock
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.data.local.RainingTraceDatabase
import com.rainingtrace.data.content.ContentStore
import com.rainingtrace.data.content.JsonPlaceWriter
import com.rainingtrace.data.repository.ContentNpcProactiveRuleCatalog
import com.rainingtrace.data.repository.ContentNpcRepository
import com.rainingtrace.data.repository.ContentPlaceRepository
import com.rainingtrace.data.repository.ContentResourceCatalog
import com.rainingtrace.data.repository.ContentYieldRuleCatalog
import com.rainingtrace.data.repository.RoomExplorationRepository
import com.rainingtrace.data.repository.RoomFootprintRepository
import com.rainingtrace.data.repository.RoomInventoryRepository
import com.rainingtrace.data.repository.RoomMemoryRepository
import com.rainingtrace.data.repository.RoomNpcCommitmentRepository
import com.rainingtrace.data.repository.RoomNpcMessageRepository
import com.rainingtrace.data.repository.RoomNpcStateRepository
import com.rainingtrace.data.repository.RoomTrackRepository
import com.rainingtrace.data.settings.DataStoreSettingsRepository
import com.rainingtrace.domain.exploration.ExplorationRepository
import com.rainingtrace.domain.exploration.PerformPlaceActionUseCase
import com.rainingtrace.domain.footprint.FootprintRepository
import com.rainingtrace.domain.inventory.AddItemToInventoryUseCase
import com.rainingtrace.domain.inventory.InMemoryResourceCatalog
import com.rainingtrace.domain.inventory.InventoryRepository
import com.rainingtrace.domain.inventory.ResourceCatalog
import com.rainingtrace.domain.map.GridManager
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.PlaceRepository
import com.rainingtrace.domain.map.PlaceWriter
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.ar.ArController
import com.rainingtrace.domain.memory.AudioNoteController
import com.rainingtrace.domain.memory.CreateMemoryUseCase
import com.rainingtrace.domain.memory.MemoryFocusRequest
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.npc.NarrativeService
import com.rainingtrace.domain.npc.NpcCommitmentRepository
import com.rainingtrace.domain.npc.NpcCommitmentUseCase
import com.rainingtrace.domain.npc.NpcMessageParser
import com.rainingtrace.domain.npc.NpcMessageRepository
import com.rainingtrace.domain.npc.NpcMessageWriter
import com.rainingtrace.domain.npc.NpcPresenceUseCase
import com.rainingtrace.domain.npc.NpcProactiveMessageUseCase
import com.rainingtrace.domain.npc.NpcProactiveRuleCatalog
import com.rainingtrace.domain.npc.NpcRepository
import com.rainingtrace.domain.npc.NpcStateRepository
import com.rainingtrace.domain.npc.RecordNpcEncounterUseCase
import com.rainingtrace.domain.npc.RuleBasedNpcMessageParser
import com.rainingtrace.domain.npc.SendNpcMessageUseCase
import com.rainingtrace.domain.npc.TemplateNarrativeService
import com.rainingtrace.domain.settings.AppSettingsRepository
import com.rainingtrace.domain.settings.LocationMode
import com.rainingtrace.domain.settings.NpcClockOffset
import com.rainingtrace.domain.track.ChangeGridLevelUseCase
import com.rainingtrace.domain.track.RebuildFogFromTrackUseCase
import com.rainingtrace.domain.track.RecordTrackPointUseCase
import com.rainingtrace.domain.track.RevealFogFromPointUseCase
import com.rainingtrace.domain.track.TrackDayFocusRequest
import com.rainingtrace.domain.track.TrackRepository
import com.rainingtrace.domain.track.TrackingController
import com.rainingtrace.domain.world.DerivedSeasonSource
import com.rainingtrace.domain.world.FakeWeatherProvider
import com.rainingtrace.domain.world.InMemoryResourceYieldRuleCatalog
import com.rainingtrace.domain.world.ManualTimeOfDaySource
import com.rainingtrace.domain.world.MutableWeatherProvider
import com.rainingtrace.domain.world.ResourceYieldRuleCatalog
import com.rainingtrace.domain.world.RandomSource
import com.rainingtrace.domain.world.SeasonSource
import com.rainingtrace.domain.world.SeededRandomSource
import com.rainingtrace.domain.world.SystemWorldStateProvider
import com.rainingtrace.domain.world.TimeOfDaySource
import com.rainingtrace.domain.world.WeatherProvider
import com.rainingtrace.domain.world.WorldStateProvider
import com.rainingtrace.platform.ar.ArCoreController
import com.rainingtrace.platform.audio.AndroidAudioNoteController
import com.rainingtrace.platform.camera.CameraXController
import com.rainingtrace.platform.location.AndroidTrackingController
import com.rainingtrace.platform.location.FakeLocationProvider
import com.rainingtrace.platform.map.MapLibreAdapter
import com.rainingtrace.platform.location.AndroidLocationProvider
import com.rainingtrace.platform.location.SwitchableLocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * RT-BOOT-003: 手写 DI（AppContainer）。
 *
 * 决策：MVP 使用 AppContainer + 构造注入，不引入 Hilt
 * （人工确认 2026-09-13；符合"40 行优于 100 行"原则）。
 *
 * 依赖方向保持 feature → domain ← platform/data。
 */
class AppContainer(
    context: Context,
    private val applicationScope: CoroutineScope,
) {

    private val appContext: Context = context.applicationContext

    /**
     * 内容（地点/NPC/资源/规则/台词）的唯一入口：内置 assets 默认 + 私有目录覆盖。
     *
     * 构造期就同步加载完（几毫秒的小文件读，口径同下面的 `gridManager`）：
     * 内容被地图、设置、聊天、背包同时需要，懒加载失手会表现为"莫名其妙空掉的地图"。
     */
    val contentStore: ContentStore = ContentStore(appContext)

    init {
        contentStore.load()
    }

    // 世界原点：北湖（参考坐标，真机试玩后校准）
    private val worldOrigin = WorldCoordinate(39.7326, 116.1712)

    private val database: RainingTraceDatabase by lazy {
        RainingTraceDatabase.create(context)
    }

    val clock: WorldClock by lazy { SystemWorldClock() }

    val settingsRepository: AppSettingsRepository by lazy {
        DataStoreSettingsRepository(appContext)
    }

    /** 格子档位与当前网格；启动时从偏好恢复。 */
    val gridManager: GridManager = GridManager(
        initialLevel = runBlocking { settingsRepository.currentGridLevel() },
        origin = worldOrigin,
    )

    /** 启动时恢复的定位模式（Fake 默认，点击地图调试）。 */
    val initialLocationMode: com.rainingtrace.domain.settings.LocationMode =
        runBlocking { settingsRepository.locationMode.first() }

    /** 供仍需直接取网格的只读便捷属性（实时跟随档位）。 */
    val grid: HexGrid get() = gridManager.grid

    private val fakeLocationProvider: FakeLocationProvider by lazy {
        FakeLocationProvider(clock = clock, initial = worldOrigin)
    }

    private val androidLocationProvider: AndroidLocationProvider by lazy {
        AndroidLocationProvider(appContext, clock)
    }

    val locationProvider: SwitchableLocationProvider by lazy {
        SwitchableLocationProvider(
            fake = fakeLocationProvider,
            android = androidLocationProvider,
            initialMode = initialLocationMode,
            parentScope = applicationScope,
            modeFlow = settingsRepository.locationMode,
        )
    }

    /** 供 feature 判断当前定位模式（权限流、Fake 提示等）。 */
    val locationModeFlow get() = settingsRepository.locationMode

    /** 应用前后台状态：地图处理流与后台记录服务的唯一省电闸门。 */
    val foregroundState = AppForegroundState()

    val trackingController: TrackingController by lazy {
        AndroidTrackingController(appContext)
    }

    /** 定位权限是否已授予（监督者与记录服务共用同一判断）。 */
    fun hasLocationPermission(): Boolean = androidLocationProvider.hasPermission()

    /**
     * 足迹记录服务的唯一决策点：开关开 + GPS 模式 + 有定位权限 = 该记录。
     *
     * 停随时可以停；启只能发生在应用可见时——Android 14 起禁止从后台启动
     * location 类型前台服务。所以这里也把前台状态作为一个输入。
     */
    private fun syncTrackingService(enabled: Boolean, mode: LocationMode, foreground: Boolean) {
        val shouldRun = enabled && mode == LocationMode.GPS && hasLocationPermission()
        when {
            shouldRun && foreground -> trackingController.start()
            !shouldRun -> trackingController.stop()
            // shouldRun 但当前不可见：不做任何事。服务本该已在可见时拉起；
            // 若被系统杀掉，则等下次回到前台再启动（这是唯一合法的启动时机）。
        }
    }

    init {
        applicationScope.launch {
            combine(
                settingsRepository.tracking,
                settingsRepository.locationMode,
                foregroundState.isForeground,
            ) { tracking, mode, foreground -> Triple(tracking.enabled, mode, foreground) }
                .distinctUntilChanged()
                .collect { (enabled, mode, foreground) ->
                    syncTrackingService(enabled, mode, foreground)
                }
        }
        // NPC 调试时间偏移：DataStore → 内存镜像（见 npcClockOffset 的说明）。
        applicationScope.launch {
            settingsRepository.npcClockOffset.collect { npcClockOffset.value = it }
        }

        // NPC 主动消息 + 约定兑现：前台可见时每分钟检查一次。
        // 熄屏不生成（HANDOFF_4 §6「后台服务只写库」+ Android 14 的后台限制），
        // 回前台再补算一次即可——两个引擎都是幂等的，漏不掉也不重发。
        // 玩家坐标用 locationProvider.latest 取快照，不额外订阅定位流。
        applicationScope.launch {
            while (true) {
                delay(NPC_PROACTIVE_TICK_MS)
                if (!foregroundState.isForeground.value) continue
                runCatching { tickNpcEngines() }
            }
        }
        applicationScope.launch {
            // StateFlow 本来就只发变化，不需要（也不该）再 distinctUntilChanged。
            foregroundState.isForeground
                .collect { foreground -> if (foreground) runCatching { tickNpcEngines() } }
        }
    }

    /**
     * 两个 NPC 引擎共用一次 tick：先兑现约定，再考虑主动消息。
     *
     * 顺序有讲究——"他到了"是玩家刚做的事的直接反馈，优先级高于主动消息；
     * 而且两者都自带限流，一轮最多各出一条。
     */
    private suspend fun tickNpcEngines() {
        val coordinate = locationProvider.latest?.coordinate
        npcCommitments.tick(coordinate)
        npcProactiveMessages()
    }

    /** 定位权限状态变化后重新尝试拉起 GPS 采集（并重新评估足迹记录服务）。 */
    fun refreshLocation() {
        locationProvider.refresh()
        applicationScope.launch {
            syncTrackingService(
                enabled = settingsRepository.currentTracking().enabled,
                mode = settingsRepository.currentLocationMode(),
                foreground = foregroundState.isForeground.value,
            )
        }
    }

    /** 调试用：仅 Fake 模式点击地图有效。 */
    val debugMapTap: (WorldCoordinate) -> Unit = { coordinate ->
        locationProvider.emitDebugMove(coordinate)
    }

    val mapRenderer: MapRendererAdapter by lazy { MapLibreAdapter() }

    val placeRepository: PlaceRepository by lazy {
        ContentPlaceRepository { contentStore.index.value }
    }

    /** 现场采点：把当前位置记成一个地点，写进 `files/content/places.json`。 */
    val placeWriter: PlaceWriter by lazy {
        JsonPlaceWriter(context = appContext, store = contentStore, clock = clock)
    }

    /** NPC 档案：和地点一样是只读配置（手工编写，将来由内容资产/服务端下发）。 */
    val npcRepository: NpcRepository by lazy {
        ContentNpcRepository { contentStore.index.value }
    }

    /**
     * NPC 调试时间偏移：DataStore 是真相，这里放一份内存镜像，
     * 让 [NpcPresenceUseCase] 每次求值不必读磁盘（它会被 10s ticker 频繁调用）。
     */
    private val npcClockOffset = MutableStateFlow(NpcClockOffset.DEFAULT)

    /** NPC 此刻在哪：位置是时间的纯函数，不落库。 */
    val npcPresence: NpcPresenceUseCase by lazy {
        NpcPresenceUseCase(
            npcRepository,
            placeRepository,
            npcClockOffset,
            commitments = npcCommitmentRepository,
        )
    }

    /** 走到 NPC 跟前 → 记一次"第一次遇见"（真相在 footprint 事件里）。 */
    val recordNpcEncounter: RecordNpcEncounterUseCase by lazy {
        RecordNpcEncounterUseCase(clock, footprintRepository, worldStateProvider)
    }

    /** NPC 消息（Room v5）：会话列表 / 未读 / 线程。 */
    val npcMessageRepository: NpcMessageRepository by lazy {
        RoomNpcMessageRepository(database.npcMessageDao())
    }

    /** NPC 关系与情绪（Room v5）：好感、情绪、上次互动。 */
    val npcStateRepository: NpcStateRepository by lazy {
        RoomNpcStateRepository(database.npcStateDao())
    }

    /** 约定（Room v6）：他答应的事有落点，所以片 3 可以真的兑现。 */
    val npcCommitmentRepository: NpcCommitmentRepository by lazy {
        RoomNpcCommitmentRepository(database.npcCommitmentDao())
    }

    /** 片 2 与片 3 共用的"写消息 + 留足迹"。 */
    private val npcMessageWriter: NpcMessageWriter by lazy {
        NpcMessageWriter(npcMessageRepository, footprintRepository)
    }

    /**
     * 台词变体选择的随机源。用启动时刻做 seed：同一次运行里可复现，
     * 跨次启动有变化（固定 seed 会让每次开 app 说同样的话）。
     */
    private val npcRandom: RandomSource by lazy { SeededRandomSource(clock.now().toEpochMilli()) }

    /** 规则版意图解析。将来接 AI 只换这一个实现（Prompt 08）。 */
    private val npcMessageParser: NpcMessageParser by lazy {
        RuleBasedNpcMessageParser(rules = { contentStore.index.value.npcKeywords })
    }

    /** 地点口语别名来自内容（NPC 消息解析用），改完 JSON 重读即生效。 */
    private fun placeAliases(): Map<String, String> = contentStore.index.value.placeAliases

    /** 模板叙事。将来接 LLM 只换这一个实现，玩法与状态计算都不用动。 */
    private val npcNarrative: NarrativeService by lazy {
        TemplateNarrativeService(
            random = npcRandom,
            lines = { contentStore.index.value.npcLines },
        )
    }

    /** 玩家发消息 → NPC 回复（含好感/情绪结算与足迹留档）。 */
    val sendNpcMessage: SendNpcMessageUseCase by lazy {
        SendNpcMessageUseCase(
            clock = clock,
            npcRepository = npcRepository,
            placeRepository = placeRepository,
            npcPresence = npcPresence,
            parser = npcMessageParser,
            narrative = npcNarrative,
            messageRepository = npcMessageRepository,
            stateRepository = npcStateRepository,
            footprintRepository = footprintRepository,
            worldState = worldStateProvider,
            random = npcRandom,
            placeAliases = ::placeAliases,
            commitmentRepository = npcCommitmentRepository,
        )
    }

    /** 主动消息规则表：内容放 data，和 NPC/地点的 id 一起维护。 */
    private val npcProactiveRules: NpcProactiveRuleCatalog by lazy {
        ContentNpcProactiveRuleCatalog { contentStore.index.value }
    }

    /** NPC 主动发消息：一次检查最多一条（冷却 + 每日上限 + 免打扰）。 */
    val npcProactiveMessages: NpcProactiveMessageUseCase by lazy {
        NpcProactiveMessageUseCase(
            clock = clock,
            npcRepository = npcRepository,
            placeRepository = placeRepository,
            npcPresence = npcPresence,
            rules = npcProactiveRules,
            stateRepository = npcStateRepository,
            messageWriter = npcMessageWriter,
            footprintRepository = footprintRepository,
            worldState = worldStateProvider,
            settings = settingsRepository,
        )
    }

    /** 约定兑现：他到了就发一句、你没来就记一次（片 3）。 */
    val npcCommitments: NpcCommitmentUseCase by lazy {
        NpcCommitmentUseCase(
            clock = clock,
            commitments = npcCommitmentRepository,
            placeRepository = placeRepository,
            messageWriter = npcMessageWriter,
            stateRepository = npcStateRepository,
            footprintRepository = footprintRepository,
            worldState = worldStateProvider,
        )
    }

    val explorationRepository: ExplorationRepository by lazy {
        RoomExplorationRepository(database.explorationDao(), gridManager)
    }

    val trackRepository: TrackRepository by lazy {
        RoomTrackRepository(database.trackPointDao())
    }

    val footprintRepository: FootprintRepository by lazy {
        RoomFootprintRepository(database.footprintDao())
    }

    val inventoryRepository: InventoryRepository by lazy {
        RoomInventoryRepository(database.inventoryDao())
    }

    /** 资源/图鉴目录：定义库存在这里，库存量只在 inventoryRepository。 */
    val resourceCatalog: ResourceCatalog by lazy {
        ContentResourceCatalog { contentStore.index.value }
    }

    /** 世界天气状态：MVP 用 Fake（固定晴）；P1 换真实 API 适配器，条件与产出不用改。 */
    val weatherProvider: WeatherProvider by lazy { FakeWeatherProvider() }

    /** 供设置页的调试区手动改天气（真实 API 版不实现 MutableWeatherProvider，界面自动隐藏）。 */
    val mutableWeatherProvider: MutableWeatherProvider? get() = weatherProvider as? MutableWeatherProvider

    /**
     * 季节来源：**按节气推导**（立春/立夏/立秋/立冬），设置页调试区可手动覆盖。
     * 产出条件（SeasonIn）不用改。
     */
    val seasonSource: SeasonSource by lazy { DerivedSeasonSource(clock, applicationScope) }

    /** 时段来源：默认按真实时间；调试区可固定成某时段，验证黎明/夜晚限定内容。 */
    val timeOfDaySource: TimeOfDaySource by lazy { ManualTimeOfDaySource() }

    /**
     * 世界状态（时间 + 天气 + 季节 + 时段）：资源产出、事件、NPC 出现的统一输入口。
     * 只在有人订阅时推进（WhileSubscribed），后台不空转。
     */
    val worldStateProvider: WorldStateProvider by lazy {
        SystemWorldStateProvider(
            clock = clock,
            weatherProvider = weatherProvider,
            seasonSource = seasonSource,
            timeOfDaySource = timeOfDaySource,
            scope = applicationScope,
        )
    }

    /** 产出规则表：加内容只加规则，不改用例。 */
    val resourceYieldRules: ResourceYieldRuleCatalog by lazy {
        ContentYieldRuleCatalog { contentStore.index.value }
    }

    val addItem: AddItemToInventoryUseCase by lazy { AddItemToInventoryUseCase(clock) }

    val recordTrackPoint: RecordTrackPointUseCase by lazy {
        RecordTrackPointUseCase(trackRepository, clock)
    }

    val revealFog: RevealFogFromPointUseCase by lazy { RevealFogFromPointUseCase(gridManager) }

    val rebuildFog: RebuildFogFromTrackUseCase by lazy {
        RebuildFogFromTrackUseCase(revealFog)
    }

    val changeGridLevel: ChangeGridLevelUseCase by lazy {
        ChangeGridLevelUseCase(
            gridManager = gridManager,
            settings = settingsRepository,
            explorationRepository = explorationRepository,
            trackRepository = trackRepository,
            rebuild = rebuildFog,
        )
    }

    val performPlaceAction: PerformPlaceActionUseCase by lazy {
        PerformPlaceActionUseCase(
            clock = clock,
            inventoryRepository = inventoryRepository,
            addItem = addItem,
            footprintRepository = footprintRepository,
            resourceCatalog = resourceCatalog,
            worldState = worldStateProvider,
            rules = resourceYieldRules,
        )
    }

    val memoryRepository: MemoryRepository by lazy {
        RoomMemoryRepository(database.memoryDao())
    }

    /** 日记 →「在地图查看」：一次性聚焦请求，地图侧消费后清空。 */
    val memoryFocusRequest: MemoryFocusRequest = MemoryFocusRequest()

    /** 轨迹日历 →「在地图查看」：同上，聚焦某一天的轨迹。 */
    val trackDayFocusRequest: TrackDayFocusRequest = TrackDayFocusRequest()

    val cameraController: CameraXController by lazy {
        CameraXController(context = appContext, clock = clock)
    }

    /** 语音记录/回放：录音失败必须能降级为纯文字记忆。 */
    val audioNoteController: AudioNoteController by lazy {
        AndroidAudioNoteController(context = appContext, clock = clock)
    }

    val createMemory: CreateMemoryUseCase by lazy {
        CreateMemoryUseCase(
            gridManager = gridManager,
            clock = clock,
            memoryRepository = memoryRepository,
            explorationRepository = explorationRepository,
            footprintRepository = footprintRepository,
            worldState = worldStateProvider,
        )
    }

    val arController: ArController by lazy {
        ArCoreController(
            context = appContext,
            locationProvider = locationProvider,
            clock = clock,
        )
    }
}

/**
 * NPC 主动消息的检查间隔。一分钟一次足够——他不可能比这更频繁地"想起你"，
 * 而且每次检查只是几条本地查询。
 */
private const val NPC_PROACTIVE_TICK_MS = 60_000L
