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