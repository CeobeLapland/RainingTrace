# NPC 第一片：地图腿骨架 + 走动（含季节节气推导）

## Context / 为什么做这个

《雨迹》P0 闭环（地图迷雾/轨迹/记忆/定位/世界状态→产出/采集）已真机验收，140 单测全绿，Room v4。下一步主线定为 **NPC**。用户对 NPC 的完整愿景分两条腿：

- **地图腿**：NPC 有作息、有信息、位置不固定、能沿路径走、可交互对话/一起玩/购物/接任务。
- **消息腿**：消息界面里 NPC 有性格/经历/好感/情绪，可主动或被动聊天，接 AI，玩家可预设。
- **桥**：消息里的约定影响地图行为（"明天在 X 等我" → NPC 真去），同样，地图里的行为也会影响消息的内容。

**本片只做地图腿的地基：NPC 领域模型 + 按作息在地图出现 + 走动 + 只读详情卡片 + 首次遇见记入足迹。** 不含消息界面、不含 AI、不含购物/任务/一起玩。

顺带先做一个小任务：**季节按节气推导**（用户已定口径），因为它独立、改动小，且能一次性点亮 4 条现有但永不触发的季节规则。

已确认决策：① 本片范围 = 骨架 + 走动；② 移动用 **路点 + 直线插值，位置是时间的纯函数**（不需要路网、不需要 A*、不需要不可通行地块、不落库）；③ AI 本片不接但领域上不堵死；④ 季节按节气推导并保留手动开关。

---

## 第一部分：季节按节气推导

**新增** `app/src/main/java/com/rainingtrace/domain/world/SolarTerm.kt`（纯 Kotlin，无 Android 依赖）：

- `enum class SolarTerm { LICHUN, LIXIA, LIQIU, LIDONG }`
- `fun solarTermDay(year: Int, term: SolarTerm): Int` —— 21 世纪通用近似公式 `day = floor(Y*0.2422 + C) - floor(Y/4)`，`Y = year % 100`；常量 立春 `3.87`(2月)、立夏 `5.52`(5月)、立秋 `7.5`(8月)、立冬 `7.438`(11月)。已知 ±1 天特例用 `EXCEPTIONS: Map<Pair<Int, SolarTerm>, Int>` 校正表兜住（如 2024 立春公式给 2/3、实际 2/4），可增量补。
- `fun seasonOf(date: LocalDate): Season` —— `date < 立春 → WINTER`；`< 立夏 → SPRING`；`< 立秋 → SUMMER`；`< 立冬 → AUTUMN`；否则 `WINTER`。用 `date.year` 取年份，跨年天然正确。

**新增** `app/src/main/java/com/rainingtrace/domain/world/DerivedSeasonSource.kt`，实现现有 `SeasonSource` 接口（`domain/world/SeasonSource.kt`）：

- `override: MutableStateFlow<Season?>`（null = 自动按节气推导）
- `season: StateFlow<Season?>` = `combine(override, ticker(60s)) { o, _ -> o ?: seasonOf(clock.now().atZone(WORLD_ZONE).toLocalDate()) }.stateIn(scope, WhileSubscribed(5min), 初值)`
- `setSeason(null)` 语义变为"回到自动推导"，接口签名不变 → `WorldCondition.SeasonIn` 一行不改。

**接口微调**：`SeasonSource` 加 `val override: StateFlow<Season?>`（设置页要渲染"自动"行）。`ManualSeasonSource` 的 `override` 返回自身，测试继续可用。

**接线**：
- `core/common/AppContainer.kt`（约 L222）：`ManualSeasonSource()` → `DerivedSeasonSource(clock, applicationScope)`。
- `feature/settings/SettingsViewModel.kt`：`canSetSeason` 保持 true；`season`（L56）现在读到的是**生效值**；新增 `seasonOverride`（读 `seasonSource.override`）。
- `feature/settings/SettingsScreen.kt`（L237-319 世界状态调试区）：文案改为"按节气（立春/立夏/立秋/立冬）推导"；"未确定"行改为"自动（按节气）"，`selected = seasonOverride == null`，点击调 `setSeason(null)`。

