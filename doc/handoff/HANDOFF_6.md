# RainingTrace / 雨迹 — 开发进度交接（内容外置 + 真实天气 + 定位看门狗）

> 更新时间：2026-09-24（新对话开工前请先读本文件 + `doc/02_AGENTS.md` + `GDD_v3.md`）
> 上一版：`HANDOFF_5.md`（NPC 地图腿 + 消息腿 + 约定）
> **`HANDOFF_1`~`HANDOFF_5` 已封存为历史路径**，不再修改；当前状态以本文件为准。
> 本版覆盖：**内容全部外置为 JSON**（含开发者模式与现场采点）、**真实天气（Open-Meteo）**、**定位精度与看门狗**、**若干旧 bug 修复**

## 1. 当前一句话状态

**内容不再住在代码里**：地点/资源/产出规则/NPC/主动规则/台词/别名/关键词共 8 类全部在
`assets/content/*.json`（+ 私有目录覆盖层），设置页点「重新读取内容」即时生效；地图上还能
「记点」现场新建地点。**天气是真的**（Open-Meteo，免 key），同时保留手动覆盖调试。
**定位会说人话**：chip 显示精度，"卡住了"会明确提示去放行后台定位。
单测 **342 全绿**；Room 仍是 **v6**（本轮没动 schema）；AR 仍阶段封存（见 `HANDOFF_3 §7`）。

## 2. 本轮完成的内容

### A. 内容全部外置（最大的一件）

1. **8 份 JSON**：`app/src/main/assets/content/{places,resources,yield_rules,npcs,npc_proactive_rules,npc_lines,place_aliases,npc_keywords}.json`。
2. **覆盖层**：`<filesDir>/content/` 放同名文件即可覆盖/追加；实体型按 `id` 整条替换，
   映射型（台词/别名）按 key 整体替换，关键词表整份替换。`removedIds`/`removedKeys`/`removedAliases` 用来删内置条目。
3. **Kotlin 里的内容常量全拆了**：`FakePlaceRepository.DEFAULT_PLACES` + 具名地点 + `PLACE_ALIASES`、
   `FakeNpcRepository.DEFAULT_NPCS`、`InMemoryResourceCatalog.DEFAULT`、`InMemoryResourceYieldRuleCatalog.DEFAULT`
   + 具名规则、`FakeNpcProactiveRuleCatalog.DEFAULT`、`NpcLineCatalog`（整个文件）、`NpcKeywordRules.BUILT_IN`
   ——全部删除。那些类现在是"收列表的内存实现"，运行时用的是 `LiveRepositories.kt` 里的 `Content*` 实现。
4. **加载**：`data/content/ContentStore.kt` 在 `AppContainer` 构造期同步加载（口径同 `gridManager`），
   暴露 `ContentIndex` 快照；仓储读它，所以 `reload()` 之后一切自动生效。
   `AppContainer.contentStore` 也是开发者面板的来源。
5. **诊断三层**：logcat（tag `ContentLoad`）+ 设置页「内容（开发者模式）」（条数 + 全部诊断 + 重读按钮）+ `ShippedContentTest`（构建前拦住内置内容出错）。
6. **解析的宽容度**：枚举按名字忽略大小写；错误消息列出合法值；`WeatherKind` 额外认 `RAINY` 简写；
   逐条解析（一条写坏不牵连整份文件）；DTO→领域对象的构造整个包在 `runCatching` 里，
   所以领域模型的 `require` 自动变成一条诊断而不是崩溃。
7. **降级粒度刻意不对称**：地点/资源/规则坏一条只丢那一条；**NPC 的一条作息指向不存在的地点 → 整位 NPC 剔除**
   （半截作息会瞬移，那是"静默说谎"，比缺席糟）。

### B. 现场采点（"记点"）

`domain/map/PlaceDraft.kt`（`PlaceDraft`/`defaultActionsFor`/`rejectionReason`/`newPlaceId`/`PlaceWriter`）
+ `data/content/JsonPlaceWriter.kt`（追加 + **原子写** + reload）+ 地图右侧「记点」按钮 + 无状态表单。
`MapViewModel.captureCurrentPlace(draft)` 成功后立刻选中新点（新点所在格若未揭示就不会画，详情卡是唯一即时确认）。

