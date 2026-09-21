# NPC 消息线：片 0 + 片 1 + 片 2（调试偏移 / 消息与关系 / 主动消息）

## Context / 为什么做这个

NPC 的"地图腿"已完成（按作息出现 + 走动 + 只读卡片 + 首次遇见记足迹，173 单测全绿）。用户的目标是**让 NPC 看起来真的有生命**，方式是补上"消息腿"：

- **片 0**：NPC 调试时间偏移。当前调试区只能覆盖**时段**（四个桶），而 NPC 位置读真实 `minuteOfDay`，两者故意解耦 → **用户无法验证 NPC 是否在走**。补一个只影响 NPC 的偏移。
- **片 1**：消息界面（会话列表 + 聊天线程）+ 模板对话 + 好感/情绪 + 会话列表显示「此刻在「X」· 在做什么」。
- **片 2**：NPC 主动发消息（由世界状态 / 玩家行为驱动）。

**"活着"的三件事**：他记得你、他会主动找你、他说的话是真的。本片全部落地，**不需要 LLM**。

### 非目标（明确不做）

- **承诺系统（片 3）**：片 1 没有 commitment，所以玩家说"明天在图书馆等我"时 NPC **不能答应**——答应了却不去就是骗人。处理方式：NPC **如实陈述自己的作息**（"明天下午我应该在图书馆"）。这是真的，而且比承诺更像活着。承诺（作息被覆盖）留给片 3。
- 真 LLM：只留 `NarrativeService` / `NpcMessageParser` 两个接口（命名严格对齐 `doc/04_AI提示词_Prompt_Pack.md` 的 Prompt 08）。
- AR、资源点 spawn、地图图标美术。
- 熄屏后台生成主动消息（受 HANDOFF_4 §6「后台服务只写库」+ Android 14 限制，只做前台生成 + 回前台补算）。

### 已确认的用户决策

① 范围 = 片 0+1+2；② 意图解析用**自由解析**（规则实现先上，接口留给将来的 AI 选动作/地点）；③ 经历 = 手写少量 + 系统生成；④ 先补调试时间偏移。

---

## 关键设计决策

| 决策 | 结论 | 理由 |
|---|---|---|
| 关系用几条数值 | **单条 `affection: Int`** + 阈值派生 `RelationshipStage` | `NPC_MET` 只记第一次（`RecordNpcEncounterUseCase.kt:33-35` 已去重），派不出熟悉度；两条数值会漂移且玩家感知不到区别 |
| "聊过几次"从哪来 | 新增足迹事件 `NPC_TALKED`，**不存第二份真相** | 项目原则是 event-first |
| 情绪怎么衰减 | **只存"事件情绪 + 起始时刻"，基线由世界状态派生** | 零定时器、零 tick、完全可测 |
| 主动规则的时段/季节/天气判定 | **复用 `WorldCondition`**（包一层 `NpcTriggerCondition.World`） | 已有 `WeatherIn/TimeOfDayIn/SeasonIn/BetweenMinutes/All`（`WorldCondition.kt:12-78`），别重写 |
| 主动引擎跑在哪 | `AppContainer` 的 `applicationScope`，**前台门控**，60s tick | 与现有监督循环（`AppContainer.kt:160-172`）同构 |
| 引擎怎么读世界状态 | **只在 tick 里调 `worldState.current()`，绝不订阅 `worldStateProvider.state`** | `state` 是 `WhileSubscribed(5min)`；引擎是永久订阅者会让 30s ticker **永不停止**，无声破坏省电口径（`WorldState.kt:101-103` 写明的约束） |
| 玩家行为怎么拿到 | **内存"水位"**：每 tick `eventsBetween(watermark+1, now)` 后推进 | 后台唯一写库者是 `TrackRecordingService`（只写轨迹点、不写足迹），进程重启把水位置 now 不会漏。照 `catchUpFogFromBackgroundTracks`（`MapViewModel.kt:281-296`）口径 |
| 聊天页导航 | **新增子路由 + nav 参数** `npc_chat/{npcId}` | 子路由靠 `showChrome = currentRoute in TOP_LEVEL_ROUTES`（`RainingTraceApp.kt:63`）自动隐藏顶/底栏；`MemoryFocusRequest` 那套是**为常驻 Tab 间跳转**设计的，主从导航用它反而要处理 consume 时机。`navigateToTab` 的 saveState 只管一级 Tab，无冲突 |
| 未读红点放哪 | `RainingTraceBottomBar` **内部** collect | 放 `RainingTraceApp` 顶层会让每次消息变化重建 `Scaffold`+`NavHost`，导航/滚动抖动 |
| 好感怎么防刷 | `todayAffectionGain` + `todayDateKey` 日上限（O(1) 记账，不扫足迹） | 否则连点发送秒到 FRIEND |

