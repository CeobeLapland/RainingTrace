# NPC 系统升级设计稿（切片路线 + 切片 1 详设）

> 状态：待评审。本次只出设计稿，不动代码、不生成/覆盖任何 json。
> 配套：`npcs_design.md`（30 人角色详设）、`temp/npcs.json`（30 人 json 工作副本）、`doc/handoff/HANDOFF_7.md`。
> 本稿是「接下来怎么做」的落纸版：先定路线，再详设第一个切片。

---

## 0. 背景与已定案口径（2026-10-04 讨论共识）

现状：运行时只有 5 个 NPC（`assets/content/npcs.json`）；30 人 json 在设计稿与 `temp/npcs.json` 里，未接入运行时；代码没有任何 `giftPreferences` 字段；`NpcTrait`/`NpcTopic` 枚举容量不足；头像/立绘图片命名与代码约定对不上且立绘无落点。

本轮已定 4 项口径：

| 口径 | 决定 |
|------|------|
| 切片顺序 | 内容接入先行 → 地图交互 → 随机运动/作息变体 → 记忆层+LLM |
| LLM 接入 | 先做架构 + 本地规则记忆，真实 API 留插槽；关键词匹配留作保底 |
| 随机运动 | 同一天同结果（沿用 spawn 确定性铁律） |
| 立绘落位 | 档案全屏页 + 地图卡片 |

---

## 1. 切片总览（路线图）

| 切片 | 内容 | 说明 |
|------|------|------|
| **N1 内容接入** | 枚举扩容、NpcProfile 加字段、30 人 json 入运行时、图片命名对齐 | 本稿详设（§2） |
| N2 地图交互 | 卡片操作条（聊天/档案/送礼）、档案全屏页、作息轨迹预览 | 概要 §3 |
| N3 随机运动 + 作息变体 | 确定性小范围溜达、雨天/周末/事件变体 schedule | 概要 §4 |
| N4 记忆层 + LLM | `npc_memory_facts` 表、规则化事实提取、Prompt 组装、LLM 插槽 | 概要 §5 |
| N5 互通与高级 | 结伴出行、礼物、委托跑腿、关系网、特殊角色、主动分享扩展 | 概要 §6 |

---

## 2. 切片 1 详设：内容接入（本稿重点）

### 2.1 枚举扩容（代码侧）

**traits：8 → 26**。新增 18 个：
`OBSERVANT` 观察力强 / `SERIOUS` 认真 / `SHY` 怯场 / `EXACTING` 挑剔 / `BRIGHT` 明亮 / `CHATTY` 嘴碎 / `STUBBORN` 倔 / `LISTENER` 会听 / `SHARP` 锋利 / `GENEROUS` 讲义气 / `TIRELESS` 不歇 / `STEADY` 稳 / `MYSTERIOUS` 来路不明 / `ABSORBED` 忘我 / `MANNERED` 讲究 / `SMUG` 自得 / `GENTLE` 慢性子 / `PLAIN` 不起眼

**topics：8 → 22**（以设计稿第一节为准）。新增 14 个：
`MUSIC` 音乐 / `SEWING` 缝纫 / `PHOTO` 拍照 / `TECH` 机器 / `SPORT` 运动 / `STUDY` 学习 / `PEOPLE` 人 / `SOUND` 声音 / `RAIN` 雨 / `MEMORY` 记忆 / `CAT` 猫 / `DRAWING` 制图 / `LAW` 规则 / `MONEY` 钱

**必须同步的三处（漏一处就"匹配到了却无话可说"）**：
1. `NpcTrait` / `NpcTopic` 枚举定义（`domain/npc/`）。
2. `assets/content/npc_keywords.json` 的 `topics`：每个新话题 ≥3–4 个中文关键词。
3. `assets/content/npc_lines.json` 的台词：本期先给 14 个新话题各补 4 条**共享** `topic.*` 台词，不逐人写专属（体验兜底走 `topic.default`，不崩但干瘪）。NPC 专属覆盖（`npc.<id>.topic.*`）按需再补。

### 2.2 NpcProfile 字段

新增两个字段（其余已有）：

