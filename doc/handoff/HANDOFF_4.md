# RainingTrace / 雨迹 — 开发进度交接（定位闭环 + 世界状态层 + 采集线）

> 更新时间：2026-09-19（新对话开工前请先读本文件 + doc/02_AGENTS.md + GDD_v3.md）
> 上一版：HANDOFF_3.md（迷雾/轨迹/地点/记忆/筛选闭环 + AR 调研结论；其中"仅前台定位"等描述已被本版修正）
> 本版覆盖：**定位**（按天轨迹+日历、后台低频记录、回前台补算迷雾）、**世界状态层**（时间/天气/季节 → 产出条件）、**采集线**（动作统一 + 条件产出 + 自然资源点）

## 1. 当前一句话状态

四条线都已真机验收闭环：**地图**（迷雾/轨迹/地点/记忆/筛选）、**记忆**（日记/语音/多图/在地图查看）、**定位**（按天轨迹+日历、显式开关的后台低频记录、回前台增量补算迷雾）、**世界状态 → 产出**（天气/时段/季节影响观察与采集）。
单测 **140 全绿**；Room **v4**（schema 4.json 已导出）。AR 仍阶段封存（见 HANDOFF_3 §7）。

**下一步尚未排期**，候选与依赖关系见 §7。NPC 骨架已确定排在采集之后，用户明确说"先缓一缓"。

## 2. 本次会话完成的内容

### A. 定位线（P0 打磨）

1. **轨迹按天 + 日历**：日记页加「记忆 / 轨迹」页签；轨迹页是月历（有轨迹的日子实心圆）→ 选中某天看摘要（距离/起止/点数）→「在地图查看」。
   - SQL 侧按本地日分桶取汇总（`(ts + 时区偏移) / 86400000`），不加载点；选中那天才加载算距离。
   - 跨屏沿用一次性请求模式：新增 `TrackDayFocusRequest`（与 `MemoryFocusRequest` 同构），不用导航参数。
2. **后台低频记录（显式开关）**：`TrackRecordingService`（location 类型前台服务），**服务内只做「定位 → 去噪 → 写 track_points」**，不碰迷雾/渲染/地点/世界状态。常驻通知 + 「停止记录」动作。
3. **前台闸门（这条比服务本身更重要）**：`AppForegroundState`（Application 的 ActivityLifecycleCallbacks，按 started/stopped 计数）。`MapViewModel` 的定位处理流按前台过滤——**改造前，进程在后台也会跑去噪、开雾、写 exploration_cells、MapLibre 渲染、查附近地点**。`WorldStatusViewModel` 也改由订阅驱动，不再自己轮询。
4. **回前台增量补算迷雾**：后台只写轨迹点，迷雾是轨迹点的投影，所以回前台按"水位"（DataStore `fog_watermark_ms`）把新增点 reveal 一次即可；幂等，水位同时推到 now，避免每次重扫一整天。
5. **采集节奏可调**：`LocationCadenceController.setPassiveIntervalMs()`；前台 5s，后台按用户配置（30s/1m/2m/5m），夜间窗口外 15 分钟极稀疏档且**不写任何点**。

### B. 世界状态层（GDD §07）

