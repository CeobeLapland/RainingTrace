# 资源点随机刷新（spawn）

## Context

地图是**真实世界地图**，所以"随机撒点"会撒出"大马路上的苹果树"。项目自己在 [HANDOFF_4.md](file:///e:/DreamingPath/RainingTrace/doc/handoff/HANDOFF_4.md) §6 已给出结论：位置约束是 spawn 真正的成本，**属内容/数据问题而非技术**；资源点就做成 `Place`（不引入 `ResourceNode` 平行实体）；给 `Place` 加 `origin` 与 `expiresAtEpochMs`；倾向**确定性重算（不落库）**+ 复用 footprint 冷却做"枯竭→再生"。

本次口径（用户已定）：**候选点 + 随机偏移**——人在 JSON 里给候选坐标，实际位置在该点半径内偏移；距离、时间都可配置。范围 = 完整版。

产出：`assets/content/spawn_rules.json`（第 9 类内容）+ 确定性 planner + 仓储合成层 + 渲染/筛选修正 + zoom 阈值。**机制在代码，口径在 JSON**，与 `yield_rules.json` 同构。

---

## 一、数据口径：`spawn_rules.json`

```json
{
  "schemaVersion": 1,
  "removedIds": [],
  "entries": [
    {
      "id": "spawn.berry_bush_campus",
      "name": "浆果丛",
      "placeType": "BERRY_BUSH",
      "description": "小径旁的一丛灌木，走过去顺手就能摘几颗。",
      "spots": [
        { "lat": 39.7341, "lng": 116.1726, "radiusMeters": 18 },
        { "lat": 39.7335, "lng": 116.1702, "radiusMeters": 12 }
      ],
      "perDay": 2,
      "lifetimeMinutes": 1440,
      "conditions": [
        { "type": "seasonIn", "seasons": ["SUMMER", "AUTUMN"] }
      ]
    }
  ]
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | ✅ | 唯一，`spawn.` 前缀 |
| `name` | ✅ | 地点名（`PlaceType.label()` 在 core/ui，domain 不能用，所以写进 JSON） |
| `placeType` | ✅ | 复用现有 `PlaceType`，JSON 只能引用不能新增 |
| `description` | ❌ | 默认空 |
| `actions` | ❌ | 缺省用 [defaultActionsFor(type)](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/map/PlaceDraft.kt#L25-L67)——和"记点"同一口径，不重复维护 |
| `spots` | ✅ | 候选点：`lat`/`lng`/`radiusMeters`（>0）。**这就是"不刷到马路上"的保证**：位置只能来自你给的点附近 |
| `perDay` | ✅ | 今天激活几个候选点（≤ `spots.size`，超出记 WARN 并夹紧） |
| `lifetimeMinutes` | ❌ | 默认 `1440`（整天）。小于一天时，当天会随机一个出现时刻 |
| `conditions` | ❌ | 复用 `WorldCondition`：`seasonIn`/`weatherIn`/`timeOfDayIn`/`betweenMinutes`/`all`/`not` |

**产出不在这里配**：刷出来的点照样吃 `yield_rules.json`（按 `placeType` + `action` 匹配）。所以校验里加一条 WARN：**该规则的 `placeType` 若没有任何产出规则，就是个哑点**。

---

## 二、确定性算法（`domain/spawn/`，纯函数）

新增 `SpawnRule` / `SpawnSpot` / `SpawnRuleCatalog`，与 `planSpawns(rules, state: WorldState): List<Place>`。

关键决策（据此避免"每天瞬移、找不着、冷却失效"）：

1. **偏移是随机的，但固定**：jitter 的 seed 只用 `(ruleId, spotIndex)`，**不含日期**。同一个候选点永远偏到同一个真实坐标——玩家能记住"那棵树下有莓子"，点不会天天挪窝。
2. **只有"今天激活哪几个"随日期变**：seed = `(ruleId, localDate.toEpochDay())` 洗牌 `spots`，取前 `perDay` 个。
3. **世界状态只做门控，不参与洗牌**：否则天气一变，点全体瞬移。天气/季节/时段只通过 `conditions.allSatisfiedBy(state)` 决定"此刻能不能采"。
4. **地点 id 跨天稳定**：`place.spawn.<ruleKey>.<spotIndex>`（**不含日期**）。这样
   - footprint 冷却按 `placeId` 判（`PerformPlaceActionUseCase.onCooldown`）→ 长冷却不会被"每天换 id"重置；
   - 图鉴按 `resourceId` 聚合，不会被重复计数；
   - `placeById` 可重算解析。
5. **窗口**：`startMinute ∈ [0, 1440 - lifetimeMinutes]`，`expiresAt = 当天零点 + (startMinute + lifetime) 分钟`，**夹在当天内**（不跨零点，否则会和新实例重叠）。`lifetime = 1440` 时 startMinute 恒为 0。
6. **宽限**：`placeById` 对刚过期的点放 2 分钟宽限，避免"图标还在、点下去没反应"。
7. 确定性哈希自己实现一个小的 mixer（splitmix64 风格）放在 `domain/spawn/`，**不复用** `SeededRandomSource`（那个是用启动毫秒做 seed 的，不可复现）。

---

## 三、运行时接线

1. **`Place` 加两个字段**（[Place.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/map/Place.kt#L105-L118)）：
   `origin: PlaceOrigin = PlaceOrigin.AUTHORED`（新枚举 `AUTHORED` / `SPAWNED`）、`expiresAtEpochMs: Long? = null`。**给默认值**，所以现有 21 处测试构造点不用改；`PlaceDto` **不扩展**（spawn 点不落盘）。
2. **内容仓储**：`data/repository/LiveRepositories.kt` 加 `ContentSpawnRuleCatalog { contentStore.index.value }`（照 `ContentYieldRuleCatalog` L515）。
3. **合成层**：新增 `SpawnAwarePlaceRepository(static, spawnRules, worldStateProvider)`：
   - `all()` → **只返静态点**（`all()` 是给 NPC 作息解析用的，塞进 spawn 点会污染语义）。
   - `nearby()` → 静态 + 当天激活的 spawn，合并后按距离排序。
   - `placeById()` → 先静态，再当天 plan（含 2 分钟宽限）。
   - 在 [AppContainer.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/common/AppContainer.kt#L287-L289) 那行包一层。
4. **刷新触发**（[MapViewModel.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapViewModel.kt#L635)）：现在**没有订阅 `worldState.state`**。加两路：
   - 订阅 `worldState.state`，键取 `(localDate, weather, timeOfDay)`，变了就 `refreshPlaces`（跨天换一批 + 天气/时段门控生效需要）；
   - 一个 ~1 分钟的周期 tick（照 `runNpcTicker` 的写法，带前台门控），否则"到点出现/过期"不会自己反应。

---

## 四、渲染与筛选：**`origin` 必须参与判定**（本次最容易漏、后果最重的一处）

现在判定全挂在 `PlaceType.category` 上。而 spawn 完全可能用 `FOREST`/`HILL`/`FIELD` 这类 **PLACE** 类型（上一步刚加的），于是会出现：
- 未揭示的 spawn 点被画成灰色 `?` → **免费开图**（HANDOFF_4 §6 坑 2）；
- spawn 点挤进"附近地点"列表 → **淹没列表**（坑 1）。

改法（两处）：
- [MapVisuals.placeVisualsFor](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/map/MapVisuals.kt#L95-L112)：未揭示时**只有 `origin == AUTHORED && category == PLACE`** 才画灰 `?`，否则不画。
- [MapViewModel](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapViewModel.kt#L647-L654) 附近列表：`origin == AUTHORED && category == PLACE` 才进列表。

**zoom 阈值**：`MapLibreAdapter` L501 构造 `SymbolLayer` 时，对 `PlaceType.category == RESOURCE` 的类型 `.setMinZoom(RESOURCE_MIN_ZOOM)`（新常量，建议 16f，真机可调）。SVG/文字图标太密会糊成一片（HANDOFF_4 §6）。**已知取舍**：手配的 ORCHARD/BERRY_BUSH/MUSHROOM_PATCH 也会一起被 zoom 挡住——这本来就是想要的"清理图标汤"，但你要知情。

---

## 五、内容管线：接入第 9 类

照 `yield_rules` 平行复制，**一个都不能漏**：
- `domain/content/ContentModels.kt`：`WorldContent` 加 `spawnRules`；`ContentIndex` 加 `spawnRules` / `spawnRulesById`；**`counts` 加一行** `SPAWN_RULES to ...`；companion 加常量。
- `data/content/ContentStore.kt`：文件名常量、overlay 标签、`rebuild()` 里加一个 `loadKind(...)`、**`countsSummary()` 也要补一行**（是第二处硬编码）。
- `data/content/ContentJson.kt`：`SpawnRuleDto`（`@Serializable`，全字段带默认值）+ `decodeSpawnRuleEntries`（照 `decodeYieldRuleEntries` 的 `decodeEntityFile` + `runCatching` + `parseEnum` 套路）；`conditions` 走现成的 `decodeWorldCondition`；坐标走 `WorldCoordinate`（越界由它的 `require` 兜住并变成诊断）。
- `domain/content/ContentMerge.kt`：复用 `byId`，无需改。
- `feature/settings/SettingsScreen.kt`：`contentKindLabel` 加 `SPAWN_RULES -> "刷新规则"`（其余不用动）。
- 校验 `ContentValidator.validate`：加 `validateUniqueIds(spawnRules)` + `validSpawnRules(content, report)`——`spots` 非空、`radiusMeters > 0`、`perDay >= 1`、`perDay > spots.size` → WARN 夹紧、**该 `placeType` 无任何产出规则 → WARN（哑点）**。

---

## 六、测试

- 新增 `SpawnPlannerTest`（domain 纯函数，数据层无单测所以逻辑必须落在这里）：同一天同一结果（确定性）、偏移在半径内且**跨天不变**、`perDay` 生效且不超 `spots.size`、`conditions` 门控、`expiresAt` 落在当天内、id 稳定唯一、过期后 `placeById` 宽限可解析。
- `MapVisualsTest` 补：**未揭示的 `origin == SPAWNED` 点不画**；`origin == SPAWNED` 不进附近列表。
- `ShippedContentTest` 补：`spawn_rules.json` 零诊断 + 条数下限 ≥1 + 引用完整性（`placeType` 合法、`spots` 非空、`perDay` 合法）+ **每条规则的 `placeType` 至少能匹配到一条产出规则**。
- `ContentValidatorTest` 补一条：`perDay > spots.size` 只记 WARN、内容不丢。
- 内置内容先放 **1~2 条示例规则**（用现有 `place.bit.garden` / `place.bit.orchard` 附近的坐标），这样构建期守门和真机验收都有东西。

---

## 七、验证

1. `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 全绿（当前基线 387 tests / 0 failures）。
2. 临时脚本核对 `spawn_rules.json`：id 唯一、坐标合法、`perDay ≤ spots.size`、每条规则的 `(placeType, action)` 命中现有产出规则（跑完即删）。
3. 设置页「内容（开发者模式）」应多出「刷新规则」一行且诊断为空。
4. 真机手验：把示例规则的候选点定在你脚下附近 → 走在附近并**把那一格走开雾**（未揭示的资源点不画）→ 地图上应出现浆果丛图标 → 点开能采到东西 → 隔天（或改 `conditions`）应换一批点。
5. 结构化验证：改模拟器/手机日期跨天，确认激活点变化、且**同一个候选点的坐标不挪窝**。

---

## 八、已知取舍（做完再回来看）

- 位置精度只到"你给的候选点附近"，**没有真正的地表数据（路/水）**。要把"马路"排得更干净，要么别把候选点放路上，要么以后再加一层"禁刷区/区域标签" JSON。
- zoom 阈值按 `PlaceType.category` 生效，会连带挡住手配的 RESOURCE 点。
- 若某天 `perDay ≤ spots.size` 且候选点很少，激活集合可能连续几天重复——这是候选点数量的函数，不是 bug。