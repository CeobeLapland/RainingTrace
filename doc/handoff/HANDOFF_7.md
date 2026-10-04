# RainingTrace / 雨迹 — 开发进度交接（采集来源 + 资源点随机刷新）

> 更新时间：2026-10-04（新对话开工前请先读本文件 + `doc/02_AGENTS.md` + `GDD_v3.md`）
> 上一版：`HANDOFF_6.md`（内容外置 + 真实天气 + 定位看门狗）
> **`HANDOFF_1`~`HANDOFF_6` 已封存为历史路径**，不再修改；当前状态以本文件为准。
> 本版覆盖：**内容规模扩容**（资源 511 / 配方 246）、**地点类型与动作枚举扩容**、**产出规则铺满自然基材**、**资源点随机刷新（spawn）**
> 相关设计稿（本轮产出，已入库）：`.trae/documents/自然采集枚举与产出规则.md`、`.trae/documents/资源点随机刷新-spawn.md`

## 1. 当前一句话状态

内容从"只有人文资源能采到"变成"**115 种自然基材全部采得到**"：产出规则从 23 条铺到 **128 条**，
并给户外自然类地点补了 **10 个 `PlaceType`** 与 **6 个产出类动作**。地图上还能**按规则随机刷新资源点**
（第 9 类内容 `spawn_rules.json`：你给候选点 + 半径，程序确定性算出"今天哪几个点、在半径内偏哪儿、什么时候过期"）。
单测 **404 全绿**；Room 仍是 **v6**（本轮没动 schema）；AR 仍阶段封存（见 `HANDOFF_3 §7`）。
工作区干净，本轮已提交（`b1251e7 10-4地点内部扩展`、`65d4045 10-4随机刷新`）。

## 2. 本轮完成的内容

### A. 内容规模扩容（你手写的）

`assets/content/resources.json` 与 `recipes.json` 大幅补齐：现在 **资源 511**（NATURE 115 / CRAFT 202 /
CULTURE 75 / KNOWLEDGE 55 / MEMORY 32 / ANOMALY 32）、**配方 246**。
注意：这两份都**没有新增枚举轴**，`category`/`rarity` 全在现有枚举内。

### B. 地点类型与动作枚举扩容（代码侧）

- `PlaceType` **10 个新值**（全归 `PlaceCategory.PLACE`）：`FOREST` 林地 / `FIELD` 田地 / `POND` 池塘 /
  `WETLAND` 湿地 / `SHORE` 水边 / `HILL` 山丘 / `GREENHOUSE` 温室 / `STREET` 道路 / `PATH` 小径 / `BRIDGE` 桥。
  现在共 20 个（PLACE 17 + RESOURCE 3）。
- `PlaceActionType` **6 个新值**：`HARVEST` / `GATHER` / `FISH` / `WATER` / `EXPLORE` / `WATCH`。现在共 8 个。
- 同步点（**加枚举时必须全改，漏一处编译不过**）：
  - 新 `PlaceType` → `MapVisuals.placeStyle()`（配色 + 字形）、`core/ui/WorldLabels.PlaceType.label()`。
    `MapScreen` 里那份重复的中文名**已改成委托 `label()`**，不会再有两份拷贝。
  - 新 `PlaceActionType` → `PerformPlaceActionUseCase.rangeMetersFor()`、`MapScreen.actionLabel()`、
    `MapViewModel` 的 TOO_FAR 文案。
  - 动作距离分三档：**观察类 120m（OBSERVE/WATCH/EXPLORE）/ 动手类 60m（COLLECT/HARVEST/GATHER）/
    贴边类 30m（FISH/WATER）**。
- `PlaceDraft.defaultActionsFor(type)` 也按"手要怎么动"细分了自然类默认动作，所以**手机现场采点**出来的
  自然点直接就能采（水边给垂钓/取水，田与温室给收获）。

### C. 产出规则铺满自然基材

`assets/content/yield_rules.json` 从 23 条 → **128 条**，**115 种 NATURE 资源覆盖率 100%**
（`ShippedContentTest.每种自然基材都有产出规则` 已钉住，以后"加了资源忘配来源"会立刻红）。
映射依据 = 设计稿 `assets/temp/places_design.md` 的「关联资源」列 + 资源 `tags`；稀有度决定条件数与 `amount`。
**现有 23 条一条没动**（其中 `rule.observe.rainy_lake` 被测试硬断言）。

