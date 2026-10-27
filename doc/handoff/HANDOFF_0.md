# 1

SUMMARY
- RT-DOM-001/002: HexCellId + WorldCoordinate + HexGrid（pointy-top axial、
  确定性投影、neighbors/distance/cellsWithinRadius、cube rounding）
- RT-DOM-008: WorldClock 接口 + System/Fake 实现
- RT-DOM-009: WeatherState + WeatherProvider + FakeWeatherProvider + SeededRandomSource
- RT-MAP-004/005: ExplorationState（UNKNOWN/DISCOVERED/VISITED/MEMORIZED/SPECIAL，
  状态只升不降）+ RevealNearbyCellsUseCase + MarkCellVisitedUseCase

FILES
- domain/map/{HexCellId,WorldCoordinate,HexGrid}.kt
- domain/exploration/{ExplorationState,RevealNearbyCellsUseCase}.kt
- domain/world/{WeatherState,RandomSource}.kt
- core/time/WorldClock.kt
- 3 个测试文件

TESTS
- ./gradlew :app:testDebugUnitTest → 26 个测试全部通过
- 覆盖 happy/boundary/failure：确定性、round-trip、半径公式 3r(r+1)+1、
  负半径拒绝、VISITED 不被 reveal 降级、idempotency

RISKS
- HexGrid 用等距圆柱近似（校园尺度误差 <0.1%，可接受）；cellSize=80m 是暂定值，
  等接地图时按缩放视觉再调
- 湖中心坐标 (39.7310, 116.1711) 取自校区公开资料，建议你在真机上验证后微调

NEXT
- 选项 A（推荐）：RT-DOM-003~007（Place/Resource/InventoryItem/FootprintEvent/
  MemoryNode 实体）+ Room schema，继续纯逻辑推进
- 选项 B：RT-MAP-001~003 + RT-BOOT-003（手写 DI AppContainer），第一次把
  Fake World 渲染到屏幕上——开始"看得见"，但会碰真机（MapLibre 瓦片服务需确认）



# 2

  SUMMARY
- RT-DOM-003: Place/PlaceType/PlaceActionType + PlaceRepository +
  FakePlaceRepository（预置"北湖"，坐标 39.7326/116.1712）
- RT-DOM-004: ResourceDefinition + ResourceCatalog + InMemory 目录
  （预置"观察记录"、"湖泊记忆碎片"两种 MVP 资源）
- RT-DOM-005: InventoryState + AddItem/RemoveItem UseCase
  （数量不足整笔拒绝、首次/最近获得时间戳、stack 合并）
- RT-DOM-006: FootprintEvent（append-only，存 HexCellId 而非经纬度）+ Repository 接口
- RT-DOM-007: MemoryNode（位置+时间+心情+标签+媒体）+ MemoryRepository 接口
- Room v1：exploration_cells / footprint_events / memories / inventory_items
  四张表 + DAO + RainingTraceDatabase，schema 已导出 app/schemas/1.json，KSP 验证通过