### 我否掉的两个提议（并说明理由）

- **`verbalTic`（固定口头禅）——砍掉。** 固定口头禅是"AI 味"的第一来源，同一句尾出现三次就露馅。用 `traits` 里的 `TACITURN/TALKATIVE` 控制句长与标点，更自然。
- **`portraitRef`——本片不加。** 没有美术资源就是死字段；`NpcProfile` 是只读配置、**加字段不需要迁移**，等有图了再加是一行的事。这也符合 `WorldCondition.kt:9-10` 自己写的"别为想象中的需求先造"。

---

## 阶段 0：NPC 调试时间偏移（小，先做）

**不能改 `WorldClock`**：`clock.now()` 同时喂轨迹时间戳、迷雾水位、`memories.createdAt`、去噪窗口，偏移会污染轨迹历史且写库后无法回滚。

- 新增 `domain/settings/NpcClockOffset.kt`：`enum class NpcClockOffset(val key, val label, val minutes)` —— `NONE(0,"真实时间")` / `PLUS_1H(60)` / `PLUS_3H(180)` / `PLUS_6H(360)` / `MINUS_3H(-180)` / `PLUS_12H(720)`，`fromKey` 容错回落 `NONE`（照 `GridLevel.fromKey` 范式）。
- DataStore 5 处（照 `LocationMode`）：key `npc_clock_offset`、`npcClockOffset: Flow`、`currentNpcClockOffset()`、`setNpcClockOffset()`、接口声明。
- `NpcPresenceUseCase` 加第三参 `clockOffset: StateFlow<NpcClockOffset> = MutableStateFlow(NpcClockOffset.NONE)`（**带默认值**，既有测试不炸），内部折算 `val minute = (state.minuteOfDay + clockOffset.value.minutes + 1440) % 1440` 后再 `presenceAt(minute)`。
- `AppContainer` 持 `private val npcClockOffset = MutableStateFlow(NpcClockOffset.NONE)`，在现有 `init` 里加一个 collector 从 DataStore 同步 → 每次 tick 不碰磁盘，`presenceAt` 仍是纯函数。
- 设置页在"世界状态（调试）"块内追加 `SectionLabel("NPC（调试）")` + 说明"只改变 NPC 此刻在哪，不影响你的轨迹、迷雾与世界状态" + 6 个 `OptionRow`。
- **副作用（是好事）**：遇见判定也一起偏移——你看到的和你能遇到的是同一个位置。

---

## 阶段 1：数据层（Room v5）+ 领域模型

### 1.1 两张新表（不需要第三张，遇见/聊过都走足迹）

`npc_messages`：`id TEXT PK` / `npcId TEXT NOT NULL` / `speaker TEXT NOT NULL` / `text TEXT NOT NULL` / `createdAtEpochMs INTEGER NOT NULL` / `isRead INTEGER NOT NULL` / `source TEXT NOT NULL` / `topic TEXT`(可空) / `ruleId TEXT`(可空)；索引 `Index(value = ["npcId", "createdAtEpochMs"])`（**复合索引必要**：会话列表分组与线程分页都吃它；单列 `npcId` 被前缀覆盖，不要重复建）。

`npc_states`：`npcId TEXT PK` / `affection INTEGER NOT NULL` / `mood TEXT NOT NULL` / `moodSinceEpochMs INTEGER NOT NULL` / `lastInteractionAtEpochMs INTEGER`(可空) / `todayAffectionGain INTEGER NOT NULL` / `todayDateKey TEXT NOT NULL` / `updatedAtEpochMs INTEGER NOT NULL`；无额外索引。

### 1.2 迁移顺序（务必照此执行，否则老库升级直接崩）

① 写 `Entities.kt` 两个 Entity + `Daos.kt` 两个 Dao；② `RainingTraceDatabase.kt` 的 entities 列表加两项、`version = 5`、加两个 `abstract fun`，**先不写 Migration**；③ 构建一次，KSP 产出 `app/schemas/com.rainingtrace.data.local.RainingTraceDatabase/5.json`；④ 从 `5.json` 的 `createSql` **逐字抄**（索引是数组里的独立 `CREATE INDEX` 条目，索引名形如 `index_npc_messages_npcId_createdAtEpochMs`，表名/列名带反引号，全部保留）；⑤ 写 `MIGRATION_4_5` 并 `.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`（`RainingTraceDatabase.kt:62`）；⑥ 再构建确认 identityHash 未变。

