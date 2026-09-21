# RainingTrace / 雨迹 — 开发进度交接（NPC 地图腿 + 消息腿 + 约定）

> 更新时间：2026-09-21（新对话开工前请先读本文件 + doc/02_AGENTS.md + GDD_v3.md）
> 上一版：HANDOFF_4.md（定位闭环 + 世界状态层 + 采集线）
> 本版覆盖：**季节节气推导**、**NPC 地图腿**（作息/走动/卡片/遇见）、**NPC 消息腿**（会话/聊天/好感情绪/主动消息）、**约定（片 3）**

## 1. 当前一句话状态

NPC 的两条腿都通了：**地图上按作息出现并按路点走动、能点出只读卡片、走到跟前会"遇见"**；**消息页能聊天、他有性格与情绪、记得你、还会主动找你**；**答应了的约定他真的会去**（作息被覆盖）。
单测 **249 全绿**；Room **v6**（`5.json` / `6.json` 已导出，v4→v5、v5→v6 都在真机实测过迁移）。AR 仍阶段封存（见 HANDOFF_3 §7）。

**下一步未排期**，候选见 §8。

## 2. 本次会话完成的内容

### A. 季节按节气推导（顺手做的小任务）

1. `SolarTerm.kt`：寿星公式 `floor(Y*0.2422+C) - floor((Y-1)/4)`。**立春的闰年偏移单独取 1**——不这样取 2000/2020/2024 会早一天。
2. `DerivedSeasonSource.kt`：按节气推导 + 手动覆盖优先，`WhileSubscribed` + 60s tick。`setSeason(null)` = 回到自动推导。
3. `SeasonSource` 加 `manualOverride`（UI 要渲染"自动"那一行）；设置页"未确定"改成"自动（按节气）"。
4. **行为变化**：`SeasonIn` 不再恒为 null，4 条季节规则（秋日松果 / 春日花瓣 / 冬日雪霜纹 / 秋天果林）现在真的会触发。
5. **没做校正表**：校准过 2000–2099 的边界，说不出任何一个确切的 ±1 天特例年份，空表就是死代码。公式仍是近似（±1 天），要零误差就换成离线日期表，调用方不用改。

### B. NPC 地图腿（片 1 层）

1. **领域**：`NpcProfile(id/name/oneLiner/schedule/role/traits/topics/favoriteTopic/backstory)`、`NpcScheduleEntry(startMinute=到达时刻, placeId, travelMinutes, activity)`、`resolveSchedule(profile, places, overrides)`、`ResolvedSchedule.presenceAt(minuteOfDay)`。
2. **位置 = 时间的纯函数**：路点 + 经纬度直线插值（`GeoMath.lerpTo`），**不需要路网 / A\* / 不可通行格**。作息按**天环**处理：跨零点、最后一条走到次日第一条都成立；行走时长夹紧到"间隔-1"。
3. **地图**：`rt-npcs` source + 两种姿态静态图标层（"人"青绿 / "行"琥珀），沿用"静态层 + eq 过滤"（避开 `icon-image(match)` 的坑）。点选 **NPC 优先于地点**。
4. **刷新**：`MapViewModel.runNpcTicker` 10s，**三重门控**（前台 → 有人在走且在视口内 → 才渲染），只写 `rt-npcs` 一个 source。
5. **遇见**：`RecordNpcEncounterUseCase`（30m 内首次写 `NPC_MET` 足迹）。

### C. NPC 调试时间偏移（片 0）

设置页「NPC（调试）」：真实时间 / ±1/3/6/12 小时。只挪 NPC 的分钟数（`NpcPresenceUseCase.minuteOfDayFor`），**不动 `WorldClock`**——那会污染轨迹时间戳。想验证"他真的在走"就切一下。

### D. NPC 消息腿（片 1 + 片 2）

