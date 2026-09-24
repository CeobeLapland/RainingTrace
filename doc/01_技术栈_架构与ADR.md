# RainingTrace / 雨迹 — 技术栈、架构与 ADR

## 1. 结论先行

### 客户端：Android-first 原生 Kotlin

选择：

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel + StateFlow
- Repository + UseCase
- Coroutines
- Room
- DataStore
- CameraX
- MapLibre Native Android
- ARCore
- WorkManager

原因：地图、定位、后台任务、相机、传感器、AR 是核心，不值得为了“跨平台”在第一天牺牲平台能力。

Jetpack Compose 官方架构强调状态驱动与单向数据流；MapLibre 提供 Android Native 地图 SDK；ARCore 官方提供 Android 与 Geospatial AR 能力。citeturn819369search6turn819369search10turn765638search15

### Backend：Supabase + Postgres/PostGIS

选择：

- PostgreSQL
- PostGIS
- Supabase Auth
- Supabase Storage
- Supabase Realtime（谨慎使用）
- Supabase Edge Functions / TypeScript

PostGIS 用于空间查询和索引；Supabase 以完整 Postgres 为基础，并提供 Auth/Storage/Realtime/Functions。citeturn819369search2turn765638search6turn765638search7

### 为什么暂时不 KMP

未来可以做 Kotlin Multiplatform，但 MVP 不做。

原因：

- 当前最大风险不是代码复用，而是游戏规则正确性。
- Android 是第一目标平台。
- AR、地图、后台定位等平台能力仍然需要原生适配。
- 提前 KMP 会增加构建和依赖复杂度。

未来如果 iOS 成为确定目标，再把纯 Domain/Data 逻辑逐步抽成 KMP shared module。Kotlin 官方目前支持 Android/iOS 等多平台，并建议可从孤立的业务逻辑开始共享。citeturn819369search4

---

## 2. 总体架构

```text
┌───────────────────────────────────────────────┐
│                   UI Layer                    │
│ Compose Screens / UI State / Navigation      │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│                Presentation                    │
│ ViewModel / StateFlow / UI Event              │
└──────────────────────┬────────────────────────┘
                       │
┌──────────────────────▼────────────────────────┐
│                    Domain                      │
│ UseCases / Entities / Policies / Interfaces   │
│ WorldClock / Fog / Exploration / Memory       │
└─────────────┬───────────────────────┬──────────┘
              │                       │
┌─────────────▼───────────┐ ┌────────▼───────────┐
│ Local Data              │ │ Remote Data         │
│ Room / DataStore        │ │ Supabase / Functions │
└─────────────┬───────────┘ └────────┬────────────┘
              │                       │
┌─────────────▼───────────────────────▼──────────┐
│ Platform / External Adapters                    │
│ Location / Camera / Map / AR / Weather(Open-Meteo) / Time │
└──────────────────────────────────────────────────┘
```

---

## 3. 建议模块

初期不要拆成几十个 Gradle module；保持单 app module + 清晰 package，等边界稳定再拆。

```text
app/
  core/
    common/
    time/
    location/
    network/
    logging/
  domain/
    world/
    map/
    exploration/
    footprint/
    memory/
    inventory/
    economy/
    npc/
    quest/
    home/
    ar/
  data/
    local/
    remote/
    repository/
  platform/
    location/
    camera/
    map/
    ar/
    permissions/
  feature/
    map/
    journal/
    discovery/
    inventory/
    home/
    npc/
    ar/
    settings/
```

---

## 4. 状态设计原则

UI 不拥有游戏真相。

例如：

```kotlin
data class ExplorationUiState(
    val cells: List<HexCellViewData>,
    val playerCell: HexCellId?,
    val isSyncing: Boolean,
    val error: UiError? = null,
)
```

而不是：

```kotlin
var coins by remember { mutableStateOf(100) }
```

金币来自：

```text
Repository → UseCase → ViewModel → UI
```

---

## 5. 本地优先（Offline-first）

《雨迹》核心玩家行为不应该因为网络抖动失效。

本地可执行：

- 地图缓存
- 探索状态
- 足迹待上传队列
- 记忆草稿
- 拍照草稿
- 背包只读缓存
- 世界时间显示
- Fake/Seeded 世界事件

联网后：

- 同步玩家状态
- 上传媒体
- 获取世界事件
- 拉取公共痕迹
- 服务端结算

---

## 6. 服务器权威范围

必须服务端最终确认：

- 金币
- 多货币
- 物品数量
- 交易结果
- 稀有物品
- 任务奖励
- 世界事件奖励
- 公共玩家痕迹发布
- 社交互动计数