> ⚠️ 行为变化：接上之后 `SeasonIn` 开始返回非 null，**4 条季节规则（`domain/world/ResourceYieldRule.kt` L138 秋日松果 / L147 春日花瓣 / L166-169 冬日雪霜纹 / L262 秋天果林）会真实触发**。这是预期的，但要在提交说明里点名。

---

## 第二部分：NPC 领域模型

**新增目录** `app/src/main/java/com/rainingtrace/domain/npc/`（纯 Kotlin，禁 Android SDK）。

- `NpcProfile.kt`：
  - `NpcProfile(id: String, name: String, oneLiner: String, schedule: List<NpcScheduleEntry>)`
  - `NpcScheduleEntry(placeId: String, startMinute: Int, travelMinutes: Int, activity: String)`
  - 语义：**`startMinute` 是"到达"时刻**，条目生效区间 `[startMinute_i, startMinute_{i+1})`（按天环）；行走发生在 `[startMinute_i - travelMinutes_i, startMinute_i)`，从 `entries[i-1].place` 走到 `entries[i].place`。
  - `activity` 本片就是文案 String，**不造枚举**（避免过早抽象）。
- `NpcPresence.kt`：
  - `data class NpcPresence(npcId, coordinate, placeId, placeName, activity, walking: Boolean, progress: Double)`
  - `data class ResolvedEntry(startMinute, travelMinutes, coordinate, placeId, placeName, activity)`、`class ResolvedSchedule(entries)`
  - `fun resolveSchedule(profile: NpcProfile, places: Map<String, Place>): ResolvedSchedule` —— **引用了不存在地点的条目直接剔除**；全部剔除则该 NPC 永不出现。
  - `fun ResolvedSchedule.presenceAt(minuteOfDay: Int): NpcPresence?`
- **插值**：`domain/map/GeoMath.kt` 加 `fun WorldCoordinate.lerpTo(other: WorldCoordinate, t: Double): WorldCoordinate`，**用经纬度线性插值**。理由：校园尺度（<2km）下与 HexGrid 等距圆柱投影差异亚米级；用 HexGrid 会把 NPC 坐标绑到可切档的 `cellSize` 与 origin 上（位置随档位变，语义错误）；纯函数无需实例化 grid，单测更直接。不处理反经线。

**`presenceAt` 算法（关键，跨零点）**：把作息当**日环**。
- `i = 最后一个 startMinute <= minute 的条目`；若 `minute` 早于最早条目，则 `i = n-1`（昨天那条延续至今）。
- `d = (entries[i].startMinute - minute + 1440) % 1440`；`walking = travelMinutes > 0 && d in 1..travelMinutes`；`progress = 1 - d / travelMinutes`；否则停在本条目坐标。
- `next(i) = (i+1) % n`，最后一条走到次日第一条自然成立。
- 边界：无作息 → null；只有一条 → 恒停留 `walking=false`；落在条目正中 → 停在条目地点；`travelMinutes <= 0` → 瞬移；相邻条目 `placeId` 相同 → 不行走；`travelMinutes >= 下一条间隔` → 在 `resolveSchedule` 阶段夹紧为 `gap-1`，保证行走不吃掉整段停留。

**新增 UseCase** `NpcPresenceUseCase.kt`：`suspend fun presencesAt(state: WorldState): List<NpcPresence>`、`suspend fun presenceOf(npcId: String, state: WorldState): NpcPresence?`。依赖 `NpcRepository` + `PlaceRepository`。

**`PlaceRepository` 微调**（`domain/map/Place.kt` L64）：加 `suspend fun all(): List<Place>`；`data/repository/FakePlaceRepository.kt` 一行实现 `byId.values.toList()`。（比用超大半径 `nearby` 干净。）