1. **领域**：`NpcTrait`(8) / `NpcTopic`(8) / `NpcMood` + 关系阶段 / `NpcState` / `NpcMessage`。`NpcProfile` 新增字段**全部带默认值**（否则既有测试构造点全炸）。
2. **数据**：Room **v5** — `npc_messages`（复合索引 `(npcId, createdAtEpochMs)`）+ `npc_states`。`MIGRATION_4_5` 照 `5.json` 的 createSql 逐字抄。
3. **对话引擎**：`NpcMessageParser`(接口) + `RuleBasedNpcMessageParser`（关键词表 + 地点别名**先长后短**）→ `NarrativeService`(接口) + `TemplateNarrativeService`（部件拼接 + 两层台词回落 + 每部件 ≥4 变体 + **避开最近 3 条用过的句子**）。
4. **好感与情绪**：`NpcAffection` 全纯函数。日上限用 `todayAffectionGain`/`todayDateKey` O(1) 记账。**基线情绪不落库**（由世界状态派生），所以情绪会自己淡回去却零定时器。
5. **守门人**：`DialogueValidator` 机械挡下承诺词 / 超长 / 占位符残留。
6. **UI**：`MessagesRoute`+`MessagesScreen`（会话列表带"此刻在「X」· 在做什么"）、`ChatRoute`+`ChatScreen`+`ChatViewModel`（子路由 `npc_chat/{npcId}`，项目里唯一带导航参数的路由）、底栏未读红点（`BadgedBox`）、设置页「消息」分区。
7. **主动消息**：`NpcProactiveRule` + `NpcTriggerCondition`（**复用 `WorldCondition`**，只新增跃迁/玩家行为条件）+ `FakeNpcProactiveRuleCatalog`（10 条）+ `NpcProactiveMessageUseCase`。

### E. 约定（片 3，"他答应了就真的会去"）

1. **`NpcCommitment`** + Room **v6**（`npc_commitments`，无索引，表很小）。状态机就是 `status`（AGREED → KEPT/MISSED），所以不需要额外去重记录。
2. **作息覆盖**：`NpcScheduleOverride` + `resolveSchedule(..., overrides)`。窗口内的基础条目让位给约定条目（**带 `travelMinutes`**，所以他"走过去"那段在地图上也是真的）。`NpcPresenceUseCase` 只取"今天且已开始"的约定。
3. **答应与否由真实作息决定**：`answerCommitment()` —— 他本来就在那儿 → `AlreadyThere`；算得出步行时间（80 m/min）且来得及 → `Agree(travelMinutes)`；马上就另有安排 → `Busy`；没有效作息 → `Unknown`。**不是随机数**。
4. **"不骗人"的升级**：`DialogueValidator.validate(text, allowPromises)`。**只有承诺真的写进库，调用方才会传 true**——所以承诺词能不能说由数据决定，不靠自觉。
5. **兑现**：`NpcCommitmentUseCase.tick(playerCoordinate)` 与主动消息**共用同一个 tick 循环与 `NpcMessageWriter`**。玩家来了 → KEPT + "我到了，在「X」。" + 好感 +2 + `NPC_COMMITMENT_KEPT` 足迹；窗口过了没来 → MISSED + `NPC_COMMITMENT_MISSED` 足迹（不立刻发消息）。
6. **"你没来"有后果**：`NpcTriggerCondition.PlayerMissedCommitment` + 两条规则 → 他下次主动提一句。
7. **补算**：约定判定按"日期 + 分钟"算，所以熄屏期间到期的约定，回前台一次 tick 就能补出结果（有测试）。

## 3. 关键约定（仍然有效）

- 手写 DI（`AppContainer`），不用 Hilt；Compose stateless + UiState；domain 禁 Android SDK。
- 外部 SDK 全走 adapter/interface；所有时间注入 `WorldClock`；时区统一 `core/time/WORLD_ZONE`。
- 单测：`.\gradlew.bat :app:testDebugUnitTest`（**249 全绿**）。**数据层没有单测**（无 Robolectric / in-memory Room），所以新逻辑要尽量放在 domain 纯函数里。
- 真机：Honor 100（MAA-AN00）；`adb install -r` 可直接覆盖（迁移已验证）。
- **复杂实机交互由用户手动测试并回报**；AI 只做构建/安装/启动/单测/低阶 logcat。
- 世界原点 39.7326,116.1712（待校准）；cellSize 默认 40m。
- 新增 `PlaceType` 会自动多一个地图图标层，但必须在 `placeStyle()` 补配色/字形。

## 4. 数据与设置现状

- **Room v6**：`track_points` / `exploration_cells` / `footprint_events` / `memories` / `inventory_items` / **`npc_messages`** / **`npc_states`** / **`npc_commitments`**。
  - v1→v2 destructive（历史）；v2→v3、v3→v4、v4→v5、v5→v6 都是显式 Migration。改 schema 必须写 Migration + 同步 schema 导出。