可以客户端先预测：

- 地图动效
- 探索动画
- UI toast
- 相机快门反馈
- 本地草稿

---

## 7. ADR-001：为什么不是 Unity-first

### 决策

MVP 不用 Unity 作为主客户端框架。

### 原因

《雨迹》第一核心是：

> 现实位置 + 地图 + 生活记录 + 轻量游戏。

不是：

> 复杂实时 3D 场景。

Unity 在后期做高沉浸 AR 当然值得考虑，但作为第一客户端会让：

- 原生权限
- 后台定位
- Android 生命周期
- Compose UI
- 本地数据库
- 系统相机

变复杂。

### 未来转 Unity 的条件

只有当：

- AR 场景变成 3D 世界主玩法
- 实时 3D 生物交互成为核心
- 移动端场景渲染成为性能瓶颈

才评估 Unity/AR Foundation。

---

## 8. ADR-002：为什么 MapLibre

选择 MapLibre Native Android 作为地图渲染层。

优点：

- 地图渲染独立于业务
- 可自定义样式
- 可使用 vector tiles
- 与六边形/自定义图层配合方便
- 后续有 iOS / Compose Multiplatform 演进空间

注意：MapLibre Compose 目前 API 仍非稳定状态，因此 Android MVP 优先使用经过验证的 Native Android API / adapter，不让业务代码直接依赖 MapLibre Compose 的具体 API。citeturn819369search8turn819369search13

---

## 9. ADR-003：为什么 PostGIS

空间查询是产品核心：

- 玩家附近资源
- 地点半径查询
- 世界区域
- 轨迹简化
- 六边形覆盖
- 公共痕迹范围

PostGIS 提供 Point/Polygon/LineString 等空间类型和空间索引能力。citeturn819369search2

---

## 10. ADR-004：为什么 Edge Functions

早期后端避免维护独立微服务。

适合的工作：

- Authenticated API
- 世界事件生成
- NPC 文本请求
- ~~第三方天气/活动数据接入~~（天气已改为**端上直连**：客户端直接调 Open-Meteo，
  绕一层 Edge Function 只会多一跳延迟与一份配额，没有任何收益。真实活动数据接入仍可放这里）
- 媒体预处理
- 服务端结算

## 关于天气数据源

**选 Open-Meteo**（`platform/weather/OpenMeteoWeatherApi.kt`）：

- 非商用免费、**无需 API key**（没有密钥就没有泄露面，也不用做密钥分发）
- 上限 10000 次/日；本项目 15 分钟拉一次 = 96 次/日
- 数据许可 CC BY 4.0 → **必须署名**，设置页「世界状态（调试）」里有一行
- `weather_code` 是标准 WMO 4677 码，映射表在 `domain/world/WmoWeatherCode.kt`

想要天气预报/历史/多日曲线时也是同一个 base URL，只是换参数。
真要换成别家，只需另写一个 `WeatherApi` 实现——玩法层与条件一个都不用改。

Supabase Edge Functions 当前以 TypeScript/Deno 为主，并支持本地开发。citeturn765638search2turn765638search13

不适合：

- 长时间运行的模拟
- 大规模批处理
- 重 CPU AI

后者未来再用 Worker/独立服务。

---

## 11. ADR-005：AR 分三阶段

### AR-0

本地平面放置。

### AR-1

地点绑定 AR，验证“现实坐标 → 虚拟对象”的体验。

### AR-2

Geospatial API/VPS，支持更强的位置绑定。

ARCore Geospatial API 可以通过设备传感器、GPS 和 Google VPS 做地理定位，并支持 WGS84、Terrain、Rooftop 等 anchor 类型。citeturn765638search3turn765638search15

不允许在 AR-0 阶段就把整个游戏绑定到 VPS。

---

## 12. 性能底线

首个 Beta 目标：

- 地图拖动不明显掉帧
- 进入地图不因加载大量 POI 卡死
- Camera/AR 进入失败时可降级
- 位置更新不会持续唤醒所有业务模块
- 数据库查询必须可解释、可索引

---

## 13. 安全底线

- Secret 不进入 APK
- 公开 anon/publishable key 不能承担管理员权限
- DB 默认拒绝，按用户开放 RLS
- 媒体路径与访问权限分开
- GPS 原始轨迹默认本地化/采样化，不默认全量公开
- 公共痕迹必须有匿名化/可见范围控制
- 位置数据必须有删除机制

## 14. 测试注意

- 我是用我的真机测试，不是模拟器，全局命令要小心！别把我的手机搞坏！
- 型号是华为Honor 100