1. **`WorldState` 快照**：instant / localDate / minuteOfDay / `timeOfDay` / weather / `season?` / `holiday?`；`deriveWorldState()` 纯函数；时段分界 05/08/17/20 点。
2. **`WorldStateProvider`**：`state: StateFlow`（给 UI，`WhileSubscribed` + 30s tick，没人看就停）与 `current()`（给玩法判定，按此刻现算，避免读到过期 tick）。
3. **天气从"拉一次"改成流**：`WeatherProvider.weather: StateFlow`；`WeatherKind` 自带 `isRain`；`weatherPreset()` 让假数据内部自洽；`MutableWeatherProvider` 只给调试。
4. **条件模型**：`WorldCondition` = WeatherIn / TimeOfDayIn / SeasonIn / BetweenMinutes（支持跨零点）/ All。纯函数、可枚举化，为将来的开发者编辑器铺路。
5. **产出规则表**：`ResourceYieldRule(id, resourceId, action, placeType?, conditions, amount, cooldownMs)`。
6. **动作统一（还债）**：`ObservePlaceUseCase` → `PerformPlaceActionUseCase(action)`，观察/采集共用前置+结算+冷却+留档一份实现；`selectRule()` 是唯一判定入口，`preview()` 与实际结算共用它，**所以预览不会和结算漂移**（有测试断言两者一致）。
7. **记忆/足迹落档世界状态**：`memories` 加 `weatherKind`/`season`（Room v3→v4 显式迁移）；`FootprintEvent.payload` 补 weather/timeOfDay/season/ruleId/action。
8. **调试区**：设置页「世界状态（调试）」= 天气(7) → 时段(自动+4) → 季节(未定+4)。

### C. 采集线（GDD §09）

1. **资源目录 2 → 18 条**，类别补齐 GDD §09 有但代码里缺的**文化 / 异常**。
2. **规则 2 → 21 条**，含世界状态限定：雨天湖边碎片、雨夜镜月鱼影（异常）、秋日松果、春日花瓣、黎明露珠、冬+雪霜纹、夜空水声、雨后菌丛 ×2、秋天果林 ×2。
3. **地点 6 → 9 个**：新增 3 个**自然资源点**（果林 / 浆果丛 / 菌丛），只给采集动作。
4. **地点卡**：动作竖排（按钮 + 此刻产出提示），可扩展。
5. **图鉴**：已收集条目显示「首次 M月d日」（`firstAcquiredAtEpochMs` 早在存，只是没露出来）。

## 3. 关键约定（仍然有效）

- 手写 DI（AppContainer），不用 Hilt；Compose stateless + UiState；domain 禁 Android SDK。
- 外部 SDK 全走 adapter/interface；所有时间注入 WorldClock；时区统一 `core/time/WORLD_ZONE`（`TRACK_ZONE` 就是它）。
- 单测：`.\gradlew.bat :app:testDebugUnitTest`（140 全绿）。
- **真机：Honor 100（MAA-AN00）**；adb 授权已解决，可直接 install。
- **复杂实机交互由用户手动测试并回报**；AI 只做构建/安装/启动/单测/低阶 logcat（adb 模拟点击在 MagicOS 上不可靠，别指望它验 UI）。
- 世界原点仍是北湖参考坐标 39.7326,116.1712（待真机校准）；cellSize 默认 40m。
- **新增 PlaceType 会自动多一个地图图标层**（渲染层按 `PlaceType.entries` 遍历建层），但必须在 `placeStyle()` 里补配色/字形，否则少一层。

## 4. 数据与设置现状

- **Room v4**：`track_points`（ts 索引）/ `exploration_cells` / `footprint_events` / `memories`（+`audioRef`, +**`weatherKind`/`season`**）/ `inventory_items`。
  - v1→v2 destructive（历史）；v2→v3、**v3→v4 都是显式 Migration**。改 schema 必须写 Migration + 同步 schema 导出。
- **DataStore `rt_settings`**：`grid_level`、`location_mode`、`show_memories`、`memory_time`、`tracking_enabled`、`tracking_bg_interval`、`tracking_daytime_only`、`tracking_day_window`、**`fog_watermark_ms`**、**`hidden_place_types`**（新）、`shown_place_types`（旧键，只读一次做反算）。
- **Manifest**：FINE/COARSE/BACKGROUND 定位、CAMERA、RECORD_AUDIO、INTERNET、POST_NOTIFICATIONS、**FOREGROUND_SERVICE**、**FOREGROUND_SERVICE_LOCATION**；`TrackRecordingService` 声明 `foregroundServiceType="location"`。
- 记忆形态：`mediaRefs`（多张，Char(0) 分隔）、`audioRef`（可空）、`weather`/`season`（可空，老数据为 null）。