易错点：Boolean 是 `INTEGER NOT NULL`（不是 TEXT）；可空列**不能**带 `NOT NULL`；表名/列名大小写与反引号必须一致。

### 1.3 DAO

`NpcMessageDao`：① 未读总数 `Flow<Int>`（`WHERE isRead = 0 AND speaker = 'NPC'`）；② 线程分页 `WHERE npcId = :npcId ORDER BY createdAtEpochMs DESC LIMIT :limit`（领域侧 `reversed()`）；③ 标记已读 `UPDATE ... SET isRead = 1 WHERE npcId = :npcId AND isRead = 0`；④ `@Insert(onConflict = IGNORE)`。
`NpcStateDao`：`byId` / `upsert`(REPLACE) / `observeAll(): Flow`。

### 1.4 仓储

`NpcMessageRepository`：`observeConversations(): Flow<List<NpcConversation>>` / `observeUnreadCount(): Flow<Int>` / `observeThread(npcId, limit = 50): Flow<List<NpcMessage>>` / `append(msg)` / `markRead(npcId)`。
`NpcStateRepository`：`observeStates(): Flow<Map<String, NpcState>>` / `stateOf(npcId): NpcState` / `save(state)`。`stateOf` 缺省返回 `NpcState.initial(npcId)`，**首次变化才落库**。

实现放新建 `data/repository/RoomNpcRepositories.kt`，照 `RoomMemoryRepository` 的私有 `toDomain()` + 枚举 `.name` / `runCatching{valueOf}` 写法。
`observeConversations` **用一个 `observeAll()` 单 Flow + 领域侧 `groupBy` 拼 last/unread**（5 个 NPC × 数百条完全可接受，且只有一个 Flow、无失效漏报风险）；注释写明何时该改成 `GROUP BY`。

### 1.5 领域模型

- `NpcProfile`（`domain/npc/NpcProfile.kt`）加字段，**全部带默认值**（否则既有 3 个测试文件 + `FakeNpcRepository` 的构造点全编译失败）：`role: String = ""`、`traits: Set<NpcTrait> = emptySet()`、`topics: Set<NpcTopic> = emptySet()`、`favoriteTopic: NpcTopic? = null`、`backstory: List<String> = emptyList()`；加 `init { require(favoriteTopic == null || favoriteTopic in topics) }`（防内容写错导致专属句静默永不出现）。
- `NpcTrait`：`TALKATIVE`(话多/句子长会追问) / `TACITURN`(话少/短句) / `WARM`(热络) / `RESERVED`(拘谨客气) / `PRACTICAL`(务实) / `DREAMY`(爱形容) / `CURIOUS`(会反问) / `PUNCTUAL`(按点生活，答作息时更确定)。
- `NpcTopic`：`BOOKS` / `ART` / `RUNNING` / `FOOD` / `WEATHER` / `NIGHT` / `PLANTS` / `SELF`。每个都要能从中文关键词命中，且有对应接话台词。
- `NpcMood`（**不复用 `domain/memory/Mood.kt`**）：`CALM`(基线) / `GLAD` / `INTRIGUED` / `TIRED` / `BUSY` / `DOWN`。
- `NpcState(npcId, affection, mood, moodSinceEpochMs, lastInteractionAtEpochMs?, todayAffectionGain, todayDateKey, updatedAtEpochMs)`。
- `RelationshipStage.of(affection)`：`STRANGER(<10)` / `NODDING(10..29)` / `ACQUAINTED(30..69)` / `FRIEND(>=70)`。
- `NpcMessage(id, npcId, speaker, text, createdAtEpochMs, read, source, topic?, ruleId?)`；`speaker ∈ {PLAYER, NPC}`；`source ∈ {PLAYER, TEMPLATE, AI}`。
- `FootprintEventType` 加 `NPC_TALKED`（每次聊天一条）与 `NPC_MESSAGE_SENT`（每条主动消息一条，payload 带 `ruleId`）。**加枚举值不需要迁移**（存字符串，`RoomRepositories.kt:183` 用 `valueOf`）。
- `FakeNpcRepository` 的 5 人补齐 `role/traits/topics/favoriteTopic/backstory`；同文件加 `PLACE_ALIASES: Map<String,String>`（"湖边"→`north_lake`、"食堂"→`canteen_one`…），避免 domain 硬编码 placeId。

