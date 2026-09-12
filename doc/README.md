# RainingTrace / 雨迹 — V3 Vibe Coding 文档包

> 目标：让 AI coding agent 能够在不“画一下午然后写废”的情况下，持续、可回滚、可验证地推进《雨迹》。
>
> 文档版本：V3-VibeCoding-1.0  
> 对应产品 GDD：RainingTrace / 雨迹 GDD V3  
> 文档日期：2026-09-12

---

## 0. 这套文档解决什么问题

《雨迹》不是一个适合“先把所有功能一起写出来”的项目。它同时涉及地图、定位、相机、AR、时间天气、游戏模拟、内容系统、社交和云端数据。

V2 已经暴露了一个典型风险：

> **AI 很会写局部代码，但不知道什么不能写、什么应该先写、什么必须被验证。**

因此 V3 的 Vibe Coding 规则不是“让 AI 多写”，而是：

1. 让 AI 始终知道产品边界。
2. 让 AI 每次只修改一个可验证切片。
3. 让每一个外部能力都有适配层，不把 SDK API 到处散落。
4. 让所有重要行为都有测试或可重复的本地模拟。
5. 任何“不确定”的技术事实，都先验证再编码。
6. 允许 AI 生成代码，但不允许 AI 私自改架构。
7. 每一个功能都必须拥有关闭/降级路径。

---

## 1. 文档地图

| 文件 | 用途 | 主要读者 | 优先级 |
|---|---|---|---|
| `00_Vibe_Coding_总纲.md` | 总协作规则、边界、工作循环 | 人 + AI | P0 |
| `01_技术栈_架构与ADR.md` | 技术栈、模块、架构决策 | 人 + AI | P0 |
| `02_AGENTS.md` | 放进仓库根目录，作为 AI coding agent 的项目宪法 | AI | P0 |
| `03_开发阶段_任务树与切片.md` | 从空项目到 MVP 的任务拆解 | 人 + AI | P0 |
| `04_AI提示词_Prompt_Pack.md` | 可直接复制的开发提示词 | 人 | P0 |
| `05_领域模型与数据契约.md` | 游戏领域模型、事件模型、API 契约原则 | 人 + AI | P0 |
| `06_地图_定位_AR专项.md` | Map/Location/AR 专项约束 | AI | P0 |
| `07_测试_验收与Definition_of_Done.md` | 测试、验收、回归、DoD | 人 + AI | P0 |
| `08_本地开发_环境变量_运行手册.md` | 环境搭建与故障排查 | 人 | P0 |
| `09_版本与Git策略.md` | Commit、branch、rollback、checkpoint | 人 + AI | P1 |
| `10_风险与反模式.md` | “不要再把 V2 写废”的红线 | 人 + AI | P0 |
| `11_MVP_首个可玩切片.md` | 第一口“雨迹味”的精确目标 | 人 + AI | P0 |
| `templates/` | Issue、ADR、Feature Spec 模板 | 人 | P1 |
| `prompts/` | 按场景拆好的 prompt | 人 | P0 |

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
- `WeatherProvider` → `FakeWeatherProvider`
- `WorldClock` → `FakeWorldClock`
- `MapRepository` → `FakeMapRepository`
- `EventRepository` → `InMemoryEventRepository`
- `CameraCapture` → `FakeCameraCapture`

这样 AI 可以在没有 GPS、没有 AR、没有网络的情况下写和测核心逻辑。

### 原则 D：不要让 AI “顺手重构”

Prompt 中明确：

> 只完成当前任务；不要进行未请求的重构；发现架构问题时先报告；不要为了“更优雅”修改既有公共接口。

### 原则 E：每次修改都必须有验证

最小验证顺序：

1. 编译
2. 单元测试
3. 目标场景手测
4. 查看 Logcat / 网络 / DB
5. Git checkpoint

---

## 4. 推荐的 AI Coding 工作方式

不要说：

> “帮我把地图系统做出来。”

应该说：

> “读取 `02_AGENTS.md`、`01_技术栈_架构与ADR.md`、`06_地图_定位_AR专项.md`。本次只实现 `MapRepository` 的接口、Fake 实现以及一个显示六边形探索单元的 Compose demo。不要接真实地图 SDK，不要修改其他模块。完成后运行指定测试并报告改动文件、测试结果和剩余风险。”

每一个开发循环都应遵循：

```text
READ → PLAN → PATCH → TEST → RUN → REVIEW → CHECKPOINT
```

---

## 5. V3 的核心开发顺序

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

---

## 6. 一句话工程目标

> **让未来的 AI agent 变成一个可靠的“初级程序员”，而不是一个会不断推倒重写项目的神奇实习生。**