有一处刻意的调整：新增的几条"无条件规则"原本会抢过既有保底（把花园白天的保底从苔痕变成雏菊花苞、
果园的保底从青苹果变成树液），所以给它们加了条件（DUSK/DAWN/SPRING…）——**既有手感不变**。

### D. 资源点随机刷新（spawn，本轮最大的一件）

新增第 9 类内容 `assets/content/spawn_rules.json`，完全复用内容管线（内置 assets → `<filesDir>/content/` 覆盖层
→ `removedIds` → 诊断 → 设置页开发者面板条数 → `ShippedContentTest`）。

**数据形态**（口径细节见 `.trae/documents/资源点随机刷新-spawn.md`）：

```
{ id, name, placeType, description?, actions?,            // actions 缺省 = defaultActionsFor(placeType)
  spots: [{ lat, lng, radiusMeters }],                     // 位置只能来自你给的候选点
  perDay, lifetimeMinutes?, conditions? }                  // conditions 复用 WorldCondition
```

**三条决定性的设计（别推翻）**：
1. **偏移固定**：点内偏移的种子只用 `(规则, 候选点序号)`，**不含日期**。同一个候选点永远偏到同一个真实坐标
   ——玩家能记住"那棵树下有莓子"，点才能当路标。
2. **地点 id 跨天稳定**：`place.spawn.<规则名>.<序号>`（不含日期）。footprint 冷却按 `placeId` 判，
   带日期会让长冷却每天被重置；图鉴按 `resourceId` 聚合，也不会重复计数。
3. **世界状态只做门控、不参与挑选**：天气/季节/时段只通过 `conditions` 决定"此刻能不能采"。
   若参与洗牌，天气一变整片点会瞬移。

**确定性重算，不落库**：`seed = 混(规则 id, 当地日期)` 洗牌候选点取前 `perDay` 个；
`startMinute ∈ [0, 1440 - lifetimeMinutes]` 且窗口夹在当天内（跨零点会和新一天的实例重叠）；
过期后留 **2 分钟宽限**（地图 1 分钟刷一次，否则"图标还在、点下去没反应"）。

**接线**：
- `domain/spawn/SpawnRule.kt`（模型 + `SpawnRuleCatalog`）、`domain/spawn/SpawnPlanner.kt`（纯函数 planner）。
- `data/repository/LiveRepositories.kt` 加 `ContentSpawnRuleCatalog` 与 **`SpawnAwarePlaceRepository`**（合成层）：
  - **`all()` 只返静态点**（它是给 NPC 作息按 id 解析坐标用的，塞进会过期、会换位的点会污染语义）；
  - `nearby()` / `placeById()` 才带 spawn 点。
- `AppContainer.placeRepository` 已包一层；`MapViewModel` 加了**跨天/天气/时段变化重算** + **1 分钟 tick**
  （前台 + 有定位门控），否则"到点出现 / 到点过期"不会自己反应。

### E. 顺手修掉的两个坑

1. **`PlaceOrigin` 必须参与呈现判定**（本轮最容易漏、后果最重的一处）：原来的"未揭示画灰 `?`"只看
   `PlaceType.category`，而 spawn 完全可能用 `FOREST` 这类 PLACE 类型 → **免费开图**。现在
   `MapVisuals.placeVisualsFor` 与 `MapViewModel` 的附近列表都改成 **`origin == AUTHORED && category == PLACE`**。
   spawn 点既不画未揭示的"?"，也不进附近列表（那会淹没列表）。
2. **加工品链完整性从硬失败降级成 WARN**：以前 `ShippedContentTest` 里那条"被当作入料的加工品都能做出来"
   会在你补内容的中途直接拦构建，现在移到 `ContentValidator.warnUncraftableCraftGoods` 记一条 WARN
   （内容一条都不丢，但开发者面板里看得见）。**当前 WARN 列表是空的**。

## 3. 关键约定（仍然有效）