- **DataStore `rt_settings`**：原有项 + `npc_clock_offset`（调试偏移）、`npc_proactive_level`、`npc_show_affection`。
- **足迹事件类型**（append-only，payload 是单列编码、SQL 无法按 key 过滤）：`CELL_REVEALED` / `CELL_VISITED` / `PLACE_OBSERVED` / `RESOURCE_ACQUIRED` / `MEMORY_CREATED` / `PHOTO_CAPTURED` / `NPC_MET` / `NPC_TALKED` / `NPC_MESSAGE_SENT` / `NPC_COMMITMENT_KEPT` / `NPC_COMMITMENT_MISSED`。
- NPC 档案与主动规则都在 `data/repository/FakeNpcRepository.kt` 与 `FakeNpcProactiveRuleCatalog.kt`（内容引用 `FakePlaceRepository.XXX.id` 常量，编译期绑定）。

## 5. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
# 迁移/崩溃/地图/NPC 引擎：
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -v time | Select-String "Room|Migration|FATAL|MapLibreAdapter"
# 确认新表已建（设备没有 sqlite3，用 grep 代替）：
& "D:\Android\Sdk\platform-tools\adb.exe" shell "run-as com.rainingtrace sh -c 'grep -c npc_commitments databases/rainingtrace.db'"
# 单测计数：app\build\test-results\testDebugUnitTest\*.xml
```

## 6. 本次的设计取舍与提醒（下个 AI 别推翻重来）

**NPC 位置**
- **位置永远是时间的纯函数**（`presenceAt(minuteOfDay)`），不落库、不需要 tick 驱动状态。**约定也是靠"作息覆盖"实现，不是另写一套挪人的逻辑**——所以地图、遇见判定、消息里的"此刻在做什么"全部自动一致，零寻路。
- **作息按天环**：`minute` 早于最早条目时要回落到 `n-1`；`travel` 与间隔重叠必须夹紧。这两条都有测试兜着，别"简化"。
- **`NpcScheduleOverride` 不允许跨零点**（构造期 require）。约定窗口因此要 `coerceAtMost(MINUTES_PER_DAY - 1)`，别用 `mod`（会在 23:30 的约定上崩）。

**消息与状态**
- **AI 文本绝不驱动状态**：好感/情绪在调用 `NarrativeService` **之前**就算完，输入只有"解析结果 + 档案"。回复文本只写进消息表。将来接 LLM 也守这条（Prompt 08）。
- **承诺词只有"承诺已落库"才放行**（`validate(allowPromises = commitment.saved)`）。**不要**为了方便把 allowPromises 直接写死 true——那是把"不骗人"的机械保障拆掉。
- **台词去重用"包含"判断**，不是等值：最近的消息是"招呼+主体+尾"拼起来的整句，单条台词只是它的一部分。
- 好感按 `todayAffectionGain`/`todayDateKey` 记账（跨日重置）。别改成扫足迹。
- 情绪：**只存事件情绪 + 起始时刻**，基线由 `baselineMoodOf` 派生（walking→BUSY、深夜→TIRED、雨夜→DOWN）。

**引擎与省电**
- **两个 NPC 引擎（主动消息 / 约定兑现）跑在 `AppContainer.applicationScope`，前台门控，60s tick**，共用 `tickNpcEngines()`（先兑现约定，再主动消息）。熄屏不生成（HANDOFF_4 §6「后台服务只写库」+ Android 14 限制），回前台补算一次即可——两者都幂等。
- **绝不订阅 `worldStateProvider.state`**：它是 `WhileSubscribed(5min)`，引擎作为永久订阅者会让 30s ticker 永不停止，**无声地**破坏省电口径。只在 tick 里调 `current()`。
- 玩家坐标用 **`locationProvider.latest`** 取快照，**不要**再订阅 `updates`（多一个常驻 collector 没意义）。
- 主动消息的"天气跃迁"靠引擎内存变量 `lastWeatherKind`，**冷启动不算跃迁**（否则每次开 app 必发一条）。
- 玩家行为靠内存水位 `watermarkMs`。后台唯一写库的是 `TrackRecordingService`（只写轨迹点、不写足迹），所以进程重启把水位置 now 不会漏事件。
- 限流是**四重**：规则冷却（默认 4h）+ 全局最小间隔 2h + 每日上限（0/2/5）+ 免打扰 07:00–23:00。别减少层数。

**数据 / 迁移**
- 顺序照旧：**先写 Entity/Dao → 改 version（不写 Migration）→ 构建生成 `N.json` → 逐字抄 createSql（含索引）→ 再填 Migration → 再构建核对 identityHash**。Boolean 是 `INTEGER NOT NULL`，可空列不能带 NOT NULL。
- `NpcCommitmentDao.all()` / `NpcCommitmentRepository.all()` 是**有意的全量查询**：表很小（同时只有几条 AGREED），比增量/窗口函数简单且不会漏。消息表的会话列表同理（单 Flow + 领域侧 groupBy）。
- 新增 `FootprintEventType` 枚举值**不需要迁移**（存字符串）。

**UI**
- **底栏未读数在 `RainingTraceBottomBar` 内部订阅**，不要提到 `RainingTraceApp` 顶层（会让每来一条消息就重建 Scaffold + NavHost）。
- 聊天页是**子路由 + 导航参数**（`npc_chat/{npcId}`），不是一次性请求对象。`currentRoute` 会是模板串 `"npc_chat/{npcId}"`，比较时要统一用 `Routes.NPC_CHAT` 常量。
- `MemoryFocusRequest` / `TrackDayFocusRequest` 那套是**为常驻 Tab 之间跳转**设计的；主从导航（点会话进聊天）不要用它。

## 7. 坑/风险清单（累积，别再踩）

**NPC**
- `NpcProfile` 加**必填**字段会让既有 3 个测试文件 + `FakeNpcRepository` 构造点全编译失败 —— 新字段一律带默认值。
- `favoriteTopic` 必须在 `topics` 里（构造期 require），否则专属台词永远不出现且无报错。
- 作息里的 `placeId` 写错会被**静默剔除**（那个人整天不出现）；用引用 `FakePlaceRepository.XXX.id` 常量 + `FakeNpcRepositoryTest` 的全量断言兜住。
- `RuleBasedNpcMessageParser` 必须**永不抛异常**，解析不出来就 `confidence = 0`，走"没听懂"分支，绝不假装听懂。
- NPC 头像与地点图标重叠：NPC 用不同字形/颜色；**本片不画 NPC 地图文字名**（会和地点名叠）。

**数据 / 迁移**
- 手动 `ALTER` 与 schema json 不一致会崩（`Migration didn't properly handle`）。
- 筛选持久化只存"隐藏集合"（存显示集合会让新类型在老安装里被藏掉）。