---

## 第三部分：仓储与假数据

- **新增** `domain/npc/NpcRepository.kt`：`suspend fun all(): List<NpcProfile>`、`suspend fun byId(id: String): NpcProfile?`。**不提供 `nearby`** —— NPC 位置是时间函数，静态半径查询是错的。
- **新增** `data/repository/FakeNpcRepository.kt`：5 个 NPC，`placeId` 一律**引用 `FakePlaceRepository.XXX.id` 常量**（编译期绑定，防漂移）。覆盖场景：
  1. 多段作息（宿舍→图书馆→食堂→宿舍，含 2 段行走）
  2. 三段作息（黎明广场→白天食堂→夜宿舍）
  3. **单条作息**（全天北湖，恒停留）
  4. **跨零点**（22:00 花园→02:00 宿舍→06:00 花园）
  5. 挂在 3 个资源点上、travel 较长（大部分时间在路上）

**本片不需要新表、不需要 Migration**。理由：NPC 位置是纯函数不落库；作息是手工内容（与 Place 同构，将来由服务端/资产下发）；唯一要持久化的是"见过谁"，正好走 append-only 的 `footprint_events`。Room 只加一个**查询**（非 schema 变更），`RainingTraceDatabase.kt` 与 schema 导出不动。

---

## 第四部分：首次遇见记入足迹

先读 `domain/footprint/FootprintEvent.kt`、`data/repository/RoomRepositories.kt`（footprint 实现）、`data/local/Daos.kt`、`domain/exploration/PerformPlaceActionUseCase.kt`（写足迹的做法）再动手。

- `FootprintEventType` 加 `NPC_MET`。`FootprintEventEntity.eventType` 存的是 `String`（`data/local/Entities.kt` L44），**加枚举值不动 schema**。
- **去重**：`FootprintRepository` 加 `suspend fun eventsOfType(type: FootprintEventType): List<FootprintEvent>`；`FootprintDao` 加 `@Query("SELECT * FROM footprint_events WHERE eventType = :eventType")`（payload 是单列编码，SQL 无法按 key 过滤，取回后在 Kotlin 侧比 `payload["npcId"]`）。比全量 `eventsBetween(0, now)` 干净。
- **新增** `domain/npc/RecordNpcEncounterUseCase.kt`：`operator fun invoke(playerCoordinate: WorldCoordinate, presence: NpcPresence): NpcEncounterResult`，返回 `Met(npcName)` / `AlreadyMet` / `TooFar`。
  - 距离阈值 `NPC_MEET_RANGE_METERS = 30.0`（现有 OBSERVE=120 / COLLECT=60 是"看/采"，遇见是"走到跟前"，注释写明理由），用 `GeoMath.distanceMetersTo`。
  - 事件：`coordinate = playerCoordinate`（与 `PerformPlaceActionUseCase` 一致），payload = `npcId` / `placeId` / `activity` / `walking` / `distanceMeters` + `worldState.current().footprintKeys()`（沿用 `WorldState.kt` L80 口径）。
  - 走动中的 NPC 也可遇见（不设 Walking 拒绝）。
- **调用点**：`MapViewModel.onLocationFix`（约 L377）末尾 —— 已在前台闸门内、已有稳定坐标；**不放在渲染 ticker 里**。

---

## 第五部分：地图渲染与交互

沿用现有"静态 source + 固定样式 + `eq` 过滤 + 运行时生成文字占位位图"的架构（**不要用数据驱动 `match` 做 icon-image**，见 HANDOFF_4 §8 与 `MapLibreAdapter.kt` 现有注释）。

