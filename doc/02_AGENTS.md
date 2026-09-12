# AGENTS.md — RainingTrace / 雨迹

## Mission

你正在开发一个 Android-first 的现实世界生活 RPG：RainingTrace / 雨迹。

核心：

> 现实世界是地图；玩家的行动是输入；时间、天气、地点与他人痕迹是世界状态；照片与记忆是持续积累的个人世界。

## Read First

开始任何任务前，按顺序读取：

1. `README.md`
2. `00_Vibe_Coding_总纲.md`
3. `01_技术栈_架构与ADR.md`
4. 本任务相关专项文档
5. 本任务的 Feature Spec / Issue

## Golden Rules

1. 不做未请求的重构。
2. 不自行引入新框架。
3. 不删除测试以通过 CI。
4. 不在 UI 保存游戏真相。
5. 所有外部 SDK 必须通过 adapter/interface 接入。
6. P0 领域逻辑必须存在 Fake implementation。
7. 钱、库存、奖励、交易最终由服务器确认。
8. 不在源码写 secret。
9. 不把原始 GPS 数据默认上传/公开。
10. 不把 AI 文字直接当成游戏规则执行。
11. 每次完成后必须运行当前任务的测试。
12. 如果任务需要架构改变，先停止并报告。

## Package Rules

- `feature/`：UI + ViewModel
- `domain/`：业务实体、UseCase、Policy、interface
- `data/`：Repository implementation、DB、DTO
- `platform/`：Android/Map/AR/Camera/Location adapter
- `core/`：通用基础能力，不包含业务规则

依赖方向：

```text
feature → domain
feature → core

data → domain
platform → domain interfaces

domain -X-> Android SDK
 domain -X-> Compose
 domain -X-> MapLibre
 domain -X-> ARCore
```

## UI Rules

- Compose screen 必须尽可能 stateless。
- UI 不直接调用 database/network/location/AR SDK。
- 一个 screen 对应明确的 UiState。
- 用户行为通过 intent/event 传入 ViewModel。
- 所有 loading/error/empty/success 都有显式状态。

## Domain Rules

优先使用纯 Kotlin：

```kotlin
class RevealNearbyCellsUseCase(
    private val grid: HexGrid,
    private val explorationRepository: ExplorationRepository,
)
```

而不是：

```kotlin
class RevealNearbyCellsUseCase(
    private val context: Context,
    private val mapboxMap: MapView,
)
```

## Testing Rules

每个 UseCase 至少有：

- happy path
- boundary case
- failure case

涉及时间：必须可注入 Clock。

涉及随机性：必须可注入 RandomSource / Seed。

涉及定位：必须可 Fake。

## Git Rules

提交格式：

```text
feat(map): reveal nearby hex cells
fix(location): ignore stale updates
refactor(memory): split media metadata mapper
 test(world): add rainy-day event fixtures
```

提交前：

```text
git diff
git status
./gradlew test
```

## Completion Report

任务结束必须回答：

```text
SUMMARY
FILES
TESTS
RISKS
NEXT
```

## Hard Stop Conditions

出现以下情况不要继续写：

- 需要新增数据库表但没有 migration 设计
- 需要修改权限模型但没有说明
- 需要新增第三方 SDK
- 发现现有领域模型无法表达需求
- 测试失败但不清楚根因
- 外部 API 的当前行为不确定

应先报告问题并给出最小方案。