- 手写 DI（`AppContainer`），不用 Hilt；Compose stateless + UiState；**domain 禁 Android SDK**。
- 外部 SDK 全走 adapter/interface；所有时间注入 `WorldClock`；时区统一 `core/time/WORLD_ZONE`。
- **JSON 是内容的唯一真相**：想加内容就改 `assets/content/*.json` 或覆盖层，**不要在 Kotlin 里加常量**。
- **枚举轴（`PlaceType`/`PlaceActionType`/`NpcTopic`…）仍在代码里**：JSON 只能引用，不能新增。
- 单测：`.\gradlew.bat :app:testDebugUnitTest`（**404 全绿**）。**数据层没有单测**（无 Robolectric），
  所以新逻辑尽量放 domain 纯函数里——本轮的 `SpawnPlanner` 就是这么做的，`SpawnAwarePlaceRepository` 只是薄合成层。
- 真机：Honor 100（MAA-AN00）；`adb install -r` 可直接覆盖。**这台机器上第三方应用的 logcat 基本被裁掉**，
  实机验收只能看 UI。
- **复杂实机交互由用户手动测试并回报**；AI 只做构建/安装/启动/单测。
- 世界原点 39.7326,116.1712（待校准）；cellSize 默认 40m。
- 新增 `PlaceType` 会自动多一个地图图标层，但必须在 `placeStyle()` 补配色/字形。

## 4. 数据与设置现状

- **Room v6**（本轮未改）：`track_points` / `exploration_cells` / `footprint_events` / `memories` /
  `inventory_items` / `npc_messages` / `npc_states` / `npc_commitments`。
  **spawn 点不落库**（确定性重算），所以"枯竭→再生"直接复用 `yield_rules` 的 `cooldownMs`。
- **DataStore `rt_settings`**：与 `HANDOFF_6 §4` 相同，本轮没动。
- **内容覆盖层**：`<filesDir>/content/*.json`（现在共 9 类；`spawn_rules.json` 也能覆盖）。
- **内置内容当前条数**（设置页「内容（开发者模式）」应显示）：
  地点 **9** / 资源 **511** / 产出规则 **128** / 配方 **246** / NPC **5** / 主动规则 **10** /
  台词 key **35** / 别名 **15** / 话题 **8** / **刷新规则 2**。

## 5. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# 把一份手改的内容推进覆盖层（debug 包用 run-as）
& "D:\Android\Sdk\platform-tools\adb.exe" push .\app\src\main\assets\content\spawn_rules.json /data/local/tmp/spawn_rules.json
& "D:\Android\Sdk\platform-tools\adb.exe" shell "run-as com.rainingtrace mkdir -p files/content"
& "D:\Android\Sdk\platform-tools\adb.exe" shell "run-as com.rainingtrace sh -c 'cat /data/local/tmp/spawn_rules.json > files/content/spawn_rules.json'"
# 然后进设置页点「重新读取内容」