- `domain/map/MapVisuals.kt`：
  - 加 `data class NpcVisual(npcId, name, coordinate, walking)`
  - 加 `fun npcStyle(walking: Boolean): PlaceStyleSpec`（复用现成 `PlaceStyleSpec`，两种姿态两色 + 字形；**配色集中此一处**，将来换美术只改这里）
  - `enum MapLayer` 加 `NPCS`
  - `MapRendererAdapter` 加 `fun renderNpcs(npcs: List<NpcVisual>)`
- `platform/map/MapLibreAdapter.kt`：
  - 常量 `NPC_SOURCE="rt-npcs"`、`NPC_LAYER_PREFIX="rt-npc"`、`NPC_IMAGE_PREFIX="rt-npc-pin-"`、`PROP_NPC_ID="npcId"`、`PROP_NPC_NAME="npcName"`、`PROP_NPC_POSE="npcPose"`（小驼峰属性名、值为 lowercase 枚举名，与现有点选属性约定一致）
  - `installSourcesAndLayers` 加 source + 两个姿态的 `addImage(placePinBitmap(npcStyle(...)))` + 两个 `SymbolLayer` + `eq(PROP_NPC_POSE)` 过滤
  - 加 `pendingNpcs` 并在 `flushPending` 里重放（style 异步换代期的既有做法）
  - `layerIdsOf` / `clearLayer` 的 `when` 是穷尽的 —— 加 `MapLayer.NPCS` 会编译报错提醒补上，别漏
  - **点选（必须一起改）**：`handleTap` 的 query 改为 `queryRenderedFeatures(screen, *(placeLayerIds() + npcLayerIds()).toTypedArray())`，**先判 `PROP_NPC_ID` → `npcTapListener(id)` 并 return**，再判 `PROP_PLACE_ID`；新增 `fun onNpcTap(listener: (String) -> Unit)`
- `feature/map/MapScreen.kt`：`adapter.onNpcTap { viewModel.onNpcTapped(it) }`；底部卡片优先级链插入 `selectedNpc`，新增 `NpcDetailCard`（只读：名字 / 一句话 / 此刻在「placeName」/ 在做什么 / 走动中显示"正在往 X 走"）。**本片不画 NPC 地图文字名**，避免与地点名重叠、少一个图层。
- `feature/map/MapViewModel.kt`：构造加 `npcRepository` / `npcPresence` / `recordNpcEncounter`；`MapUiState` 加 `selectedNpc: NpcPresence?` 与 `selectedNpcProfile: NpcProfile?`；`onNpcTapped(id)` → `presenceOf` → `selectNpc`（同时 `selectedPlace = null`，互斥）；`clearNpcSelection()`。

---

## 第六部分：移动的刷新机制（本片最容易出错的地方）

**结论：`MapViewModel` 内加专用 NPC ticker，10s，三重门控，只写 `rt-npcs` 一个 source。**

```
// init 内一次
viewModelScope.launch {
    while (true) {
        delay(NPC_TICK_MS)                      // 10_000
        if (!foregroundState.isForeground.value) continue        // 门控①前台
        val presences = npcPresence.presencesAt(worldState.current())
        if (presences.none { it.walking && viewport?.contains(it.coordinate) == true }) continue  // 门控②有人在走且在视口内
        mapRenderer.renderNpcs(presences.map(::toVisual))         // 只写 rt-npcs
        syncSelectedNpc(presences)                                // 卡片不过期
    }
}
```