### C. 真实天气（Open-Meteo）

1. **来源**：`platform/weather/OpenMeteoWeatherApi.kt`，`https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m,relative_humidity_2m,weather_code`。
   非商用免费、**无需 API key**、10000 次/日；数据 CC BY 4.0 → 设置页有署名行。
2. **零新依赖**：只用已有的 `ktor-client-okhttp` + `bodyAsText()` + 私有 `Json`（不装 content-negotiation）。
3. **`WeatherSource`**（新建，`domain/world/WeatherSource.kt`）：与 `SeasonSource` 同构——
   `manualOverride` 非 null 时优先，`setOverride(null)` 回到真实来源。**`MutableWeatherProvider` 已删除**。
4. **`RemoteWeatherSource`**：轮询写成冷流挂在 `stateIn(WhileSubscribed(5min))` 上，所以没人看就停。
   成功 15 分钟刷一次、否则 1 分钟重试；**四条跳过规则**——没有定位 fix / 非前台 / 手动覆盖生效 / 失败保留上次好值。
5. **冷启动缓存**：`WeatherCache` 端口 + `DataStoreWeatherCache`（复用 `rt_settings` 文件，键 `weather_*`）；
   `AppContainer` 用 `runBlocking` 读首帧。初始化值优先用缓存，否则是**多云**占位（不是"晴"——"不知道"不能说成晴）。
6. **WMO 4677 映射**：`domain/world/WmoWeatherCode.kt`（纯函数，未知码返回 null 让上层保留好值）。
   `WeatherKind.WIND` **永远不可达**（风是独立变量，不是天气码）。
7. **设置页**：顶部「自动」行 + 每个天气行 + 状态行（"上次更新 HH:mm"/"正在重试"）+ 署名。

### D. 定位精度与看门狗

1. **精度等级**：`domain/map/LocationQuality.kt`，`POOR` 的分界**就是** `RecordTrackPointUseCase.MAX_ACCURACY_METERS`（50m）。
   `MapUiState.lastAccuracyMeters` 在去噪闸门**之前**写入，所以被丢弃的点同样看得见。
   GPS chip 变成 `GPS 定位中 · 精度 12 m` / `GPS 定位中 · 信号弱，暂时不记轨迹`（POOR 时刻意不给米数）。
2. **看门狗**：`domain/map/LocationWatchdog.kt`（纯函数 + `LocationWatchdogState`，边沿触发）；
   90s → 探缓存位置，180s → **同引擎**重发（绝不降级换源），300s → `LocationHealth.stalled` 亮起，
   地图提示"定位卡住了 · 去系统设置允许后台定位"。
3. `AndroidLocationProvider` 新增 `scope`（看门狗跑在 `Dispatchers.Main.immediate`，那几个 client 字段不是 `@Volatile`）。

### E. 旧 bug 修复

1. **地图冷启动空白**：上一条轨迹点就在脚下（<8m）时，去噪闸门判 `TOO_CLOSE` 后整条丢弃，连渲染一起没了
   ——玩家标记、地点、附近卡片全不出现。现在只对"原地没动"这类拒绝补一次渲染（`RejectReason.isStandingStill()`），
   不落库、漂移保护不变。
2. **设置页天气选中态**：原来用**生效值**判断选中，真实天气是多云时"多云"那一行会被点亮，看着像手动生效了。
   现在用 `weatherOverride`。

## 3. 关键约定（仍然有效）

