# RainingTrace / 雨迹 — AI Coding Prompt Pack

以下 prompt 默认配合 `02_AGENTS.md` 使用。

> **加内容的地方是 JSON，不是 Kotlin。**
> 地点 / 资源 / 产出规则 / NPC / 主动消息规则 / 台词 / 地点别名 / 解析关键词表
> 全部住在 `app/src/main/assets/content/`，玩家覆盖层在 `<filesDir>/content/`。
> 任何"请在 Kotlin 里加一个地点/NPC/台词"的写法都是过时的——那会造出第二份真相，
> 而这正是我们已经拆掉的东西。

---

## Prompt 00 — 新任务总模板

```text
你现在是 RainingTrace / 雨迹 项目的编码代理。

先读取：
- README.md
- 00_Vibe_Coding_总纲.md
- 01_技术栈_架构与ADR.md
- 02_AGENTS.md
- <本任务专项文档>

任务 ID：<RT-XXXX>
任务目标：<一句话>

范围：
- 允许修改：<文件/模块>
- 不允许修改：<文件/模块>

Non-goals：
- <不做什么>

验收条件：
- GIVEN ... WHEN ... THEN ...

要求：
1. 先给出 5~10 行实现计划。
2. 不要先写代码后解释。
3. 不进行未请求的重构。
4. 外部 SDK 只能通过 adapter/interface。
5. P0 逻辑必须可 Fake。
6. 完成后运行相关测试。
7. 最后按 SUMMARY / FILES / TESTS / RISKS / NEXT 汇报。
```

---

## Prompt 01 — 读取项目而不是乱改

```text
先不要写代码。

请扫描项目结构，并回答：
1. 当前架构层次是什么？
2. Domain/Data/Platform/Feature 的边界是否符合 AGENTS.md？
3. 当前任务最可能修改哪些文件？
4. 是否存在与任务直接冲突的旧实现？
5. 有没有需要我确认的架构风险？

只做分析，不改文件。
```

---

## Prompt 02 — 实现一个纯 Domain UseCase

```text
仅实现 <UseCase>。

要求：
- 纯 Kotlin
- 不依赖 Android SDK
- 不依赖 Compose
- 不依赖 MapLibre
- 不依赖 ARCore
- 所有外部状态通过接口传入
- 添加 happy/boundary/failure tests

不要修改 UI，不接真实平台。
```

---

## Prompt 03 — 做 Fake 世界

```text
现在我们需要让开发机/模拟器在没有真实 GPS 和网络时也能完整运行。

请新增：
- FakeLocationProvider
- FakeClock
- FakeWeatherProvider

**不要**新增 SeededWorldRepository：这个名字从来没实现过，内容也不需要"seed"——
地点/资源/NPC/台词全部来自 `app/src/main/assets/content/*.json`（+ `<filesDir>/content/` 覆盖层）。
开发者没有网络时照样有完整内容；断网只影响真实天气，而天气可以在设置页手动覆盖。

必须可配置：
- 时间（设置页「世界状态（调试）」固定时段）
- 天气（同上，手动覆盖优先于真实来源）
- 季节（同上，或按节气自动推导）
- 玩家位置（定位方式切 Fake，点地图移动）
- 随机种子

不要调用真实 GPS。
```

---

## Prompt 04 — 地图功能

```text
实现地图功能，但不要让 Map SDK 进入 Domain。

请：
1. 定义 MapRendererAdapter。
2. 实现 FakeMapRendererAdapter。
3. 在 Android 层实现真实 adapter。
4. UI 只依赖抽象后的 map state。
5. 六边形网格由 Domain 计算，不让 Map SDK 决定游戏逻辑。

完成后测试：
- map 初始化
- camera update
- hex overlay 数据
- fog overlay 数据
```

---

## Prompt 05 — 定位

```text
实现 LocationProvider。

要求：
- Domain 只知道 LocationSample
- Platform 负责 Fused Location / Android API
- 过滤低精度与明显跳点
- 支持 start/stop
- 测试 stale update
- 测试 accuracy threshold
- 测试 location unavailable

绝对不要把原始 Location 对象传入 Domain。
```

---

## Prompt 06 — 迷雾探索

```text
实现 HexCell reveal 规则。

规则：
- 玩家进入 cell 后，该 cell 变为 discovered
- 根据 reveal radius 额外发现邻居
- 不重复计数
- 事件可重复播放但奖励不能重复结算
- 状态可序列化

请先写测试，再实现代码。
不要做动画。
不要接地图 SDK。
```

---

## Prompt 07 — 记忆系统

```text
实现 Memory Draft。

字段：
- id
- createdAt
- location
- mood
- tags
- text
- mediaRefs
- sourceEventId

要求：
- 支持本地草稿
- 网络不可用仍可保存
- media 与文本元数据分离
- 不把真实照片本体存进 Room
- 提供 sync status

补充测试：
- create
- edit
- delete draft
- retry upload
```

---

## Prompt 08 — AI NPC

```text
不要直接把 LLM 接进 NPC。

先设计：
NarrativeService interface
NpcDialogueContext
GeneratedDialogue
Safety/Validation layer

要求：
- 游戏规则由代码决定
- AI 只生成候选文本
- NPC 状态由 domain 决定
- AI 输出 JSON schema 校验后才能进入游戏
- AI 失败时有 deterministic fallback
```

---

## Prompt 09 — AR

```text
实现 AR-0：设备平面识别后放置一个虚拟物体。

注意：
- AR 模块必须独立
- 不允许 Domain 依赖 ARCore
- 没有 AR 支持时显示降级页面
- AR session 生命周期必须安全
- 不做 Geospatial/VPS
- 不做多人 AR

完成后提供：
1. capability detection
2. session state
3. placement event
4. fake AR controller
5. instrumentation/manual verification steps
```

---

## Prompt 10 — 数据库 migration

```text
请为 <table> 增加字段 <field>。

先分析：
- 当前 schema
- 现有 FK
- RLS
- index
- backward compatibility

然后：
1. 写 migration
2. 更新 DTO/domain mapper
3. 更新 seed data
4. 更新 tests

禁止修改已有玩家数据含义。
```

---

## Prompt 11 — Bug Fix

```text
这是一个 bug：
<完整复现步骤>

先不要修。
请先：
1. 判断 bug 属于 UI / domain / data / platform 哪一层。
2. 找到最小复现点。
3. 写一个失败测试。
4. 再做最小修复。
5. 运行相关回归测试。

不要顺手重构。
```

---

## Prompt 12 — 代码审查

```text
请 review 当前 diff，站在 RainingTrace 架构维护者角度检查：

- domain 是否依赖平台？
- 是否引入了未批准的 SDK？
- 是否存在 UI business logic？
- 是否存在未测试的规则？
- 是否有潜在数据丢失？
- 是否有隐私风险？
- 是否破坏 offline-first？
- 是否制造未来难以拆分的耦合？

只报告问题，不自动修改。
按 Critical / High / Medium / Low 排序。
```

---

## Prompt 13 — 最后的“收尾而不是继续加功能”

```text
今天不增加任何新功能。

请只做：
- 删除死代码
- 补测试
- 修命名
- 修日志
- 修错误处理
- 补文档
- 保证构建稳定

任何新需求都不要顺手实现。
```