取舍说明：
- **10s**：校园相邻地点 150–300m / travel 10–20min ≈ 15–30 m/min，10s 一步 2.5–5m，zoom 16.5 下约 2–4px，视觉上"在挪动"；30s 会跳 8–15px 像瞬移。
- **不破坏省电口径**：ticker 只活在 `viewModelScope`（地图屏存活期），`foregroundState` 判断放在 `delay` 之后、任何查询之前（与 `MapViewModel.kt` L177 同一口径）；**无人在走时零渲染、不写 GeoJSON**。
- **视口门控**：复用已有 `viewport`（L135，相机 idle 更新）与 `MapViewport.contains`（`MapVisuals.kt` L99）。
- **只重渲 NPC**：`renderNpcs` 只 `setGeoJson(NPC_SOURCE)`，函数注释写死"不得触碰 cells/places/memories/track/focus"。
- **与 `onLocationFix` 的关系**：`onLocationFix` **不加** `renderNpcs`（定位密集时反复重渲 NPC 无收益）。约定 NPC source 只有三个写入点：**ticker**、**`refresh()`**（补渲染，与 L221 补 focus 环同理）、**`onViewportChanged`**（L196）末尾补一次（否则拖图后要等 ≤10s 才看到 NPC）。三者都是整份幂等 `setGeoJson`，不会互相打架。
- **已排除的替代**：复用 `WorldStateProvider.state`（30s 太粗，且把 NPC 渲染绑到世界状态订阅者上）；MapLibre 逐帧动画/AnimationPlugin（增依赖与每帧开销，违背现有静态 source 架构）。

---

## 文件清单

**新增**
- `app/src/main/java/com/rainingtrace/domain/world/SolarTerm.kt`
- `app/src/main/java/com/rainingtrace/domain/world/DerivedSeasonSource.kt`
- `app/src/main/java/com/rainingtrace/domain/npc/{NpcProfile,NpcPresence,NpcRepository,NpcPresenceUseCase,RecordNpcEncounterUseCase}.kt`
- `app/src/main/java/com/rainingtrace/data/repository/FakeNpcRepository.kt`

**改动**
- `domain/map/{MapVisuals,Place,GeoMath}.kt`
- `domain/footprint/{FootprintEvent,FootprintRepository}.kt`
- `domain/world/SeasonSource.kt`
- `data/local/Daos.kt`（FootprintDao 加一个查询）
- `data/repository/{RoomRepositories,FakePlaceRepository}.kt`
- `platform/map/MapLibreAdapter.kt`
- `feature/map/{MapViewModel,MapScreen}.kt`
- `feature/shell/RainingTraceApp.kt`（MapViewModel 构造注入新依赖）
- `feature/settings/{SettingsViewModel,SettingsScreen}.kt`
- `core/common/AppContainer.kt`

---

## 测试清单

沿用现有风格：内部 Fake 仓储 + `FakeWorldClock` + `FakeWorldStateProvider` + `runTest` + 反引号命名 + 一个 Fixture 拼装（参考 `PerformPlaceActionUseCaseTest.kt`）。

- `domain/world/SolarTermSeasonTest.kt`：立春当天=SPRING / 前一天=WINTER；立夏立秋立冬同日切换；1/1 与 12/31 均 WINTER（跨年）；闰年（2024/2028）不偏移；特例年份落校正表（2024 立春=2/4）；遍历 2000..2099 断言四节气日始终落在 2/5/8/11 月内。
- `domain/world/DerivedSeasonSourceTest.kt`：无覆盖走推导 / 覆盖优先 / 清空回到推导 / `override` 清空后为 null。
- `domain/npc/NpcScheduleTest.kt`：区间内停对应地点 / 到达前线性插值 / 中点 progress≈0.5 且坐标=两端中点 / 最后一条走到次日第一条 / 跨零点 23:50 仍在路上 / 单条作息不行走 / 空作息返回 null / travel 超间隔被夹紧 / 相邻同地点不行走 / 引用不存在地点被剔除。
- `domain/npc/NpcPresenceUseCaseTest.kt`：全部 NPC 可算出位置 / 按 `state.minuteOfDay` 取位置（非系统时间）/ 未知 id 返回 null。
- `domain/npc/RecordNpcEncounterUseCaseTest.kt`：30m 内首次写 `NPC_MET` / payload 含 npcId 与世界状态键 / 二次不写 / 超距不写 / 其它类型足迹不影响首次判定。
- `data/repository/FakeNpcRepositoryTest.kt`：**所有作息 placeId 都存在于 FakePlaceRepository**（防静默剔除）/ 至少一个 NPC 在某时刻处于行走 / id 唯一。
- `domain/map/NpcVisualsTest.kt`：行走与停留取不同 style / visual 带名字与坐标。