## 5. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
# 检查迁移/Room/崩溃/地图/记录服务：
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -v time | Select-String "Room|Migration|FATAL|MapLibreAdapter|TrackRecordingService"
# 单测计数：app\build\test-results\testDebugUnitTest\*.xml
```

## 6. 本次的设计取舍与提醒（下个 AI 别推翻重来）

**定位**
- **后台只写库这一条是硬约束**：服务里不碰迷雾/渲染/地点/世界状态，靠"回前台按水位补算"兑现一致性。别为了省事把 `MapViewModel.onLocationFix` 的逻辑搬进服务。
- **前台记录仍由地图侧负责**（地图 VM 跨 Tab 存活），所以"记录我的足迹"开关的语义是**"熄屏/切走后继续记"**，不是"唯一记录开关"。开关关掉时，前台照样记。
- 白天窗口**只约束后台**；你人在前台看地图时不受限。
- 单位换算：采集节奏切档会重启定位请求；`AndroidLocationProvider` 记住被动档，切模式（Fake/GPS）时不会丢。

**世界状态**
- **天气必须是 StateFlow**：天气"变化"本身就是玩法信号；改回"每次拉一次"会让 UI 回到轮询、也让"雨天触发"没有事件源。
- **`SeasonIn` 在季节未确定（null）时一律不满足**——不给"猜"的机会。季节与时段**故意不推导**：季节口径（节气 vs 月份）未定，时段覆盖只换时段判断、`minuteOfDay` 保持真实时间（否则会篡改精确时间窗条件）。
- 规则优先级的规则是 `conditions.size*2 + 绑定了地点类型?1:0`，**并列时按 rule id 取字典序最后者**（确定性优先）。**并列是内容设计的信号**，加内容时应避免同一条件组合下出现两条同权规则。
- 冷却按 **(地点, 规则)** 记，真相在 footprint 事件里 → 罕见机会不会被保底规则挡住；重启不重置。

**采集 / 资源点**
- **一个动作一个入口**：再加"钓鱼/交易/休息"只需加动作类型 + 规则，**不要复制结算逻辑**。
- **观察 120m / 采集 60m**：采集要走到跟前。
- **产出预览不含距离判断**（那是站位问题，卡片已显示距离），所以会出现"预览有产出但点下去提示再走近"——这是刻意的。

**资源点（重要设计结论，别另起炉灶）**
- **资源点做成 `Place` 是正确的，不是妥协**：本作里 `Place` 的语义就是"一个坐标 + 可做动作 + 条件产出"，资源点完全符合。分组挂在 `PlaceType.category`（PLACE/RESOURCE）上，**不在 Place 上再存一份**。
- **真正要补的不是新实体，而是两条生命周期差异**：来源（人配置 vs 规则+种子生成）与生存期（常驻 vs spawn/expire）。将来只需加 `origin` / `expiresAtEpochMs`，**别引入 ResourceNode 平行实体**——那会让渲染、筛选、距离、规则、冷却全维护两遍。
- **三个必须处理的坑**（做 spawn 之前先想清楚）：
  1. **附近列表会被淹没** → 已处理：资源点不进"附近地点"列表，靠地图图标点选。
  2. **图标汤 + 开图泄露** → 已处理一半：**未揭示的资源点完全不画**（人文地点仍是灰色 `?`）。**按 zoom 阈值显示还没做**，等点数上几十个必须补。
  3. **生成位置必须受现实约束**（别落在马路上/水里，GDD §22）→ **未做**，这是 spawn 真正的成本，属内容/数据问题而非技术。
- **刷新机制两条路未定**：确定性重算（seed = 日期+格+世界状态，不落库、天然可复现，`SeededRandomSource` 就是为它留的）vs 落库 spawn 表（灵活、可手工压点，多一张表+迁移）。倾向先确定性重算 + 复用 footprint 记录采集状态（"枯竭→再生"就是现成的每规则冷却）。

**美术**
- 现在的地图图标/缩略图都是**文字或色块占位**，架构上不需要任何预留：配色+字形集中在 `placeStyle(type)` 一个函数，换美术 = 换位图 + 改这一个函数。
- **替换时机有依赖关系**：等"同时可见标记 > 7~9 个"（短时记忆容量）就该动手，所以**在资源点随机刷新之前**先把地图图标换掉，否则几十个点全用文字图标会彻底失效。

**种田 / 建造（将来）**
- **世界表达层必须复用现有基础**（坐标+动作+规则结算），**绝不要另开一套"农场系统"**；宿舍已经是个 `Place`。
- **但存储与状态层必须新开**：`Place` 是只读配置，种植格是"玩家拥有的可变状态 + 生长状态机"（种下时间/水分/成熟阶段/枯萎），需要新表 + 显式迁移。新子域**对外仍实现与 `Place` 相同的契约**。
- 时机：P1 做家园/宿舍（GDD §12）时第一次真遇到，**那时定表结构最划算**，现在别预设。
- **别把 `GridManager` 的六边格当地块用**——那是战争迷雾的表现网格，语义不同。

## 7. NEXT（未排期，开工前先确认）

**P0 打磨剩余（按价值/依赖排序）**

1. **NPC 骨架**（用户已定：排在采集之后；上一轮说"缓一缓"）。范围待定，最小版 = NPC 数据（名/一句话/作息时段/所在地点）+ 地图按时段出现 + 只读卡片 + 首次遇见记入足迹。世界状态（时段/天气/季节）已就绪，可以直接挂作息与出现条件。
2. **资源点随机刷新（spawn）**：先定刷新密度与位置约束口径，再选确定性 seed / 落库两条路（§6）。配套：zoom 阈值显示。
3. **季节推导口径**：节气（立春/立夏/立秋/立冬）还是月份？定了之后接上推导（现在的调试开关可以保留成开发者模式能力）。
4. **地图图标/缩略图换美术**（§6 的依赖关系：排在 spawn 之前）。
5. **真实天气 API**：换一个 `WeatherProvider` 实现即可，条件与产出不用改。
6. **定位体验细节**：GPS 信号质量指示；Fused 在 MagicOS 省电下回调稀疏的兜底；「始终允许」权限引导已有入口。

**P1 主干（doc/03 任务树）**：Supabase 同步、真实天气、NPC、家园/宿舍、种植制作、轻经济。

**文档债务**：`doc/06_地图_定位_AR专项.md`、`doc/11_MVP_首个可玩切片.md`、`HANDOFF_1/2/3` 里关于"只前台定位""产出只有观察记录"的描述已过时；本文件优先级最高，冲突以本文件为准。

## 8. 坑/风险清单（累积，别再踩）

**地图渲染**
- MapLibre **不要用数据驱动 `match` 做 icon-image / circle-color**（分支标签唯一性校验会让整层失败）；用"每类型静态层 + `eq` 过滤"。
- 新增 `PlaceType` 会自动建层，但**必须在 `placeStyle()` 里补配色/字形**（有单测兜底）。
- style 异步换代期 `getSourceAs` 抛 IllegalStateException → `safeSource` + pending 重放；同一地图不重复 `setStyle`；MapView 生命周期手动驱动。
- 地点图标 GeoJSON 属性与 filter 都用**小写枚举名**，大小写不一致会导致图标全灰或消失。
- 切 Tab 后靠 `VM.refresh()` + attach 代际 pending 重放；聚焦环要在 refresh 里补画。

**数据 / 迁移**
- **改 schema 必须写 Migration**（v3→v4 已示范）；新增列同步 schema 导出与仓储映射；手动 `ALTER` 与 schema json 不一致会崩。
- **筛选持久化只存"隐藏集合"**：存显示集合会让以后新增的类型在老安装里被莫名藏掉。
- `LIKE :prefix` 需 SQL 侧拼 `%`；迷雾挖洞已证伪用分块掩膜。

**定位 / 服务**
- **Android 14 起禁止从后台启动 location 前台服务** → 服务只能在应用可见时拉起（`AppContainer` 的监督者已按此实现）；`startForeground` 失败必须立刻 `stopSelf`（否则框架判超时崩溃）。
- MagicOS 后台限制严格：**需要用户手动放行**（允许后台活动/自启动 + 电池白名单）。熄屏后轨迹断成几段基本就是这个原因，不是代码问题。
- 相机离开组合（含"摄像 Tab 内切到 AR 模式"）必须显式 unbind CameraX；语音离开页面要停录音/回放。
- `MediaRecorder.stop()` 未 start 或过短会抛异常，`AndroidAudioNoteController` 已 runCatching 包裹并丢短录音。

**测试**
- `SystemWorldStateProvider` 用 `stateIn` 会常驻收集协程：测试里挂 `backgroundScope`，否则 `runTest` 报 `UncompletedCoroutinesError`。

## 9. 代码地图（要点）

```
core/
  common/AppContainer（世界状态/记录服务监督者/产出规则/CLI 式 DI）
  lifecycle/AppForegroundState（前台闸门：地图与服务的省电边界）
  time/WorldClock + WORLD_ZONE
  ui/ MoodLabels、WorldLabels（天气/时段/季节文案）、LocalImage、AudioNoteChip