**定位 / 服务**
- Android 14 起禁止从后台启动 location 前台服务 → 服务只能在应用可见时拉起；`startForeground` 失败必须立刻 `stopSelf`。
- MagicOS 后台限制严格：**需要用户手动放行**（后台活动/自启动 + 电池白名单）。熄屏轨迹断段基本是这个原因。
- `TrackRecordingService` 内**只写轨迹点**，别把世界状态/NPC 逻辑搬进去。

**测试**
- `SystemWorldStateProvider` / `DerivedSeasonSource` 用 `stateIn` 会常驻收集协程：测试里挂 `backgroundScope`，否则 `runTest` 报 `UncompletedCoroutinesError`。
- `List.asSequence()` 里不能调 suspend（`filter`/`mapNotNull` 在 Sequence 上是非 inline 的）——NPC 引擎踩过，用 List 的版本。

## 8. NEXT（未排期，开工前先确认）

**NPC 线的延伸**
1. **接真 LLM**：换 `NpcMessageParser` / `NarrativeService` 两个实现即可（Supabase Edge Function 或直连），状态与规则完全不用动；失败走确定性回落。这是本片刻意留的口子。
2. **NPC 交互**（对话之外的）：购物（他店里多出你要的东西）、接任务、一起出去走走——都属于"给地点/动作加内容"，不用动架构。
3. **玩家预设/开发者模式做进 NPC 档案**：现在经历（`backstory`）是硬编码，将来让玩家配置。
4. **`NPC_*` 足迹的可查询化**：现在 payload 单列编码、SQL 不能按 key 过滤。要做"你上周三来过湖边吧"这类句子，得让它可查（专用列或新事件）。