---

## 验证方式

1. 单测：`.\gradlew.bat :app:testDebugUnitTest`（现有 140 + 本片新增，全绿）。
2. 构建安装：`.\gradlew.bat :app:assembleDebug` → `& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk`。
3. 真机（Honor 100 / MAA-AN00）手动验收（复杂交互由用户手动测并回报，AI 只做构建/安装/启动/单测/低阶 logcat）：
   - 地图上出现 NPC 图标，与地点图标可区分；点击 NPC 弹出只读卡片，显示名字/一句话/此刻在哪个地点/在做什么。
   - 切到调试区把时段/时间调到某 NPC 的行走窗口，观察该 NPC 图标在两个地点之间**缓慢移动**（约 10s 一步）。
   - 走到某个 NPC 跟前（30m 内），确认写入一条 `NPC_MET` 足迹；再走一次不重复写。
   - 设置页世界状态调试区：季节出现"自动（按节气）"行且当前季节正确；手动选一个季节后覆盖生效；再选"自动"回到推导。用一个季节限定产出（如秋日松果）验证季节规则真的触发了。
4. logcat 观察：`& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -v time | Select-String "MapLibre|FATAL|NPC"`。

---

## 风险与坑

1. **图层与点选不一致**：新增 `rt-npc-*` 后漏改 `handleTap` 的 query → 点 NPC 无反应且**穿透成裸地图点击**（Fake 模式下 = 玩家瞬移）。必须同时改 query 列表、`layerIdsOf`、`clearLayer`。
2. **ticker 破坏省电口径**：写成无条件 `renderNpcs` 会在无人走时也每 10s 写 GeoJSON；漏 `foregroundState` 判断会后台空转。门控顺序必须是 `delay → 前台 → 查询 → "有人在走且在视口内" → 才渲染`。
3. **作息跨零点 / 首尾衔接**：`minute` 早于最早条目时必须回落到 `n-1` 条目；`travel` 与 `gap` 重叠必须夹紧，否则停留期被行走吞掉。
4. **季节覆盖优先级反转**：`SeasonSource.season` 必须返回**生效值**（推导或覆盖），否则设置页显示错；UI 需要 `override` 流才能渲染"自动"行。另外季节一旦非 null，4 条季节规则会真实触发（预期行为变化）。
5. **Fake 数据与真实 Place id 耦合**：写错 `placeId` 会被静默剔除、NPC 整天不出现且无报错 → 用"引用 `FakePlaceRepository.XXX.id` 常量" + 一条全量断言单测兜住。
6. **NPC 图标与地点图标重叠**：NPC 常驻地点上，需不同字形/颜色区分；本片不画 NPC 文字名以规避与地点名重叠。

---

## 后续切片（不在本片，仅记录路线）

- **消息腿**：`MessagesScreen` 落地（会话列表 + 聊天线程）+ `NpcRelationship`（好感/阶段）+ `NpcMood` + `NarrativeService` 接口 + 模板实现（离线可测）+ NPC 主动发消息（由 WorldState/作息驱动）+ 玩家预设。届时需新表 + v4→v5 Migration。
- **桥**：玩家消息 → 规则化意图解析（时间 + 地点）→ `NpcCommitment` → 作息覆盖 → NPC 真的去/真的在商店里多出某物。
- **之后**：真 LLM 走 `NarrativeService` 的 adapter（Supabase Edge Function 或直连 API，密钥与降级），失败走确定性 fallback；NPC 交互（对话/购物/任务/一起玩）；`NpcFocusRequest`（照 `MemoryFocusRequest` 范式，从消息页跳地图看某 NPC）。