| 字段 | 类型 | 用途 |
|------|------|------|
| `giftPreferences` | `GiftPreferences(liked: List<String>, disliked: List<String>)` | 本期**只入库不做玩法**；送礼/对话反应在 N5 |
| `homePlaceId` | `String?` | 显式"家"；夜间可打扰性判断的地基，N3 作息变体也会用 |

关系网（设计稿只有文字图）不做结构化，N5 再补 —— 本期不扩字段。

### 2.3 30 人 json 接入（含一个必须先确认的前置依赖）

搬运路径：`temp/npcs.json` → `assets/content/npcs.json`（内置 assets，是运行时的唯一真相）。原 5 人 id 与 30 人版完全一致，直接合并去重（name 升级为全名，oneLiner/backstory 已重写）。

**前置依赖（关键风险）**：30 人 schedule 引用 48 个 placeId（设计稿第 7.1 节占位表），而运行时 `places.json` 只有 9 个地点。`ResolvedSchedule` 对不存在地点的条目**直接剔除不崩**——所以不加地点数据就切 30 人，大部分人只会剩下"基于现有 9 点"的少量作息，其余全被静默丢掉。

两条路（待确认，见 §7）：
- **a) 全量切**：先扩 `places.json`（`places_design.md` 已备 139 处但经纬度全空，需要用户提供坐标）。
- **b) 两段切**：先把 N1 代码侧（2.1 / 2.2 / 2.4）+ 30 人 json 落地（作息被剔除的条目暂时不生效），地点数据齐了再补 places 并复查作息覆盖率。

### 2.4 图片命名对齐（含立绘落位）

现状：用户已按 `npc.head.bit.lin.png`（头像）/ `npc.body.bit.lin.png`（立绘）出图；代码约定 `ArtPaths.npcAvatar(id)` 找的却是 `art/npc/<id>.png` → 对不上，两张图都显示不出来。

**方案（改约定，不动已画好的文件名）**：

| 函数 | 新路径 | 回退 |
|------|--------|------|
| `npcAvatar(id)` | `art/npc/npc.head.<id>.png` | 现有"色块+姓氏"兜底 |
| `npcPortrait(id)`（新增） | `art/npc/npc.body.<id>.png` | 无图时隐藏立绘区 |

立绘落位（已定）：**档案全屏页**（N2 实现）+ **地图卡片**（N2 实现）。本期只定死路径约定（`ArtPaths` + 单测），UI 落位在 N2。

### 2.5 测试影响

- 枚举只增不改，存量测试不受影响。
- 新增 14 个话题若只有关键词没有台词，会走 `topic.default` 兜底——不红但体验差，故 §2.1 要求补共享台词。
- 图片路径改动涉及 `ArtPaths` 测试，同步更新。
- 30 人 json 切进内置内容后，`ShippedContent` 要求零诊断（WARN 都会拦构建）：需保证 `favoriteTopic ∈ topics`、字段集一致等（转换脚本已校验过，搬运时再跑一遍自检）。

---

## 3. 切片 2 概要：地图交互

- `NpcDetailCard` 加操作条：`[聊天]`（跳聊天页，预填开场白）/ `[档案]`（全屏页）/ `[送礼]`（占位，N5 接玩法）。
- **档案全屏页**（galgame 式）：立绘大图为视觉主体 + 头像 + oneLiner + 性格标签 + 礼物喜好 + 关系阶段 + 今日作息轨迹小时间轴（"图书馆 → 食堂 → 宿舍"）。
- **地图卡片**：卡片内嵌小立绘/头像。
- "在地图上碰见 → 能聊"的入口正式接线（当前卡片只读、只有 toast）。

## 4. 切片 3 概要：随机运动 + 作息变体

- **确定性溜达**：seed = `(npcId, 日期)` → 在作息停留点周围算半径内固定小偏移，NPC 在做停留时小范围活动。复用 `SpawnPlanner` 的确定性哈希思路，与承诺覆盖兼容（承诺是整条作息替换，溜达只在停留期发生）。
- **作息变体**：给 schedule 加"变体表"——`雨天变体`（周野雨天不跑广场改操场看台）、`周末变体`（不排课）、`事件变体`（校庆全员去礼堂）。这是"动态变化"（旅游/毕业/生病）的通用底座：旅游=带日期窗口的变体（几天不在），生病=临时变体+对话提及。
- 世界状态仍只做**门控**（是否变体），不参与挑选（沿用 spawn 铁律）。

