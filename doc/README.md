# RainingTrace / 雨迹 — V3 Vibe Coding 文档包

> 文档版本：V3-VibeCoding-1.0  
> 对应产品 GDD：RainingTrace / 雨迹 GDD V3  
> 文档日期：2026-09-12

---

## 1. 文档地图

| 文件 | 用途 |
|---|---|
| `01_技术栈_架构与ADR.md` | 技术栈、模块、架构决策 |
| `02_AGENTS.md` | 放进仓库根目录，作为 AI coding agent 的项目宪法 |
| `03_开发阶段_任务树与切片.md` | 从空项目到 MVP 的任务拆解 |
| `05_领域模型与数据契约.md` | 游戏领域模型、事件模型、API 契约原则 |
| `06_地图_定位_AR专项.md` | Map/Location/AR 专项约束 |
| `08_本地开发_环境变量_运行手册.md` | 环境搭建与故障排查 |
| `12_AR实现路径与阶段规划.md` | AR 的两条技术路线、精度事实、素材需求与阶段划分 |
| `templates/` | Issue、ADR、Feature Spec 模板 |

---

## 2. 技术栈总览

### 客户端

- Android-first
- Kotlin
- Jetpack Compose + Material 3
- Android Architecture：单向数据流 + ViewModel + Repository/UseCase 分层
- Coroutines / Flow
- Room：本地缓存、离线队列、探索/足迹/记忆本地数据
- DataStore：小型设置、功能开关、本地偏好
- CameraX：相机采集
- MapLibre Native Android：地图渲染
- ARCore：AR 能力
- WorkManager：可靠的后台同步/上传/清理任务

### 地图与空间

- MapLibre Native Android
- 第一阶段允许使用托管矢量瓦片服务进行原型
- 空间数据库：PostgreSQL + PostGIS
- 地图业务坐标统一使用 WGS84；游戏世界层避免直接把经纬度写入 UI 业务逻辑
- 六边形网格：业务层使用独立 `HexCellId` / axial coordinate，不依赖地图 SDK 的 annotation 当作游戏状态

### 后端

- Supabase
  - Postgres
  - PostGIS
  - Auth
  - Storage
  - Realtime（仅在真正需要时）
  - Edge Functions / TypeScript
- Server authoritative：库存、货币、奖励、探索结算、公共事件必须服务端确认
- 客户端可以乐观 UI，但不能成为最终事实源

### AI

- AI 只通过 `AiGateway` / `NarrativeService` 进入产品
- MVP 不要求 AI 驱动核心经济和世界状态
- AI 首先用于：NPC 对话增强、日志整理、事件摘要、内容生成辅助
- 所有 AI 输出都必须结构化后进入游戏规则层，不能直接执行数据库写操作

### CI/CD

- GitHub
- GitHub Actions
- Kotlin static analysis / formatting / unit test / instrumentation test
- Preview/Debug channel 与 Production channel 隔离

---

## 3. 最重要的工程原则

### 原则 A：先做“可玩闭环”，再做“完整系统”

第一阶段只需要证明：

> 走到一个真实地点 → 地图/迷雾变化 → 发现地点 → 做一个动作 → 获得资源 → 留下一条足迹/记忆 → 下一次回来世界记得这件事。

如果这个闭环不好玩，继续加 NPC、经济、AR、AI 都是在放大错误。

### 原则 B：现实 SDK 都必须被隔离

禁止在 UI 里直接：

```kotlin
LocationManager(...)
MapView(...)
ArSession(...)
SupabaseClient(...)
```

正确方向：

```text
UI
 ↓
UseCase / ViewModel
 ↓
Domain Interface
 ↓
Platform Adapter
 ↓
Android / Map / AR / Backend SDK
```

### 原则 C：任何 P0 功能必须可以 Fake

例如：

- `LocationProvider` → `FakeLocationProvider`
- `WeatherApi` → `FakeWeatherProvider`（真实实现是 Open-Meteo，手动覆盖即可离线）
- `WorldClock` → `FakeWorldClock`
- `MapRepository` → `FakeMapRepository`
- `EventRepository` → `InMemoryEventRepository`
- `CameraCapture` → `FakeCameraCapture`

**内容不用 Fake**：地点/NPC/台词本来就在 `assets/content/*.json` 里随包发布，
没有网络也是完整的。所以 P0 的"没有 GPS、没有网络也能开发"现在是真的成立。

这样 AI 可以在没有 GPS、没有 AR、没有网络的情况下写和测核心逻辑。

### 原则 D：不要让 AI “顺手重构”

Prompt 中明确：

> 只完成当前任务；不要进行未请求的重构；发现架构问题时先报告；不要为了“更优雅”修改既有公共接口。

## 4. V3 的核心开发顺序

```text
P0. Domain Model
    ↓
P0. Fake World
    ↓
P0. Map + Hex Fog
    ↓
P0. Location + Footprint
    ↓
P0. Place Action
    ↓
P0. Resource / Inventory
    ↓
P0. Memory
    ↓
P0. Camera / Snapshot
    ↓
P0. First AR placement
    ↓
P1. Server sync
    ↓
P1. Seeded World Events
    ↓
P1. NPC skeleton
    ↓
P1. Home / inventory / planting
    ↓
P2. Dynamic world
    ↓
P2. Social traces
    ↓
P2. World Director
    ↓
P3. Full Geospatial AR + AI world
```