**P0 打磨剩余**
5. 资源点随机刷新（spawn）：先定刷新密度与位置约束口径，再选确定性 seed / 落库（HANDOFF_4 §6）。
6. 地图图标/缩略图换美术（§6 的依赖：排在 spawn 之前）。NPC 头像同理（`NpcAvatar` 一处 + `NpcAvatarPalette` 色板）。
7. 真实天气 API：换一个 `WeatherProvider` 实现即可（条件与产出不用改）。
8. 定位体验细节：GPS 信号质量指示；Fused 在 MagicOS 省电下回调稀疏的兜底。

**P1 主干（doc/03 任务树）**：Supabase 同步、真实天气、家园/宿舍、种植制作、轻经济。

**文档债务**：`doc/06_地图_定位_AR专项.md`、`doc/11_MVP_首个可玩切片.md`、`HANDOFF_1/2/3` 里关于"只前台定位""产出只有观察记录""没有 NPC"的描述已过时；本文件优先级最高，冲突以本文件为准。

## 9. 代码地图（要点）

```
core/
  common/AppContainer（世界状态 / 记录服务监督者 / 产出规则 / NPC 仓储与两引擎 / tickNpcEngines）
  lifecycle/AppForegroundState（前台闸门：地图与两个 NPC 引擎的省电边界）
  time/WorldClock + WORLD_ZONE
  ui/ MoodLabels、WorldLabels、LocalImage、AudioNoteChip、NpcAvatar
domain/
  world/ WorldState(+Provider)、WeatherState、SeasonSource(+Derived 节气推导)、SolarTerm、
         WorldCondition、ResourceYieldRule(+Catalog)、RandomSource
  map/ Place(+PlaceCategory/PlaceType/Repository)、MapVisuals(placeStyle/npcStyle/placeVisualsFor)、
       GridManager、GeoMath(distanceMetersTo / lerpTo)
  npc/ NpcProfile(+NpcTrait/NpcTopic/NpcScheduleEntry)、NpcPresence(+resolveSchedule/ResolvedSchedule)、
       NpcPresenceUseCase、NpcRepository、RecordNpcEncounterUseCase、
       NpcState(+NpcMood/RelationshipStage/NpcStateRepository)、NpcMessage(+Repository)、
       NpcMessageParser(+RuleBased)、NarrativeService(+Template/Validator)、NpcLineCatalog、
       ScheduleFacts、NpcAffection、SendNpcMessageUseCase、
       NpcProactiveRule(+Catalog)、NpcProactiveMessageUseCase、
       NpcCommitment(+Repository/Feasibility/UseCase/ScheduleOverride)、NpcMessageWriter
  exploration/ PerformPlaceActionUseCase（观察/采集统一 + preview）
  track/ memory/ footprint/ settings/
data/
  local/ Room v6 + MIGRATION_2_3…5_6、Daos（含 npc 三张表）、Entities
  repository/ FakePlaceRepository(9 地点 + PLACE_ALIASES)、FakeNpcRepository(5 人)、
              FakeNpcProactiveRuleCatalog(10 条)、RoomNpcRepositories、RoomRepositories
  settings/ DataStoreSettingsRepository（含 npc 设置三项）
platform/
  map/ MapLibreAdapter（每类型静态图标层 + NPC 两种姿态层 + 记忆心情层）
  location/ audio/ camera/ ar/（AR 阶段封存）
feature/
  map/ MapScreen|MapViewModel（筛选/聚焦/产出预览/NPC ticker）、WorldStatusViewModel
  messages/ MessagesRoute|MessagesScreen|MessagesViewModel、
            ChatRoute|ChatScreen|ChatViewModel、MessageFormat
  journal/ inventory/ settings/（含世界状态调试区 + NPC 调试偏移 + 消息分区）
  shell/ RainingTraceApp（Tab + 聊天子路由 + 底栏未读红点）、Destinations
```

## 10. 待你确认的两件事

1. **NPC 走动的目视确认**：设置页切「NPC（调试）」到 `+3 小时`，回地图看有没有人在两个地点之间挪（约 10s 一步）。之前因为调试区只能覆盖"时段"、NPC 读真实分钟数，一直没法亲眼验证——片 0 就是为了解决这个。
2. **约定的实机体感**：进某个 NPC 的聊天，说"明天下午在图书馆等我"（他若白天在图书馆就会答应），回消息里应出现"说好了/我在图书馆"这类承诺；第二天到点走到图书馆，应收到"我到了，在「图书馆」。"，底栏出红点。说"明天下午在食堂等我"而他有安排时，应回一句走不开 + 他那时真在哪。