---

## 阶段 2：对话引擎（本片核心）

### 2.1 `NpcMessageParser`（接口留给 AI）

```kotlin
interface NpcMessageParser { suspend fun parse(text: String, context: ParseContext): ParsedPlayerMessage }
data class ParseContext(npc, places, placeAliases, worldState, presence)
data class ParsedPlayerMessage(raw, topics: Set<NpcTopic>, mentionedPlaceId: String?,
    timeHint: TimeHint?, wantsToMeet: Boolean, asksAboutSchedule: Boolean,
    isQuestion: Boolean, confidence: Double)
```

`ParseContext` 带 `places/placeAliases/worldState/presence` 就是给 AI 版留的口子（AI 版多填 `confidence`/`topics`，**动作与地点仍由 domain 决定**，符合 Prompt 08"AI 不驱动规则"）。
**`wantsToMeet` 与 `asksAboutSchedule` 必须分开**："明天在图书馆等我"→ 前者；"明天下午你在哪"→ 后者。

`RuleBasedNpcMessageParser`：`NpcKeywordTable` 三张表 —— 话题关键词、时间词、见面词 + 问句标记（`？/?/吗/呢/在不在/有没有`）。地点匹配 = `Place.name` 包含匹配 + 别名表。**降级：零命中 → `confidence = 0.0`，永不抛异常**；叙事层走"没听懂 + 反问"，绝不假装听懂。

### 2.2 `NarrativeService` + `TemplateNarrativeService`

命名严格对齐 Prompt 08：
```kotlin
interface NarrativeService { suspend fun respond(context: NpcDialogueContext): GeneratedDialogue }
data class NpcDialogueContext(profile, state, stage, mood, presence, parsed, worldState,
    recentMessages: List<NpcMessage>, scheduleFacts: ScheduleFacts?, memoryHooks: List<String>)
data class GeneratedDialogue(text, source: MessageSource, topic: NpcTopic?)
```

回复 = 部件拼接 `问候 + 主体回答 + 风味尾 + 追问`。台词表按**三层回落** `(npcId,key)` → `(topic,key)` → `default`，key ∈ {`greet`, `topic.<t>`, `schedule`, `no_meet`, `unparsed`, `tail.<mood>`, `ask`}。每部件 **≥4 条变体**，用注入的 `RandomSource.nextInt(size)` 选（`SeededRandomSource` 保证可测）。
关系阶段语气：`STRANGER` 客气 / `NODDING` "又见面了" / `ACQUAINTED` 用"诶" / `FRIEND` 主动关心。
**必做**：用 `context.recentMessages` 里最近 3 条 NPC 消息用过的模板 key 去重，命中则换变体——否则同一句出现三次立刻露馅。

`DialogueValidator`：非空、长度 ≤ 120、无占位符残留 `{`、**不含承诺词表**（"等你/我一定/答应你/说好了/不见不散"）。校验失败 → 确定性回落模板（Prompt 08 的 fallback 要求）。**这是"不骗人"的机械保障。**

### 2.3 如实回答作息（重点，`domain/npc/ScheduleFacts.kt`）

`TimeHint` → 目标 minute：`NOW`(0) / `LATER_TODAY`(+120，回绕 `% 1440`) / `TONIGHT`(1200) / `TOMORROW_MORNING`(540) / `TOMORROW_AFTERNOON`(840) / `TOMORROW_EVENING`(1140)。

**跨天不需要特殊处理**：`ResolvedSchedule.presenceAt(minuteOfDay)` 是**日环**（`NpcPresence.kt:90-128`），"明天下午 14:00"与"今天下午 14:00"结果天然相同，跨天只体现在**文案的时间词**上。这正是不骗人的前提——作息每天重复，所以陈述为真。

```kotlin
fun scheduleFacts(profile, places, targetMinuteOfDay, timeLabel): ScheduleFacts?   // presenceAt 为 null → null
data class ScheduleFacts(targetMinuteOfDay, timeLabel, placeName, activity, walking, isSameAsNow)
```
模板只允许引用 `placeName/activity/walking`："{timeLabel}我应该在{placeName}，{activity}。"；走路时"那会儿我大概在往{placeName}走的路上。"