## 5. 切片 4 概要：记忆层 + LLM（只做架构，不接真实 API）

| 层 | 内容 | 来源 |
|----|------|------|
| 静态档案 | 身份/性格/背景 → system prompt | JSON，不变 |
| 关系事实 | 关系阶段、好感、玩家偏好、送过什么、承诺兑现记录 | 规则从足迹+聊天提取 |
| 情景记忆 | "雨天在湖边聊过""他说过要考试" | 足迹事件 + 聊天解析沉淀 |
| 长期总结 | 对话攒够后压缩成几句"这人怎样" | MVP 先规则摘要，API 就绪后离线做 |

- 新增 `npc_memory_facts` 表（npcId、kind、content、source、importance、createdAt）；超量按重要性裁剪。
- **domain 仍然掌权**：LLM 只产候选文本，好感/情绪/承诺仍由现有 `SendNpcMessageUseCase` 链路算，`DialogueValidator` 守门不变——这个口子现在就是好的。
- 接入时换 `NarrativeService` 实现即可；请求失败/离线 → 现在这套关键词+模板兜底原样保留。

## 6. 切片 5 概要：互通与高级功能

- **结伴出行**：作息加"陪伴窗口" override，窗口内 NPC 位置跟随玩家坐标，地图并排走、边走边聊，到点/到目的地解除。（玩家驱动的，天然非确定性，豁免于同一天同结果。）
- **礼物系统**：N1 入库的 `giftPreferences` 开始生效。
- **委托跑腿**："帮我带杯水" → 地图小目标 → 完成改变关系/好感。
- **关系网**：结构化并进对话（"老陈昨天说…"）→ 互相传递消息/支线。
- **特殊角色条件出现**：沈墨/夏星/三更/娄七 不走 schedule，按设计稿第六节出场规则（雨天/照片点/深夜/收集前置）单独做条件出现，是异常侧的钩子。
- **主动分享扩展**：NPC 主动发来资源图鉴/记忆卡（现低配版只发纯文本）。

---

## 7. 待确认事项 → 已定案（2026-10-04 落实 N1 时确认）

| # | 问题 | 决定 |
|---|------|------|
| 1 | 地点数据渐进补齐，作息悬空怎么办？ | **静默逐条剔除**：`ContentValidator.validNpcs` 从"整位剔除 + ERROR"改为"逐条剔除、人保留"（含量校验测试 + 新增单测钉住）。兜底：核心 5 人必须至少 1 条可解析（`ShippedContentTest`）。 |
| 2 | 特殊 4 人（沈墨/夏星/三更/娄七）？ | **正常入档 + `enabled: false`**：可对话、可送礼，但不上地图（`NpcPresenceUseCase` 过滤）。转换脚本 `DISABLED_NPCS` 常量保证再生成也保持。 |
| 3 | 专属台词？ | 暂不写；15 个新话题已补共享台词各 4 条，专属覆盖按需再加。 |
| 4 | 关系网结构化？ | 本期不做；设计稿关系网仍是文字图。 |

## 8. N1 落地记录（2026-10-04）

- 枚举：`NpcTrait` 8→26、`NpcTopic` 8→23（含内容用到的 `ANOMALY`）。
- `NpcProfile` 新增 `giftPreferences` / `homePlaceId`（N1 只入库）/ `enabled`；管线 DTO/解码同步。
- 30 人 json 落地 `assets/content/npcs.json`；转换脚本指向 `doc/content/` 设计稿，修了齐岭 oneLiner 乱码、跨日 1440 条目（md 源同步删除，约定改为"不写 1440 跨日标记"）。
- 图片约定：头像 `art/npc/npc.head.<id>.png`、新增立绘 `art/npc/npc.body.<id>.png`（`ArtPaths` + 测试）。已画的 `npc.head.bit.lin.png` / `npc.body.bit.lin.png` 现在能读到；立绘 UI 落位在 N2（档案页 + 地图卡片）。
- 单测 407 全绿。**既有存档行为不变**：核心 5 人作息多数落地，位置/行走照旧。