FILES
- domain/{map/Place,map/GeoMath,inventory/*,footprint/*,memory/*}.kt
- data/repository/FakePlaceRepository.kt
- data/local/{Entities,Daos,RainingTraceDatabase}.kt
- 2 个新测试文件；build.gradle.kts + libs.versions.toml 补了 coroutines-test

TESTS
- :app:testDebugUnitTest 全绿（26 + 13 = 39 个测试）
- 2 个 checkpoint：34fbe6b（domain 实体）、6d139bd（Room v1）

RISKS
- FootprintEvent.payload 目前以简单字符串编码存库，P1 同步前需定 JSON 契约
- 北湖坐标仍是公开资料参考值，真机试玩后校准

NEXT
- 选项 B（推荐）：RT-MAP-001~003 + RT-BOOT-003——手写 DI（AppContainer）+
  MapLibre 接入 + 六边形 overlay 渲染迷雾。第一次"看得见"，需要你手机连上 adb
- 或先补 RT-PLACE-001~005（地点观察 UseCase + Fake 冷却），继续纯逻辑



# 3

SUMMARY
- RT-BOOT-003: 手写 DI AppContainer（无新框架）+ RainingTraceApplication（MapLibre 初始化）
- RT-MAP-001/002/003: MapLibreAdapter——GeoJSON source + 按迷雾状态分层的
  fill 层渲染六边形；MapView 只出现在 feature/platform 层
- RT-LOC-001/002: LocationProvider 接口 + FakeLocationProvider（点击地图=移动）
- RT-MAP-005/006: MapViewModel 单向数据流（位置流→reveal/visit→渲染）+ MapScreen
- HexGrid.cellPolygon 六边形顶点
- 底图：OpenFreeMap liberty（免费矢量瓦片，无需 key；demotiles 在校园缩放无数据，已换）

FILES
- platform/map/MapLibreAdapter.kt, platform/location/FakeLocationProvider.kt
- feature/map/{MapViewModel,MapScreen}.kt, core/common/AppContainer.kt
- RainingTraceApplication.kt, MainActivity.kt, domain/map/{HexGrid,LocationProvider,MapVisuals}.kt

TESTS
- 39+1 单测全绿；assembleDebug 成功
- 真机（Honor 100）验证：地图正确落在北湖附近（致美北街可见）、
  点击移动 → 所在格 H:0:0→H:0:-1、已探索 19→33 格、玩家标记跟随 ✓

RISKS
- 第一次安装被 MagicOS 拦了两次（USB 安装确认），后续安装需要你留意手机弹窗
- MapView 生命周期靠手动驱动（Compose 包装无自动转发），已验证前后台切换路径待你实测
- OpenFreeMap 是公共服务，正式版需自建瓦片或换供应商（P1 议题）

NEXT
- 建议下一切片：RT-PLACE-001~004——地点发现（进入北湖 cell 显示地点标记）、
  点击地点 → 详情页 → 「观察」动作 → 获得观察记录 + 写 FootprintEvent（Room 落库）
- 这一步会把 P0.5/P0.6 和已完成的地图串起来，MVP 主流程就通了


# 4

SUMMARY
- RT-PLACE-001~005: 地点发现与观察动作
  · ObservePlaceUseCase：亲临范围校验(120m) + 冷却(10min) + 发奖 + 写足迹
  · 地点格标记为 SPECIAL（紫色），底部地点卡片显示名称/距离 + 「观察」按钮
  · 观察成功 → 库存 +1「观察记录」+ FootprintEvent 落库 + toast 反馈
- RT-MEM/持久化：探索状态/库存/足迹全部接 Room，重启数据保留
- 三个 Room 仓库实现（Exploration/Inventory/Footprint）
- MapLibreAdapter 新增地点标记层（renderPlaces）

FILES
- domain/exploration/{ObservePlaceUseCase,ExplorationRepository}.kt
- domain/inventory/InventoryRepository.kt
- data/repository/RoomRepositories.kt
- feature/map/{MapViewModel,MapScreen}.kt, platform/map/MapLibreAdapter.kt
- core/common/AppContainer.kt, MainActivity.kt

TESTS
- 单测全绿（新增 5 个 ObservePlaceUseCase 用例：发奖/超距/冷却/冷却后/边缘）
- 真机 4 步验证：
  ① 地图落在北湖，地点格紫色 SPECIAL + 底部"北湖 距离0m"卡片 ✓
  ② 点观察 → toast"获得观察记录×1（共1）" ✓
  ③ 杀进程重启 → 19格探索/紫色格/北湖卡片全保留 ✓
  ④ 重启后再观察 →"共2" → 库存从 Room 恢复 ✓
- checkpoint: 1cd9e35

RISKS
- 冷却是内存态，重启会重置（MVP 可接受；P1 移到服务端后天然解决）
- 观察发奖目前是客户端本地结算（P0 无后端，符合 MVP §5；P1 起走服务器权威）
- 地点格 SPECIAL 标记会在每次 refreshPlaces 时叠加，暂不清除历史 SPECIAL（内容变多后再设计"地点生命周期"）

NEXT
- MVP 主流程还剩两块拼图：
  · RT-MEM-001~005 记忆系统（拍照/心情/标签 → MemoryNode 落库 + 日记时间线）
  · RT-INV 展示：一个简单的背包/图鉴入口，让"观察记录"可见
- 建议下一刀做记忆（拍照 → 记忆节点 → 日记），这是 GDD 的核心差异化，也是 MVP 三大问题里"记忆是否有情绪价值"的验证点


# 5

SUMMARY
- 记忆切片完成：CreateMemoryUseCase（文字/照片→MemoryNode→MEMORIZED→足迹）、
  RoomMemoryRepository、CameraX 降级路径、拍/记两个屏 + 轻量导航
- 修复了你测出的切页崩溃：MapView 销毁时 detach 失效 style + 代际计数器
  丢弃过期回调（fa387f9，已装到你手机）

NEXT（等你反馈）
1. 拍/记 → 取消/返回/侧滑：应正常回地图
2. 相机预览是否出画面；若仍"相机不可用"，新版已带日志，
   我跑一条 logcat 就能拿到绑定异常