`wantsToMeet = true` 时：先按 trait 给一句软拒绝（务实"我不好说死"、拘谨"我尽量，但我一向按自己的点走"、热络"你要来我就高兴，不过别特意等我"）+ 再陈述 `ScheduleFacts`。**永不出现"我等你"**。

### 2.4 好感与情绪纯函数（`domain/npc/NpcAffection.kt`）

```kotlin
fun affectionDeltaFor(parsed, profile): Int        // CHAT +1；favoriteTopic 命中 +2；CURIOUS 且问句 +1；重复内容 +0
fun applyDailyReset(state, todayDateKey): NpcState
fun affectionAfter(current, delta, todayGain, cap = 5): Pair<Int, Int>   // 夹紧 [0,100] 与日上限
fun baselineMoodOf(presence, state): NpcMood       // walking→BUSY；深夜→TIRED；雨/雪+夜→DOWN；否则 CALM
fun effectiveMood(stored, moodSinceEpochMs, nowMs, presence, state): NpcMood
fun moodFor(parsed, profile, state, presence, worldState): NpcMood
```
`MOOD_DECAY_MS = 30 分钟`：`nowMs - moodSince > 30min` → 回落 `baselineMoodOf`。

### 2.5 `SendNpcMessageUseCase`

`suspend operator fun invoke(npcId: String, text: String): SendMessageResult`。**步骤顺序不可换**：

① 校验文本 → ② `clock.now()` + `worldState.current()` + `presenceOf` → ③ 写玩家消息(`read=true`) → ④ `parser.parse` → ⑤ **算 affectionDelta 与 mood（含日重置与上限）** → ⑥ 构造 `NpcDialogueContext`（含 `scheduleFacts` / `memoryHooks`）→ ⑦ `narrative.respond` → ⑧ `validator.validate`，失败回落 → ⑨ 写 NPC 消息(`read=false`) → ⑩ 保存 `NpcState` → ⑪ 写足迹 `NPC_TALKED` → ⑫ 返回。

**"AI 文本不驱动状态"的机械保障**：第 ⑤ 步在 `narrative` 调用**之前**完成；`GeneratedDialogue.topic` 只用于留档、不写回 state；state 与 footprint 的所有输入都来自 `parsed`（解析结果）与 domain 纯函数。

### 2.6 "他记得你"（最小可行，`memoryHooksFor`）

只做两件事：① `eventsOfType(NPC_TALKED)` 里筛该 `npcId`，取最后一条且距今 ≥1 天 → "上次在「{placeName}」聊过"；② `eventsOfType(NPC_MET)` 里该 npcId 的首次 → "你是那天在「X」跟我打招呼的"（仅 stage ≥ NODDING 且本次是首次聊天）。`take(1)` 随机选一条，不做话痨。
**"你上周三来过湖边吧"本片不做**：要按 placeId 过滤全量 `PLACE_OBSERVED`，而 payload 在库里是单列编码、SQL 无法按 key 过滤（`FootprintEvent.kt:45-50`），成本随历史线性增长。留片 3。

---

## 阶段 3：消息 UI

- **`MessagesScreen`（一级 Tab）**：改写 `feature/messages/MessagesScreen.kt`，新增 `MessagesRoute.kt` + `MessagesViewModel.kt`（照 `InventoryViewModel` 的 `stateIn(WhileSubscribed(5_000))`）。每行 = `NpcAvatar(40dp)` + 名字/时间 + **「此刻在「北湖」· 在湖边画画」**（primary 色 labelMedium）+ 最后一条（单行省略）+ 未读 8dp 主色圆点。空态改文案："走到他们身边，或等他们先开口。"
  - "此刻在做什么"：`combine(observeConversations(), observeStates(), worldStateProvider.state, npcPresence)`。**直接用 `worldStateProvider.state`**（在消息页订阅是前台行为，30s tick 成本极低），不另造 tick。
