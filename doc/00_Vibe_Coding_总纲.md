# RainingTrace / 雨迹 — V3 Vibe Coding 总纲

## 1. Vibe Coding 的核心目标

Vibe Coding 在本项目中不是“随便让 AI 写代码”，而是：

> **人负责方向、边界、优先级和验收；AI 负责局部实现、样板代码、重构建议和测试补全。**

AI 不拥有架构决策权。

---

## 2. 四层决策权

### L0 — 产品不可修改规则

由产品/GDD决定，AI 不可自行改变：

- 游戏核心体验
- 核心差异化
- 玩家数据隐私边界
- P0/P1/P2/P3 优先级
- 服务器权威数据原则
- 现实世界安全原则

### L1 — 架构决策

必须人工确认：

- 新第三方 SDK
- 新数据库
- 新通信协议
- 新核心模块
- 修改核心领域模型
- 修改存储模型
- 引入新的状态管理方式
- 删除已有抽象层

### L2 — 模块实现

AI 可以独立完成：

- 一个 ViewModel
- 一个 Repository implementation
- 一个 UseCase
- 一个 Compose screen
- 单元测试
- DTO / mapper
- UI state
- Fake implementation

### L3 — 样板代码

AI 可以高度自动化：

- boilerplate
- serialization
- test fixture
- mock/fake
- preview
- documentation
- formatter

---

## 3. 每个任务必须先形成 Feature Spec

### Feature Spec 最小字段

```yaml
feature_id: RT-XXXX
name: 功能名
priority: P0 | P1 | P2 | P3
user_story: 用户希望...
non_goals:
  - 不做...
acceptance:
  - GIVEN...
  - WHEN...
  - THEN...
data_changes:
  - ...
interfaces:
  - ...
platform_dependencies:
  - ...
rollback_plan: ...
test_plan: ...
```

---

## 4. 一个任务只解决一个问题

错误：

> “实现地图、定位、迷雾、足迹和后端同步。”

正确拆成：

1. RT-MAP-001 HexCellId
2. RT-MAP-002 HexGridProjection
3. RT-MAP-003 FogState
4. RT-MAP-004 ExplorationReducer
5. RT-LOC-001 LocationProvider
6. RT-LOC-002 LocationToCellUseCase
7. RT-TRACE-001 FootprintEvent
8. RT-SYNC-001 ExplorationSync

---

## 5. 每个任务必须产生“可检查产物”

至少一个：

- 编译成功
- 单元测试
- UI screenshot
- Logcat 输出
- JSON fixture
- DB migration
- API contract
- 演示视频（大型功能）

---

## 6. Checkpoint 规则

每完成一个可玩切片：

```bash
git status
git diff
git add .
git commit -m "feat(map): reveal nearby hex cells"
```

禁止连续 30 个未提交小修改后再一次性合并。

建议：

- 小任务：1 个任务 1 commit
- 中任务：2~5 个 commit
- 大功能：feature branch + squash merge

---

## 7. AI 输出格式

要求 AI 每次完成工作后只按下面结构汇报：

```text
SUMMARY
- 做了什么

FILES
- 改了哪些文件

TESTS
- 跑了什么
- 结果是什么

RISKS
- 还存在什么风险

NEXT
- 最合理的下一步是什么
```

避免长篇解释代码。

---

## 8. 发现问题时的处理

### 能修

直接修，前提是属于当前任务范围。

### 不属于当前范围

不要偷偷改。

报告：

```text
发现架构问题：XXX
影响：XXX
建议：在 RT-XXXX 单独处理
本任务不修改它。
```

### 技术事实不确定

必须先查官方文档/仓库，再写实现。

禁止：

> “我记得这个 API 应该这样。”

---

## 9. 禁止的 AI 行为

- 删除测试来让 CI 通过
- 删除错误日志
- 用 `!!` 隐藏空值问题
- 把真正业务逻辑塞进 Composable
- 直接操作 SDK 的 UI 类
- 复制粘贴大量相同代码
- 为了一个小需求引入新的 framework
- 未经确认修改 DB migration
- 未经确认修改权限/RLS
- 把 secret/API key 写入源码
- 让客户端决定金币、库存、奖励最终值
- 把真实 GPS 原始轨迹永久上传作为默认行为
- 为了“未来可能用到”过早引入 KMP/微服务/事件总线

---

## 10. “小而稳”规则

如果有两种实现：

A. 100 行、3 个抽象层、使用新库

B. 40 行、一个明确接口、已有技术栈

默认选择 B。

---

## 11. 何时允许 AI 大改代码

只有以下情况之一成立：

1. 编译无法恢复且架构确实错误
2. 数据模型已经证实错误
3. P0 核心循环出现不可接受的性能问题
4. 官方 SDK/API 发生破坏性变化
5. 人工明确下达“重构任务”

否则只做局部修复。

---

## 12. 项目最重要的工程原则

> **代码可以被重写，玩家数据不能轻易丢。**

因此：

- DB schema 要有 migration
- 玩家数据要版本化
- 事件要尽量 append-only
- 世界状态尽量可重算
- 外部平台数据不可直接作为游戏存档唯一来源