# 单测计数：app\build\test-results\testDebugUnitTest\*.xml
```

## 6. 本轮的设计取舍与提醒（下个 AI 别推翻重来）

**产出规则**
- **同一 `(动作, 地点)` 下同权并列不是 bug**：`chooseMostSpecific` 取 `specificity` 最大者，并列时按 rule id
  字典序取大；但**命中过的规则会进冷却、下次自动轮到下一条**，所以同一个采集点"多点几次轮着出"是设计，
  不是丢内容。几十条同权并列（如 HILL+GATHER）也照此工作。
- **别用世界状态参与 spawn 的挑选**（见 §2 D 第 3 条）。
- `specificity = 条件权重和 × 2 + (placeType 是否绑)`；`Not` 的权重是 **0**（否定条件不该抢过正向限定）。

**呈现语义**
- **判断"是不是刷出来的"只能看 `PlaceOrigin`，不能看 `PlaceCategory`**。这条已经在
  `MapVisualsTest` 里钉住（未揭示的 SPAWNED 点即使是 FOREST 类型也不画）。
- 资源点图层加了 `minZoom`（`MapLibreAdapter.RESOURCE_MARKER_MIN_ZOOM = 16f`，比默认 16.5 略低）：
  只在拉远看全局时隐藏，避免几十个点糊成图标汤。**取舍**：手配的果林/浆果丛/菌丛也会一起被挡住。

**内容管线**
- `ShippedContent.load()` **要求零诊断**（`check(report.items.isEmpty())`）——注意是**所有**诊断，不只是 ERROR。
  所以内置内容里出现任何 **WARN** 都会让所有用 `ShippedContent` 的测试一起红。给内置 `spawn_rules.json`
  加规则时要保证：`spots` 非空、`perDay ≤ spots.size`、且该 `placeType` 至少能命中一条产出规则。
- 新增一类内容要动的地方是**固定 9 处**（照 `yield_rules` 抄）：`ContentModels`（字段 + `ContentIndex` 访问器 +
  **`counts`** + companion 常量）、`ContentStore`（文件名常量 + overlay 标签 + `rebuild()` 的 `loadKind` +
  **`countsSummary()`**）、`ContentJson`（DTO + decode 入口）、`ContentValidator`、`SettingsScreen.contentKindLabel`、
  `ShippedContent`、`ShippedContentTest`。**`counts` 与 `countsSummary` 是两处独立的硬编码，都要补。**

**确定性随机**
- `SpawnPlanner` 自带的哈希**必须逐个 part 雪崩**（`avalanche(h xor part)`）。若写成"先 xor 再乘一次"，
  小 salt（循环下标 0/1/2…）只动到低位，取高 53 位时几乎不变——洗牌会退化成几乎不动。
- **别复用 `SeededRandomSource`** 做 spawn：它的 seed 是启动毫秒，不可复现。spawn 要的是"同一天同结果"。

## 7. 坑/风险清单（累积）

**内容 / 打包**
- **`app/src/main/assets/temp/` 里的 12 个文件会被打进 APK**（`build.gradle.kts` 没有 `ignoreAssetsPattern`）：
  包括 4 份 `*_design.md`、3 份工作副本 `*.json`、2 个转换脚本与 `.workbuddy/` 笔记。纯包袱，**迟早要挪出 assets/
  或加排除规则**。注意 `assets/temp/` 那份 `resources.json`/`recipes.json` **不是**运行时读的那份
  （运行时读 `assets/content/`），两份并存很容易改错文件。
- **Kotlin 注释里写 `content/*.json` 会编译失败**：Kotlin 块注释可嵌套，`/*` 会开新嵌套注释，外层永不闭合
  （报 `Unclosed comment`）。写 `content/` 下的文件名时避开 `/*`。
- **Kotlin 的 Long 十六进制字面量不能超过 `0x7FFFFFFFFFFFFFFF`**：splitmix64 / FNV 的原版常数会直接编译不过，
  要么用负数形式，要么换一个范围内的常数（本轮选了后者）。

**内容 / 迁移**
- 手动 `ALTER` 与 schema json 不一致会崩；Room 改 schema 照 `HANDOFF_5 §6`。
- `preferencesDataStore(name=...)` **同一份文件只能有一个委托**。

**天气 / 定位**（原样有效）
- Open-Meteo 免费额度"非商用 + <10k 次/日"；断网时停在**上一次成功的值**并显示"正在重试"，不说谎。
- MagicOS 后台限制严格：熄屏轨迹断段基本是系统掐的，代码修不了；看门狗只负责让它"可见"。
- Android 14 起禁止后台启动 location 前台服务；`startForeground` 失败必须立刻 `stopSelf`。
- `TrackRecordingService` 内**只写轨迹点**。

**测试**
- `stateIn`/`WhileSubscribed` 的协程在测试里挂 `backgroundScope`，否则 `runTest` 报 `UncompletedCoroutinesError`。
- 对着无限 `while(true) { delay() }` 的流**不能用 `advanceUntilIdle()`**；用 `runCurrent()` / `advanceTimeBy()`。
- `ShippedContentTest` 的数量断言都是**下限**（`>=`），所以内容变多不会红。

## 8. NEXT（未排期，开工前先确认）

**P0 打磨剩余**
1. **地图图标/缩略图换美术**（`art/pin` `art/place`，命名约定见 `domain/art/ArtPaths`）。
   现在已经放了第一张 `art/item/res.pine_cone.png`，但**四角是不透明的浅白 `(249,249,249,α=255)`**，
   渲染出来是个白方块——**要重导成透明底**（100×100 尺寸本身没问题，没有测试读图片）。
   NPC 头像同理（`NpcAvatar` + `NpcAvatarPalette`）。
2. **`places.json` 补自然采集点**：spawn 规则和产出规则都已就绪，但**新类型在 `places.json` 里一个地点都没有**，
   所以规则在真机上还是空的。两条路：a) 开发者模式**现场采点**（选 `FOREST`/`POND` 等，默认动作已配好）；
   b) 采好经纬度后批量落 `places.json`（设计稿 `assets/temp/places_design.md` 已备好 139 处 / 50 type）。
3. **定位体验继续打磨**：看门狗只做了"发现 + 一次性补救"，**没做失败后的退避，也没做提示消失条件**。
4. **`assets/temp/` 挪出打包范围**（见 §7）。

**内容线的延伸**
5. 设计稿 50 个 `type` 里还有约 40 个没进代码（建筑/室内/校外/异常四组），以及其余动作
   `TALK / REST / EAT / WORK / STUDY / CREATE / ENTER / CAMP / PERFORM / FOLLOW / WALK`。
   现在 `EXPLORE`/`WATCH` 已在代码里但自然地点基本用不到 `WATCH`，所以没给它配规则。
6. **背包与图鉴要不要分开**（你之前问过，还没定）。
7. spawn 的候选点池扩充；若要把"马路/水面"排得更干净，再加一层"禁刷区 / 区域标签"内容（目前只有候选点约束）。

**NPC 线**（`HANDOFF_5 §8` 原样有效，且现在更容易——改档案只需改 JSON）
8. 接真 LLM：换 `NpcMessageParser` / `NarrativeService` 两个实现即可。
9. NPC 交互（购物/接任务/一起走走）：属于"给地点/动作加内容"。
10. `NPC_*` 足迹的可查询化（现在 payload 单列编码、SQL 不能按 key 过滤）。

**P1 主干（`doc/03`）**：Supabase 同步、家园/宿舍、种植制作、轻经济。

**文档债务**：`HANDOFF_1`~`HANDOFF_6` 已封存为历史路径。`HANDOFF_6 §10` 里"内容条数"那行已过时
（现在见本文件 §4）。冲突以本文件为准。

## 9. 代码地图（要点，只列与本轮有关的）

```
domain/
  spawn/ SpawnRule(+SpawnSpot/SpawnRuleCatalog)、SpawnPlanner(plan/placeId + 确定性哈希)
  map/   Place(+PlaceOrigin/expiresAtEpochMs)、MapVisuals(placeStyle/placeVisualsFor 的 origin 判定)、
         PlaceDraft(defaultActionsFor 按"手要怎么动"细分)、HexGrid/GeoMath(haversine，偏移换算另算)
  exploration/ PerformPlaceActionUseCase(rangeMetersFor 三档：120/60/30)
  network/ world/ ResourceYieldRule(+Catalog)、WorldCondition、WorldState(+Provider)
  content/ ContentModels(WorldContent/ContentIndex/counts)、ContentValidator(validSpawnRules 两条 WARN)
data/
  content/ ContentStore(loadKind 新增 spawn_rules)、ContentJson(SpawnRuleDto + decodeSpawnRule)
  repository/ LiveRepositories(ContentSpawnRuleCatalog、SpawnAwarePlaceRepository)、
              ContentPlaceRepository(每次现读 index，无缓存)
platform/
  map/ MapLibreAdapter(RESOURCE 图层 minZoom；每 PlaceType 一个 SymbolLayer)
feature/
  map/ MapScreen(筛选面板两行已改横向滚动)、MapViewModel(跨天/条件重算 + 1 分钟 spawn tick)
core/common/ AppContainer(placeRepository = SpawnAwarePlaceRepository(ContentPlaceRepository, ContentSpawnRuleCatalog, worldStateProvider))
```

## 10. 待你确认的几件事

1. **内容条数**：设置页「内容（开发者模式）」应显示 §4 那十个数，且诊断为空。
2. **资源点刷新**：把示例规则的候选点定在你脚下附近 → 走在附近并**把那一格走开雾**（未揭示的资源点不画）
   → 地图上应出现浆果丛图标 → 点开能采到东西。改一次手机日期跨天，确认激活的点会换、
   但**同一个候选点的坐标不挪窝**。
3. **`minZoom` 手感**：默认缩放（16.5）下资源点应当可见；拉远到 16 以下它们会消失。觉得不合适就改
   `MapLibreAdapter.RESOURCE_MARKER_MIN_ZOOM` 一个常量。