- **`NpcAvatar`**：新建 `core/ui/NpcAvatar.kt`（照 `MapScreen.kt:769-786` 的 `PlaceThumb`）。颜色 = `NpcAvatarPalette[abs(npcId.hashCode()) % size]`（`String.hashCode` 有规范定义、跨设备稳定）；色板加在 `core/ui/theme/Color.kt`。字形 = `name.take(1)`。
- **聊天页（子路由 + nav 参数）**：`Routes.NPC_CHAT = "npc_chat/{npcId}"` + `fun npcChat(npcId) = "npc_chat/$npcId"`；`RainingTraceApp.kt` 加 `composable(Routes.NPC_CHAT, arguments = listOf(navArgument("npcId") { type = NavType.StringType }))` → `ChatRoute(container, npcId, onBack = { navController.popBackStack() })`。新建 `ChatRoute.kt` / `ChatScreen.kt` / `ChatViewModel.kt`（返回栏照 `JournalRoute.kt:63-91`）。
  - UI：`LazyColumn` + `LaunchedEffect(messages.size) { animateScrollToItem(lastIndex) }`；玩家右（`primaryContainer`）/ NPC 左（`surfaceVariant`），`RoundedCornerShape(16.dp)` 一角切小。输入区**复刻 `CameraScreen.kt:229-284`**（`Surface` 圆角面板 + `OutlinedTextField(minLines=1, maxLines=4)` + 右侧发送 `IconButton`）。
  - **副标题要**：顶栏下一行「此刻在「北湖」· 在湖边画画」——让"NPC 是活的"在聊天页也成立。
  - 注意 `currentRoute` 会是模板串 `"npc_chat/{npcId}"`，统一用 `Routes.NPC_CHAT` 常量比较（现有 `else -> "雨迹"` 已兜住）。
- **底栏未读红点**：`container.npcMessageRepository.observeUnreadCount().collectAsStateWithLifecycle(0)` 放在 `RainingTraceBottomBar` **内部**，在 `Routes.MESSAGES` 的 `NavigationBarItem` 用 `BadgedBox` 画点。**不做的话玩家不知道 NPC 找过自己。**
- **设置页"消息"分区**（真实玩家设置，放在"足迹记录"之后、"世界状态（调试）"之前）：新增 `domain/settings/NpcMessageSettings.kt`（整份结构体，照 `TrackingSettings`）+ `enum class ProactiveLevel(val label, val dailyLimit)`：`QUIET("不打扰", 0)` / `NORMAL("正常", 2)` / `ACTIVE("活跃", 5)`。文案："他们会偶尔主动找你说话——按自己的作息、天气和你去过的地方。" + 3 个 `OptionRow` + `ToggleRow("显示好感数值", ...)`（`ToggleRow` 已存在，`SettingsScreen.kt:338`）。

---

## 阶段 4：NPC 主动消息（片 2）

### 4.1 规则模型（`domain/npc/NpcProactiveRule.kt`，照 `ResourceYieldRule` + `WorldCondition`）

```kotlin
data class NpcProactiveRule(id, npcId, condition: NpcTriggerCondition,
    cooldownMs = 4h, text: String, priority: Int = 0) { val specificity get() = condition.specificity }
sealed interface NpcTriggerCondition {
    data class World(val condition: WorldCondition)          // 复用现成的时段/季节/天气
    data class WeatherBecame(val kinds: Set<WeatherKind>)     // 跃迁
    data class PlayerEnteredPlace(val placeId: String)
    data class PlayerMetNpc(val npcId: String)
    data class PlayerIdleFor(val days: Int)
    data class All(val conditions: List<NpcTriggerCondition>)
}
```

`InMemoryNpcProactiveRuleCatalog.DEFAULT` 给 8 条，对应现有 5 NPC / 9 地点，例如：林 `All(WeatherBecame(RAINY), World(TimeOfDayIn(DAY)))` cooldown 12h；周 `All(World(BetweenMinutes(360,480)), PlayerIdleFor(2))`；徐 `All(PlayerEnteredPlace(north_lake), World(TimeOfDayIn(DUSK)))`；何 `All(World(TimeOfDayIn(NIGHT)), WeatherBecame(FOG))`；齐 `All(World(SeasonIn(AUTUMN)), World(TimeOfDayIn(DAY)))`；林 `PlayerMetNpc(lin)` cooldown 24h；徐 `All(WeatherBecame(RAINY), World(SeasonIn(SPRING)))`；周 `PlayerIdleFor(3)`。

### 4.2 引擎 `NpcProactiveMessageUseCase.tick(): NpcMessage?`