## 9. N2 落地记录（地图交互）

- **交互条（galgame 式选项列）确认按"列表"设计**：`NpcInteraction` 枚举（TALK/PROFILE，GIFT/ERRAND/WALK_TOGETHER 注释预留）；卡片只渲染已实现的动作，避免"点不动死的按钮"。N5 加动作 = 枚举加值 + `ALL_NPC_INTERACTIONS` 点亮 + `when` 补分支（编译器会盯着）。
- **卡片重做** [MapScreen.kt 的 NpcDetailCard](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapScreen.kt)：头像 + 名字 + 此刻状态 + 一句话 + 今天在做什么 + 操作条 `[聊天][档案]`。聊天**碰见即开放**（不需要先解锁，对应"现实遇见 → 聊天软件"的入口）。
- **档案全屏页**（NpcProfileSheet，盖在地图上的全屏页）：立绘大图（`npc.body.<id>.png`，缺图占位底板）+ 关系阶段 + 性格标签 + 礼遇喜好（N1 入库的数据，送礼玩法 N5）+ 今日作息轨迹时间轴（按可解析地点过滤，与 `resolveSchedule` 同口径）。
- 地图 → 聊天已接线（`onOpenChat` → `npc_chat/{npcId}` 路由）。
- 测试构建全绿。

## 10. N4 落地记录（记忆层 + LLM 架构，2026-10-04）

**问题定案**
- **固定提示词做成可配置**：`NpcProfile.personaPrompt`（JSON 可选字段），没填 = `NpcPersonaPrompt` 从身份/性格/背景/喜好自动组装；填了整段覆盖。
- **主动对话**：触发条件保持规则化（玩法状态，不交给 LLM 拍板）；本轮不改行为，LLM 只留"文本生成"插槽。
- **Token 控制（三层，绝不无限增长）**：① 每轮只带最近消息（已有）+ 记忆 `topK(12)`；② 每个 NPC 记忆封顶 40 条，超了把最低优先级 5 条合并成 1 条 SUMMARY（已有 SUMMARY 并入而非新开）；③ `PromptBudget`（2400 字）硬裁剪：保人格 → 保记忆 → 保最新消息。

**落地**
- Room **v8**：`npc_memory_facts` 表（有界事实表，索引 npcId），迁移照 `8.json` 逐字抄。
- `NpcMemoryMaintenance`（纯函数）：提取/去重/topK/prune(合并 SUMMARY)/对话→事实。
- `NpcMemoryWriter`：聊天（话题偏好/提到地点/答应的约定）与初次遇见都沉淀记忆，写完自动清理。
- `LlmClient` 接口 + `LlmNarrativeService`：组装"人格+世界/关系状态+记忆+最近消息"的有界 prompt；**client 为空/失败一律回退模板**，绝不假装听懂；产出的文本仍由 `SendNpcMessageUseCase` 结算好感/承诺，并过 `DialogueValidator`。
- `AppContainer`：`llmClient` 当前为 null（关闭），接 API 时实现 `LlmClient` 换掉即可，玩法零改动。
- 单测 +12（提取/去重/topK/清理/预算/人格/LLM 兜底），全绿。

## 11. 补充落地（2026-10-05）

- **personaPrompt 手动覆盖已接通"md 唯一真源"工作流**：角色详设里加一行 `- **人设提示词**：` → 转换脚本解析为 `personaPrompt` → 运行时 JSON。示例已给林砚（`doc/content/npcs_design.md` 林砚节）。不写 = 自动组装。
- **记忆可编辑（玩家/开发者共用）**：档案页"他还记得"区块——每条可编辑内容与重要度（1–10）、可删除、"＋写下一条"手写添加（自动去重、走超限合并）。入口在 `NpcMemoryEditor`（domain 用例），UI 在 [MapScreen.kt 的 NpcProfileSheet](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapScreen.kt)。
- 单测 +3（手写添加判重/超限、编辑覆盖、重要度夹取），全绿。

## 12. N3 落地记录（随机运动 + 作息变体，2026-10-27）

