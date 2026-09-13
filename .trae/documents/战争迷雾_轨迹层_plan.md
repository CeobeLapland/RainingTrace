# 战争迷雾 + 定位轨迹层 实施计划

## Repository Research

### 用户已确认的决策

1. 旧数据**删库重来**（Room destructive migration，开发期最干净）。
2. 默认 **40m 六边形 / 到达半径 30m / 视野半径 60m**；格子大小后续可在设置切换。
3. 图层开关放**地图浮层按钮**；Fake/GPS 切换放「我的 → 设置」。
4. 四刀按顺序全做：定位层 → 迷雾战争化 → 格子可配 → 真实定位。

### 现状关键事实（已核实）

- [HexGrid](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/map/HexGrid.kt) 是纯函数、无状态几何（坐标↔格、邻居、多边形、半径枚举），可直接复用。
- 位置流在 [MapViewModel.onLocationFix](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapViewModel.kt#L121) 一进来就 `cellOf` 离散化，**原始坐标从不持久化**；[FootprintEvent](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/footprint/FootprintEvent.kt) 只存 cellId。
- 「只看到 127 格」是渲染端 `RENDER_RADIUS=6` 造成的，不是存储限制；已探索格其实在累积。
- 迷雾视觉语义反了：已揭开格画彩色半透明块、未揭开处反而是清晰底图。
- [Place](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/map/Place.kt) 已是连续坐标、观察判定已是连续距离（120m），仅 `refreshPlaces` 把地点格强写 SPECIAL 一处坏味道。
- 玩家标记画在**格中心**而非真实坐标。
- 地点层只有 CircleLayer，`placeName` 属性传了但没有 SymbolLayer，名字不显示。
- memories / footprint_events / exploration_cells 三张表的主键或列含 cellId（`H:q:r`，不含尺寸档）。
- Room v1 裸 `.build()`，无 migration 设施；schema 导出开启。
- `LocationProvider` 接口干净（updates/latest），Fake 实现完整；`play-services-location` 依赖已引入；Manifest 已声明 FINE/COARSE 权限（BACKGROUND 也有但本计划不使用、不申请）。
- DataStore 依赖已引入但尚未使用。
- doc/06 §4 要求 Raw→Filter→Stable 流水线、§6 要求轨迹不是原始 GPS dump：本计划落的是**过滤后的 Stable 点**（精度/位移/瞬移过滤），只存本地、不上传，符合 doc/06 与 GDD §22 隐私红线。
- 现有约 60 个 JVM 单测；RevealNearby / ObservePlace / CreateMemory 三个测试会随签名调整更新。

### 目标架构（三层分离）

```
LocationProvider (Fake | Android GPS)
  → RawLocationFix
  → RecordTrackPointUseCase（精度/新鲜度/位移/瞬移去噪）
  → track_points 表（Stable 点，唯一空间真相）
        ├→ 今日/区间轨迹线（LineString 渲染，可按时间筛选）
        └→ RevealFogFromPointUseCase（30m VISITED / 60m DISCOVERED，米制）
              → exploration_cells（物化投影，换格子尺寸时可整体重建）
memories / footprint_events 改存连续经纬度，不再以 cellId 为位置真相。
六边形只是迷雾的表现网格，玩家、地点、记忆都不吸附格子。
```

## Files and Modules

### 新增（domain）

- `domain/track/TrackPoint.kt`：`TrackPoint(id, timestampEpochMs, coordinate, accuracyMeters, source)`；`TrackSource { GPS, FAKE }`。
- `domain/track/TrackRepository.kt`：`append / latestPoint / between(from,to) / count`。
- `domain/track/RecordTrackPointUseCase.kt`：去噪规则（可注入 Clock）——精度差于 `MAX_ACCURACY_M=50` 丢弃；与上一点位移 `< MIN_MOVEMENT_M=8` 丢弃；时间倒退/过旧 fix 丢弃；速度 `> MAX_SPEED_MPS=35` 判瞬移丢弃；通过则落库并返回该点。
- `domain/track/RevealFogFromPointUseCase.kt`：米制半径开雾，返回受影响格集合；30m→VISITED、60m→DISCOVERED（状态只升不降，沿用现有 rank）。
- `domain/track/RebuildFogFromTrackUseCase.kt`：按轨迹点全量重算当前 grid 的迷雾（换格子尺寸时用），幂等。
- `domain/map/GridSpec.kt`：格子尺寸档位 `GridLevel`（M=40m 默认；S=25m / L=60m / XL=100m 可选）。
- `domain/settings/AppSettingsRepository.kt`：gridLevel、locationMode、showFog、showTrack 的读写接口。
- `domain/map/MapViewport.kt`：`LatLngBounds(minLat,minLng,maxLat,maxLng)`；adapter 增加视口变化回调。

### 新增（data / platform）

- `data/local/Entities.kt`：`TrackPointEntity(id,ts,lat,lng,accuracy,source)` + ts 索引；`MemoryEntity`/`FootprintEventEntity` 去 cellId、改 lat/lng 列；`ExplorationCellEntity` 主键加 level 前缀（`G:<level>:q:r`）。
- `data/local/Daos.kt`：TrackPointDao（insert、between、latest、deleteAll）；ExplorationDao 加 `byLevelPrefix` / `replaceLevel`；MemoryDao 查询改坐标维度。
- `data/local/RainingTraceDatabase.kt`：version=2 + `.fallbackToDestructiveMigration()`（删库决策）。
- `data/repository/Room*Repository.kt`：新增 RoomTrackRepository；Footprint/Memory repo 改坐标映射；Exploration repo 落库带 level 前缀、读库按当前 level。
- `data/settings/DataStoreSettingsRepository.kt`：DataStore Preferences 实现。
- `platform/location/AndroidLocationProvider.kt`：callbackFlow 包装 FusedLocationProviderClient（高精度、5s/10m）；GMS 不可用/异常降级系统 LocationManager（GPS+NETWORK）；无权限/定位中显式状态由 feature 层依据权限与流状态呈现。
- `platform/location/SwitchableLocationProvider.kt`：实现 LocationProvider，按设置 mode 在 Fake/Android 间热切换；Fake 模式额外暴露 `emit`（点击地图）。

### 修改

- `domain/map/HexGrid.kt`：新增 `cellsWithinMeters(center, radiusM)`（米制半径→六边形范围枚举）；cellId 不含 level（纯几何保持纯净）。
- `domain/map/MapVisuals.kt`：`MapLayer` 增 `TRACK`、`FOG_MASK`；adapter 增 `renderTrack(points)`、`renderFog(viewport, holes, tintedCells)`、`setLayerVisible(layer, visible)`、`onViewportChanged(listener)`。
- `domain/memory/MemoryNode.kt`、`domain/footprint/FootprintEvent.kt`：cellId → `coordinate`。
- `domain/memory/CreateMemoryUseCase.kt`：落坐标；MEMORY 格升级改为通过 RevealFog/当前 grid 现算。
- `domain/exploration/ObservePlaceUseCase.kt`：移除 grid 依赖，足迹写坐标；冷却改为查 footprint 最近事件（持久化，顺手修"重启重置"）。
- `core/common/AppContainer.kt`：grid 改为 `GridManager`（var currentGrid + level 流）；locationProvider 改 Switchable；接入 settings；HexGrid 默认 40m。
- `platform/map/MapLibreAdapter.kt`：
  - track：LineLayer（苔绿、圆角线宽 4）；
  - fog：视口大小的遮罩 Polygon（外环=视口外扩，内环=视口内已揭开六边形洞，earcut 兼容多环洞）+ 洞上 VISITED/MEMORIZED 极淡染色；
  - places：CircleLayer + SymbolLayer（地名文字）；
  - camera idle → 视口回调。
- `feature/map/MapViewModel.kt`：重写为轨迹/视口驱动——去噪落轨迹点 → 米制开雾 → 玩家标记真实坐标；相机 idle 按视口刷新 fog mask；今日轨迹查询；图层开关状态；定位中/权限/错误显式 UiState；仅 Fake 模式 tap=移动。
- `feature/map/MapScreen.kt`：右侧浮层图层开关（迷雾/轨迹）；定位状态提示；去 SPECIAL。
- `feature/profile/MeScreen.kt` + 新增 `feature/settings/SettingsScreen.kt`：定位模式（Fake/GPS）、格子档位（切换即重建迷雾）、开关说明。
- `feature/journal/JournalScreen.kt`：去掉裸 `H:q:r` 文本（位置表现留给地点刀；现有记忆坐标已落库）。
- `feature/camera/*`：跟坐标化签名微调（拍照记忆本来就持有 coordinate，改动小）。

### 切片 1 · 定位层（S1）

schema 一次定到最终形态（删库前提下最省）：TrackPoint 全套 + 去噪 UseCase + Fake 点击写轨迹 + 轨迹仓储与区间查询 + 地图今日轨迹 LineString + 轨迹开关浮层。冷却持久化随足迹坐标化一并修。

**验收**：点击地图走出折线；今日轨迹显示一条苔绿线；杀进程轨迹还在；轨迹开关可隐藏；时间区间 API 有单测。

### 切片 2 · 迷雾战争化（S2）

RevealFogFromPoint（30/60m）替换半径格 reveal；fog mask 挖洞 + 视口驱动渲染；玩家标记真实坐标；地点加名字 SymbolLayer、去 SPECIAL；迷雾开关浮层。

**验收**：未走过区域是暗雾、走到哪亮到哪（VISITED 淡绿/普通揭开无染色）；拖地图到已探索区域雾是开的、新区是暗的；北湖显示地名。

### 切片 3 · 格子可配（S3）

GridManager + 设置页格子档位；换档 = 设置落库 → grid 切换 → RebuildFogFromTrack 全量重建 → 重渲染；DataStore 接图层/定位偏好。

**验收**：切 25/40/60/100m，迷雾以相同轨迹点重新网格化，轨迹线和地点位置纹丝不动（证明不吸附格子）。

### 切片 4 · 真实定位（S4）

AndroidLocationProvider（Fused + 降级）+ Switchable + 定位权限请求流 + 定位中/被拒/弱信号状态；设置切 GPS；真实模式关闭点击移动、Fake 模式保留。

**验收（用户室外手动）**：GPS 模式下走路轨迹与迷雾实时更新；室内精度差不乱跳（去噪）；切回 Fake 点击移动仍可用。

## Dependencies and Considerations

- 不新增依赖：FusedLocation（play-services-location 已在）、DataStore preferences（已在）、Navigation Compose（已用）。
- 单测覆盖（纯 Kotlin，可注入 Clock）：RecordTrack 四个过滤分支、RevealFog 30/60m 边界与不降级、RebuildFog 幂等、`cellsWithinMeters`、冷却持久化；更新三个受签名影响的旧测试。
- 不申请后台定位：只在 App 前台记录；Manifest 里 BACKGROUND 权限保留但不触发请求（后续 P1 再议，避免应用商店敏感权限）。
- 迷雾洞多边形：外环视口外扩 ~1.5 屏、内环用已揭开格顶点环；maplibre-native 底层 earcut 支持 interior rings，不依赖 winding 方向；真机验证洞渲染，若个别机型异常，退路是遮罩分块（视口瓦片格状掩膜）。
- 性能：40m 格铺满 15×15km 理论约 12 万格，但只存/只渲染走到的；视口内格数百级，camera idle 时重算无压力；track_points 每天去噪后数百至数千点，今日线一次查询。
- 切格子档位后的旧 level 迷雾行：直接清除并重算（轨迹点才是真相，迷雾是缓存）。
- 隐私：轨迹仅本地 Room，无网络上传路径；Fake/GPS 来源入库可区分；未来公开/社交层默认私密（GDD §22）。

## Validation

- 每刀后 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 全绿再装真机。
- S1–S3 真机 Fake 走查：轨迹线、挖洞迷雾、视口拖动、地名、设置换档重建、重启持久化。
- S4 用户室外手动验收 GPS 闭环；AI 只负责安装/logcat。
- 每刀结束按 doc/02 的 SUMMARY/FILES/TESTS/RISKS/NEXT 汇报。

## Risks

- **挖洞遮罩机型兼容性**：earcut 多环洞是 maplibre-native 常用能力，风险低；已备分块掩膜退路。
- **FusedLocation 在 Honor MagicOS 的省电杀后台**：前台使用受影响小；若回调稀疏，LocationManager 降级兜底，并在 UI 显示定位质量。
- **GPS 室内漂移污染轨迹**：50m 精度阈值 + 8m 位移 + 瞬移过滤三重去噪；仍可能有少量噪点，后续可加 Douglas-Peucker（doc/06 §6），本计划不做。
- **范围蔓延**：轨迹时间筛选 UI 本计划只做「今日」（区间查询 API 先备好）； Douglas-Peucker、后台定位、轨迹日历/diary 回放均不在本次。