- **天气跃迁**：引擎持 `lastWeatherKind` 内存变量，在 tick 里与 `worldState.current().weather.kind` 比较。**冷启动（null）不算跃迁**，否则每次开 app 必触发天气规则。
- **玩家行为**：内存水位 `watermarkMs`，每 tick `eventsBetween(watermarkMs + 1, nowMs)` 后推进。
- **去重**：写 `FootprintEventType.NPC_MESSAGE_SENT`（payload `ruleId`/`npcId`），冷却判定照 `PerformPlaceActionUseCase.onCooldown`（`eventsBetween(now - cooldown, now).any { ... }`）。
- **一次 tick 最多一条**：候选按 `compareBy({ specificity }, { id })` 取最大（照 `chooseMostSpecific`，确定性优先）。
- **三重限流**：每日上限（`ProactiveLevel.dailyLimit`，按 `WORLD_ZONE` 推本地日起点）+ 免打扰（`minuteOfDay !in 7*60 until 23*60` → return null）+ 全局最小间隔 2h。
- **前台门控**：循环体第一行 `if (!foregroundState.isForeground.value) continue`；`runCatching` 包住单次 tick（单次失败不能让循环死掉）。
- **回前台补算**：tick 本身幂等；在 `AppContainer` 的 `foregroundState.isForeground` collector 里 `distinctUntilChanged()` 后加一次立即 `launch { npcProactive.tick() }`。

### 4.3 循环位置

`AppContainer` 的 `applicationScope`，与现有监督循环同构：
```kotlin
applicationScope.launch {
    while (true) {
        delay(NPC_PROACTIVE_TICK_MS)   // 60_000
        if (!foregroundState.isForeground.value) continue
        runCatching { npcProactive.tick() }
    }
}
```
**绝不订阅 `worldStateProvider.state`**（见上文"关键设计决策"）。

### 4.4 未读与"刚发来"

`npc_messages` 写入 → Room Flow 自动 emit → 底栏红点与聊天页自动更新，**不需要额外通道**。

---

## 文件清单

**新增（domain/npc）**：`NpcMessage` / `NpcState` / `NpcTrait` / `NpcTopic` / `NpcMood` / `NpcAffection` / `NpcMessageParser` / `RuleBasedNpcMessageParser` / `NarrativeService` / `TemplateNarrativeService` / `ScheduleFacts` / `SendNpcMessageUseCase` / `NpcMessageRepository` / `NpcStateRepository` / `NpcProactiveRule` / `NpcProactiveMessageUseCase`
**新增（其他）**：`domain/settings/NpcClockOffset.kt`、`domain/settings/NpcMessageSettings.kt`、`data/repository/RoomNpcRepositories.kt`、`core/ui/NpcAvatar.kt`、`feature/messages/{MessagesRoute,MessagesViewModel,ChatRoute,ChatScreen,ChatViewModel}.kt`
**修改**：`domain/npc/NpcProfile.kt`（加 5 字段 + require）、`domain/npc/NpcPresenceUseCase.kt`（加 clockOffset）、`domain/footprint/FootprintEvent.kt`（加 2 个枚举值）、`data/local/{Entities,Daos,RainingTraceDatabase}.kt`（两表 + v5 + MIGRATION_4_5）、`data/repository/FakeNpcRepository.kt`（补字段 + `PLACE_ALIASES`）、`data/settings/DataStoreSettingsRepository.kt` + `domain/settings/AppSettingsRepository.kt`（两组设置各 5 处）、`core/common/AppContainer.kt`（装配 + holder + 引擎循环 + 回前台补算）、`feature/shell/{Destinations,RainingTraceApp}.kt`（路由 + 红点）、`feature/settings/{SettingsViewModel,SettingsScreen}.kt`（调试偏移 + 消息分区）、`core/ui/theme/Color.kt`（头像色板）

---

## 单测清单（数据层不测——项目没有 Robolectric/in-memory Room）

沿用风格：Fake 仓储 + `FakeWorldClock` + `SeededRandomSource` + `runTest` + 反引号命名。

- `RuleBasedNpcMessageParserTest`：多话题命中 / 地点名与别名 / "明天下午"→840 / 问句识别 / **`wantsToMeet` 与 `asksAboutSchedule` 分离** / 空串与纯符号降级 `confidence=0` / 不抛异常
- `ScheduleFactsTest`：明天下午在图书馆 / 跨零点凌晨段 / 目标时刻在路上 / 无有效作息返回 null / `LATER_TODAY` 回绕 / **今天下午与明天下午同结果**（证明日环）
- `TemplateNarrativeServiceTest`：固定 seed 输出确定 / 不同 stage 语气不同 / favoriteTopic 专属句 / 解析失败走"没听懂"且无承诺词 / **`DialogueValidator` 挡下"我等你"与超长文本** / 最近 3 条用过的模板被换掉
- `NpcAffectionTest`：CHAT +1 / favoriteTopic +2 / 日上限夹紧 / 跨日重置 / 不为负 / stage 阈值边界 9/10/29/30/69/70
- `NpcMoodTest`：favoriteTopic→GLAD / walking 被搭话→BUSY / 雨夜→DOWN / 30 分钟后回落 CALM / 未过期取事件情绪
- `SendNpcMessageUseCaseTest`：写两条消息 / state 更新 / `NPC_TALKED` payload 含 npcId+delta / **validator 拒绝时回落模板且 state 不变**（证明 AI 不驱动状态）/ 日上限后不再涨
- `NpcProactiveMessageUseCaseTest`：首次 tick 不因启动触发天气规则 / 天气跃迁触发 / 冷却挡住第二次 / `QUIET` 一条不发 / 免打扰时段不发 / 水位只覆盖新增事件 / 一次 tick 只发一条 / specificity 选规则
- `NpcPresenceUseCaseTest` 扩展 + `NpcClockOffsetTest`：偏移平移 / ±回绕 / `fromKey` 容错