domain/
  world/ WorldState(+Provider: System/Fake)、WeatherState(+StateFlow/Mutable/preset)、
         SeasonSource、TimeOfDaySource、WorldCondition、ResourceYieldRule(+Catalog)
  exploration/ PerformPlaceActionUseCase（观察/采集统一 + preview）、ExplorationState
  inventory/ ResourceDefinition（18 条 + 类别）、InventoryState（AddItem）
  map/ Place(+PlaceCategory/PlaceType)、MapVisuals(placeStyle、placeVisualsFor)、GridManager
  track/ RecordTrackPoint、RevealFogFromPoint、TrackDay(+距离/相机)、TrackDayFocusRequest
  memory/ CreateMemoryUseCase（落档天气/季节）、MemoryTimeline、MemoryFocusRequest
  footprint/ FootprintEvent（payload 承载冷却与世界状态）
  settings/ MapFilterSettings(+shownPlaceTypesFrom)、TrackingSettings
data/
  local/ Room v4 + MIGRATION_2_3/3_4、Daos（含 daySummaries 按日分桶）、Entities
  repository/ FakePlaceRepository（9 个地点：6 人文 + 3 资源点）、RoomRepositories
  settings/ DataStoreSettingsRepository（隐藏集合语义、水位、记录设置）
platform/
  location/ AndroidLocationProvider（可调档）、SwitchableLocationProvider、
            TrackRecordingService、AndroidTrackingController
  map/ MapLibreAdapter（每类型静态图标层 + 记忆心情层 + 空洞可达）
  audio/ camera/ ar/（AR 阶段封存）
feature/
  map/ MapScreen|MapViewModel（筛选/聚焦/产出预览/记录中提示）、WorldStatusViewModel
  journal/ JournalScreen|JournalViewModel|JournalRoute（记忆/轨迹页签 + 日历）
  inventory/（图鉴：类别/稀有度/首次获得）、settings/（含世界状态调试区）
  shell/ RainingTraceApp（Tab + 跨屏聚焦请求）
```

## 10. 待你确认的一件事

自然资源点的**图标目视确认**还没做：我在 Fake 模式下用 `adb shell input tap` 想把玩家移到果林附近，模拟点击在这台机器上没生效（探索格数没变），所以三个新图标（果林草绿"果" / 浆果丛紫红"莓" / 菌丛褐"菌"）我只验证了"层会建、配色有值"，没看到实际画面。你走到湖心花园北侧那片确认一下即可。