**立绘修复**：问题不在命名，而在 `ArtIcon` 默认把图解码成 96px，而立绘容器只给了高度约束
→ 实际渲染只有约 32dp 宽，糊成一小块。现改为：立绘解码上限 720px + `fillMaxSize()`。
头像不受影响；后续出图仍按 `npc.head.bit.lin.png` / `npc.body.bit.lin.png`。

**作息变体（生病 / 旅游 / 出行 / 放假的通用底座）**
- `NpcScheduleVariant`：`fromDateKey..toDateKey`（含端点）+ `weekdays` + 复用 `WorldCondition`
  + `priority` + **空 schedule = 这段时间不在校**（地图上不出现）。
- 选择口径：世界状态**只做门控**；priority 降序 → id 字典序（确定性，同一天同一时刻结果唯一）。
- 变体**整体替换**基础作息；**承诺覆盖仍在最上层**（答应了就站定，不溜达）。
- 非变体内容：某个变体写坏只丢那一条（WARN），不会连累整位 NPC；变体里的悬空地点同样逐条剔除。
- 内容入 md 第十节（`## 十、作息变体`），转换脚本自动写入 `scheduleVariants`。
  示例：周野「雨天」（不跑广场）、廖一「外出考试」（空作息）、廖一「病了」（大暴雨）。

**随机运动（确定性溜达）**
- `NpcWander`：停留期以 20 分钟为一档，档内位置固定；档间前 3 分钟平滑过渡
  （避免 10s ticker 下整点瞬移）。种子 = `(npcId, 日期, 该条作息到达时刻, 档号)`，
  **同一天同一时刻完全一致**，半径 ≤ 14m，不会跑到别的点去。
- `presenceAt(minuteOfDay, dateKey)`：传日期才溜达，不传严格站定（旧测试语义不变）。
- 走路时段仍是作息点之间的线性插值，不叠加溜达。

**档案页**：「今天他会去」会显示生效变体标签（如「今天他会去（雨天）」）；
变体作息为空时显示「今天不在（外出考试）」。

**测试**：+13（变体门控/优先级/承诺优先/外出不出现/溜达确定性与半径/过渡），共 **432 全绿**。

## 13. 下一步候选

- N5 送礼/委托/结伴 + 关系网 + 特殊角色条件出场
- 真 LLM：实现 `LlmClient`（需要你的 API key 与选型）
- 变体内容扩充：周末变体、更多角色的病情/出行

## 14. 内容工作台（NPC 可视化编辑，2026-10-27）

**为什么**：内容最终要由你亲手改，但纯对 JSON 改有两个硬伤——关联 id 要记（地点/资源/话题），
写错会**静默失效**（不报错、只是不生效）。所以把"选 id + 校验 + 即时预览"做进 App。

**入口**：设置 → 内容（开发者模式）→ **NPC 工作台**。

**能改什么**
- 基本信息：名字 / 身份 / 一句话人设 / 是否启用
- 性格（26 个多选）、愿意聊的话题（23 个多选）、最喜欢的话题（只从已选话题里挑）
- 背景（一行一条）、人设提示词（留空 = 自动组装）
- 礼物喜好（资源搜索 + 多选，不再手打 `res.*`）
- 家（地点下拉）
- 作息（时间下拉 5 分钟步进 / 地点下拉 / 在做什么 / 步行分钟 / 增删）
- 作息变体：**只读展示**（写回时原样保留，不会被编辑器清空）

**保存行为**
- 写入覆盖层 `files/content/npcs.json`（**按 id 合并**，只动你改的那一位）
- 写完自动 `reload()`，地图 / 档案页 / 聊天立刻生效，不用重装
- 列表里标 **「已覆盖」**：覆盖层优先级高于内置 assets，以后更新内置内容时这些 id 不会跟着变
- 「重置为内置内容」= 从覆盖层删掉这一位

**长期工作流（重要）**
1. App 里改 → 保存 → 真机看效果；
2. 满意后 `adb pull /data/data/com.rainingtrace/files/content/npcs.json`；
3. 把改动合并回 `doc/content/npcs_design.md`（唯一真源）→ 重跑转换脚本 → 内置内容更新；
4. 最后在 App 里点「重置为内置内容」，清掉覆盖层，避免两份数据打架。

**测试**：+7（草稿校验 / 领域 require / 覆盖层编码无损往返）。共 **438 全绿**。