---

## 验证方式

1. 单测：`.\gradlew.bat :app:testDebugUnitTest`（现有 173 + 本片新增，全绿）。
2. 构建安装：`.\gradlew.bat :app:assembleDebug` → `& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk`。
3. **迁移验证（必做）**：装 v5 前先在设备上留一个 v4 的库（先装旧包跑一次），再覆盖安装，`logcat -d | Select-String "Migration|Room|FATAL"` 应无 `Migration didn't properly handle`。
4. 真机手动验收（复杂交互由用户测；AI 只做构建/安装/启动/单测/低阶 logcat）：
   - 设置页「NPC（调试）」切到 `+3 小时`，回地图确认 NPC 位置变化、能看到有人在路上走（**这就是片 0 要解决的问题**）。
   - 消息 Tab：5 个人都在，每行显示"此刻在「X」· 在做什么"；未读时有圆点。
   - 进聊天：问"明天下午你在哪" → 回复是**真实的作息陈述**（与地图上明天的位置一致）；说"明天在图书馆等我" → **不答应**，只陈述作息 + 软拒绝。
   - 连发 10 条：好感不会爆涨（日上限）；同一句不会连续出现三次。
   - 设置里切"不打扰" → 一段时间内不再收到主动消息；切"活跃" + 用调试天气切到雨天 → 应收到对应 NPC 的消息，且底栏出现红点。
   - 主动消息不重复轰炸：同一规则在 cooldown 内只发一次。
5. `logcat` 观察：`& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -v time | Select-String "FATAL|Room|Migration|MapLibre"`

---

## 风险与坑（按严重度）

1. **Migration SQL 与 `5.json` 不一致** → 老库升级抛 `Migration didn't properly handle` 直接崩。严格按 1.2 的顺序；Boolean 是 `INTEGER NOT NULL`。
2. **引擎订阅 `worldStateProvider.state`** → `WhileSubscribed` 永不停止、30s ticker 常驻，**无声**破坏省电口径。只在 tick 里用 `current()`。
3. **前台门控漏写** → 后台空转，违反 HANDOFF_4 §6「后台只写库」。循环第一行必须门控。
4. **`NpcProfile` 加必填字段** → 既有 3 个测试文件 + `FakeNpcRepository` 构造点全编译失败。**所有新字段必须带默认值。**
5. **底栏红点在 shell 顶层 collect** → 每次消息变化重建 `Scaffold`+`NavHost`，导航/滚动抖动。下沉进 `RainingTraceBottomBar`。
6. **好感可被刷** / **主动消息轰炸** → 日上限 + 重复内容 delta=0；每日上限 + 免打扰 + 规则 cooldown ≥4h + 全局最小间隔 2h（三重）。
7. **模板"AI 味"** → 每部件 ≥4 变体 + 三层回落 + 用 `recentMessages` 排除最近用过的 key（列为必做）。
8. **`currentRoute` 是模板串** → `when(currentRoute)` 按实际路径写会失效，统一用 `Routes.NPC_CHAT` 常量。

---

## 后续（片 3，不在本片）

承诺系统：`NpcCommitment` + 意图解析出"时间窗 + 地点" + **答不答应由真实作息决定**（查 NPC 在那段时间的基础作息，赶不上就改期）+ **作息覆盖**（`NpcSchedule = 基础作息 + 生效承诺`）→ NPC 到点真的出现（**复用"位置=时间纯函数"，不需要寻路**）+ 兑现记足迹涨好感 + 你没去他第二天会提一句。届时把 `NpcMessageParser` 换成 AI 实现即可让 AI 选动作与地点。