- 手写 DI（`AppContainer`），不用 Hilt；Compose stateless + UiState；domain 禁 Android SDK。
- 外部 SDK 全走 adapter/interface；所有时间注入 `WorldClock`；时区统一 `core/time/WORLD_ZONE`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest`（**342 全绿**）。**数据层没有单测**（无 Robolectric），
  所以新逻辑尽量放在 domain 纯函数里——本轮的 WMO 映射、精度等级、看门狗决策都是这么做的。
- 真机：Honor 100（MAA-AN00）；`adb install -r` 可直接覆盖。**这台机器上第三方应用的 logcat 基本被裁掉**，
  实机验收只能看 UI。
- **复杂实机交互由用户手动测试并回报**；AI 只做构建/安装/启动/单测。
- 世界原点 39.7326,116.1712（待校准）；cellSize 默认 40m。
- 新增 `PlaceType` 会自动多一个地图图标层，但必须在 `placeStyle()` 补配色/字形。

## 4. 数据与设置现状

- **Room v6**（本轮未改）：`track_points` / `exploration_cells` / `footprint_events` / `memories` / `inventory_items` / `npc_messages` / `npc_states` / `npc_commitments`。
- **DataStore `rt_settings`**：原有项 + `npc_clock_offset` / `npc_proactive_level` / `npc_show_affection` + **`weather_kind` / `weather_humidity` / `weather_temp_c` / `weather_fetched_at_ms`**。
  `Context.settingsDataStore` 现在是 `internal`（`DataStoreWeatherCache` 共用同一个文件）。
- **内容覆盖层**：`<filesDir>/content/*.json`（可能不存在，那就是全用内置）。
- **足迹事件类型**：与 `HANDOFF_5 §4` 相同，本轮没有新增。

## 5. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# 把一份手改的内容推进覆盖层（debug 包用 run-as）
& "D:\Android\Sdk\platform-tools\adb.exe" push .\app\src\main\assets\content\npcs.json /data/local/tmp/npcs.json
& "D:\Android\Sdk\platform-tools\adb.exe" shell "run-as com.rainingtrace mkdir -p files/content"
& "D:\Android\Sdk\platform-tools\adb.exe" shell "run-as com.rainingtrace sh -c 'cat /data/local/tmp/npcs.json > files/content/npcs.json'"
# 然后进设置页点「重新读取内容」

& "D:\Android\Sdk\platform-tools\adb.exe" shell "run-as com.rainingtrace ls -l files/content"   # 看覆盖层
# 单测计数：app\build\test-results\testDebugUnitTest\*.xml
```

## 6. 本轮的设计取舍与提醒（下个 AI 别推翻重来）

**内容层**
- **JSON 是唯一真相**。想加地点/NPC/台词就改 `assets/content/*.json` 或覆盖层，**不要**在 Kotlin 里加常量
  ——那会造出第二份必然漂移的副本，这正是本轮拆掉的东西。
- **内置 assets 读不出来时落成"空" + 一条 ERROR**，不再有代码兜底。这是有意的：空是响亮的，
  且 `ShippedContentTest` 会在构建前拦住（它读真实文件、断言零诊断 + 引用完整性 + 条数下限）。
- **`ShippedContentTest` 是原编译期常量的替代品**。删掉它等于删掉"作息里 placeId 写错"的唯一保险。
- **枚举轴（`PlaceType`/`NpcTopic`…）仍然在代码里**：JSON 只能引用它们，不能新增。加一个新类型 = 加功能，本来就该改代码。
- 覆盖层的诊断分 ERROR（丢条目）/WARN（保留但可疑）；内置内容必须零诊断。

**天气**
- **不要订阅 `RemoteWeatherSource.weather` 来做轮询**：轮询是它的 upstream 冷流，多一个常驻订阅者会让它永不停止。
- **不要给 `WeatherKind` 加 `UNKNOWN`**：它会污染 `label()` 的穷尽 when、`weatherPreset`，
  以及**内容 JSON 的合法天气值列表**（`ContentJson.parseWeatherKinds`）。状态用 `WeatherStatus` 表达。
- **湿度必须 `/100.0` 再 `coerceIn(0.0, 1.0)`**：API 给百分比且台站偶发 101，而 `WeatherState.init` 有 `require`。
- **不要复用 `ContentJsonFormat`** 解天气：它是 `data.content` 的 internal 且 `prettyPrint = true`。
- HTTP 客户端 `by lazy` **不关闭**（进程存活期唯一实例；每次 `use{}` 会重建引擎线程池）。
- 缓存端口独立于 `AppSettingsRepository`：后者有两个测试 fake 实现它，加成员会直接编译失败；
  而且缓存是"系统记账"不是用户偏好。

**定位**
- **看门狗只在前台默认档判定**（`running && passiveIntervalMs == null`）。后台 15 分钟档的静默是预期的。
- **换档（尤其 `setPassiveIntervalMs(null)`）必须重置 `lastCallbackAtMs` 与标志位**，
  否则从后台回到前台会立刻误报"定位卡住了"。
- **重发只走同一个引擎**（`restartSameEngine`）。不要退化成 `tryStartFused() ?: tryStartLocationManager()`：
  那会在运行中静默换源，重复发点、弄乱轨迹。
- **探缓存位置要传 `fresh = false`**：缓存值不算"定位活过来了"，否则看门狗永远升级不到重发。
- **决策必须走 `LocationWatchdogState.consume`**，不要裸调纯函数——边沿触发靠标志位，不靠时长区间。
- 看门狗协程跑 `Dispatchers.Main.immediate`：`fusedClient`/`fusedCallback` 等字段都不是 `@Volatile`。

## 7. 坑/风险清单（累积）

**内容 / 迁移**
- 手动 `ALTER` 与 schema json 不一致会崩。Room 改 schema 的顺照 `HANDOFF_5 §6`。
- **Kotlin 的注释里写 `content/*.json` 会编译失败**：Kotlin 块注释是可嵌套的，`/*` 会开一个新的嵌套注释，
  于是外层注释永远不闭合（本轮踩过，报 `Unclosed comment`）。写 `content/` 下的文件名时避开 `/*`。
- `preferencesDataStore(name=...)` **同一份文件只能有一个委托**，否则运行期抛
  "multiple DataStores active for the same file"。

**天气 / 网络**
- Open-Meteo 免费额度是"非商用 + <10k 次/日"。真要商用得换有 key 的方案或自托管。
- 断网时天气停在**上一次成功的值**并显示"正在重试"；从未成功过则停在"多云"占位。这是有意的——不说谎。

**定位 / 服务**
- MagicOS 后台限制严格：**需要用户手动放行**（后台活动/自启动 + 电池白名单）。熄屏轨迹断段基本是这个原因，
  代码修不了；看门狗只负责让它"可见"。
- Android 14 起禁止从后台启动 location 前台服务 → 服务只能在应用可见时拉起；`startForeground` 失败必须立刻 `stopSelf`。
- `TrackRecordingService` 内**只写轨迹点**。

**测试**
- `stateIn`/`WhileSubscribed` 的协程在测试里挂 `backgroundScope`，否则 `runTest` 报 `UncompletedCoroutinesError`。
- **对着无限 `while(true) { delay() }` 的轮询流，绝不能用 `advanceUntilIdle()`**（永远等不到尽头）；
  用 `runCurrent()` 或 `advanceTimeBy(确定时长)`。
- `List.asSequence()` 里不能调 suspend。

## 8. NEXT（未排期，开工前先确认）

**P0 打磨剩余**
1. **资源点随机刷新（spawn）**：先定刷新密度与位置约束口径，再选确定性 seed / 落库（`HANDOFF_4 §6`）。
2. 地图图标/缩略图换美术（排在 spawn 之前）。NPC 头像同理（`NpcAvatar` + `NpcAvatarPalette`）。
3. 定位体验继续打磨：本轮的看门狗只做"发现 + 一次性补救"，没做失败后的退避或提示消失条件。

**NPC 线的延伸**（`HANDOFF_5 §8` 原样有效，且现在更容易了——改档案只需改 JSON）
4. 接真 LLM：换 `NpcMessageParser` / `NarrativeService` 两个实现即可。
5. NPC 交互（购物/接任务/一起走走）：属于"给地点/动作加内容"。
6. `NPC_*` 足迹的可查询化（现在 payload 单列编码、SQL 不能按 key 过滤）。

**P1 主干（`doc/03`）**：Supabase 同步、家园/宿舍、种植制作、轻经济。

**文档债务**：`HANDOFF_1`~`HANDOFF_5` 已按用户要求**封存为历史路径**，里面关于
"内容硬编码在 Kotlin""天气还没接""地图冷启动空白"的描述都已过时。**冲突以本文件为准。**
`doc/06`、`doc/11`、`doc/08`、`doc/04`、`doc/03`、`doc/README`、`doc/01` 已在本轮同步修正。

## 9. 代码地图（要点）

```
core/
  common/AppContainer（内容 ContentStore / 天气 weatherSource + weatherHttpClient + weatherCache /
                       定位 locationProvider / NPC 两引擎 / tickNpcEngines）
  lifecycle/AppForegroundState（前台闸门：地图、NPC 引擎、天气轮询共用的省电边界）
  time/WorldClock + WORLD_ZONE
  ui/ MoodLabels、WorldLabels、LocationLabels(gpsQualitySuffix)、LocalImage、AudioNoteChip、NpcAvatar
domain/
  content/ ContentModels(WorldContent/ContentIndex/ContentDiagnostic/ContentPanel)、
           ContentMerge(byId/byKey)、ContentValidator
  world/ WorldState(+Provider)、WeatherState、WeatherSource(+WeatherStatus)、WeatherApi、
         WmoWeatherCode、RemoteWeatherSource、WeatherCache、FakeWeatherProvider、
         SeasonSource(+Derived 节气推导)、SolarTerm、WorldCondition(含 Not + weight)、
         ResourceYieldRule(+Catalog)、RandomSource
  map/ Place(+PlaceCategory/PlaceType/Repository)、PlaceDraft(+PlaceWriter/PlaceWriteResult)、
       LocationQuality、LocationHealth、LocationWatchdog(+State)、
       MapVisuals(placeStyle/npcStyle/placeVisualsFor)、GridManager、GeoMath
  npc/ NpcProfile(+NpcTrait/NpcTopic/NpcScheduleEntry)、NpcPresence(+resolveSchedule)、
       NpcPresenceUseCase、NpcRepository、RecordNpcEncounterUseCase、
       NpcState/NpcMessage(+Repository)、NpcMessageParser(+RuleBased + NpcKeywordRules)、
       NarrativeService(+Template/Validator)、ScheduleFacts、NpcAffection、SendNpcMessageUseCase、
       NpcProactiveRule(+Catalog)、NpcProactiveMessageUseCase、
       NpcCommitment(+Repository/Feasibility/UseCase/ScheduleOverride)、NpcMessageWriter
  exploration/ track/ memory/ footprint/ settings/
data/
  content/ ContentStore、ContentJson(DTO + 宽容解析)、JsonPlaceWriter
  local/ Room v6 + Migrations + Daos + Entities
  repository/ LiveRepositories(ContentPlace/ContentNpc/ContentResource/ContentYieldRule/ContentNpcProactive)、
              FakePlaceRepository/FakeNpcRepository(收列表的内存实现，主要给测试)、
              RoomNpcRepositories、RoomRepositories
  settings/ DataStoreSettingsRepository、DataStoreWeatherCache
platform/
  map/ MapLibreAdapter        location/ AndroidLocationProvider(含看门狗) / SwitchableLocationProvider /
                              FakeLocationProvider / TrackRecordingService
  weather/ OpenMeteoWeatherApi
  audio/ camera/ ar/（AR 阶段封存）
feature/
  map/ MapScreen|MapViewModel（筛选/聚焦/产出预览/NPC ticker/记点表单/精度与卡住 chip）、WorldStatusViewModel
  messages/ journal/ inventory/
  settings/ SettingsViewModel|SettingsScreen（定位/足迹/消息/世界状态调试+天气自动行/时段/季节/NPC调试/内容开发者面板）
  shell/ RainingTraceApp（Tab + 聊天子路由 + 底栏未读红点）、Destinations
```

## 10. 待你确认的三件事

1. **真实天气**：地图左上 chip 的天气与手机自带天气应大体一致，温度**不是**默认的 20.0。
   设置页切「大雨」应立刻生效（雨天在湖边观察掉「湖泊记忆碎片」）；点回「自动」约 1 分钟内回到真实值，
   且**"多云"这类行不应被点亮**。飞行模式冷启动应保持上次的值、不跳回"晴"。
2. **定位**：室外走一段，`GPS 定位中 · 精度 ±N m` 的 N 应从几百收敛到 10~20；进楼里应变成"信号弱，暂时不记轨迹"
   且玩家标记不再跟随。把系统里"后台定位"改成受限 + 开省电，前台静置 5 分钟应出现"定位卡住了"提示，
   放行后提示消失。
3. **内容**：设置页「内容（开发者模式）」应显示 8 类条数（地点 9 / 资源 18 / 产出规则 21 / NPC 5 / 主动规则 10 /
   台词 key 36 / 别名 15 / 话题 8）且诊断为空；地图右侧「记点」应当能新建一个地点并立刻出现在地图上。