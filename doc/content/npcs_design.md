# 校园版星露谷物语 · 人物表（30 人）

> 配套 `resources_design.md` / `recipes_design.md`。本文字段命名与工作区示例 `npcs.json` 对齐。
> **本文仅为设计稿，暂不生成 json。**
> 已含 `schedule`（作息）。`sceneAnchor`（常驻地点速记）仅供策划参考，不进 json。

---

## 一、字段与命名约定

| 字段 | 约定 |
|------|------|
| `id` | `npc.bit.` + 英文小写，示例里已有 Lin / Zhou / Xu / He / Qi 五人沿用原 id |
| `name` | 姓/名一到两个字，全表不重名 |
| `role` | 一句话身份（含年级/职务），社交面板主标题 |
| `oneLiner` | 玩家在路边第一次撞见时看到的那句话，≤ 20 字 |
| `traits` | 性格标签，英文大写下划线，用于对话分支 |
| `topics` | 可聊话题标签，英文大写，2–4 个 |
| `favoriteTopic` | 玩家送对东西时好感涨得最快的那个话题 |
| `backstory` | 2–3 句，只写能改变玩家行为的往事 |
| `personaPrompt` | （可选，N4/LLM 用）人物设定的手写覆盖：不填 = 程序按身份/性格/背景/喜好自动组装；填了 = 整段采用。**普通角色不用写**，只有想精调语气的才在角色详设里加一行 `- **人设提示词**：` |
| `sceneAnchor` | 常驻地点（不进 json，策划用），对应 `placeId` |
| `giftPreferences` | `{ liked: [resourceId], disliked: [resourceId] }`，详见第八节 |
| `schedule` | `[{ startMinute, placeId, travelMinutes, activity }]`，详见第九节 |

**traits 候选（现有 8 个 + 新增，本文用到）**
现有：`TACITURN` 寡言 / `PUNCTUAL` 守时 / `WARM` 温和 / `TALKATIVE` 爱聊 / `DREAMY` 发呆 / `RESERVED` 疏离 / `CURIOUS` 好奇 / `PRACTICAL` 务实
新增：`OBSERVANT` 观察力强 / `SERIOUS` 认真 / `SHY` 怯场 / `EXACTING` 挑剔 / `BRIGHT` 明亮 / `CHATTY` 嘴碎 / `STUBBORN` 倔 / `LISTENER` 会听 / `SHARP` 锋利 / `GENEROUS` 讲义气 / `TIRELESS` 不歇 / `STEADY` 稳 / `MYSTERIOUS` 来路不明 / `ABSORBED` 忘我 / `MANNERED` 讲究 / `SMUG` 自得 / `GENTLE` 慢性子 / `PLAIN` 不起眼

**topics 候选**
`BOOKS` 书 / `SELF` 自己 / `FOOD` 吃的 / `RUNNING` 跑步 / `WEATHER` 天气 / `ART` 画画 / `NIGHT` 夜里 / `PLANTS` 植物 / `MUSIC` 音乐 / `SEWING` 缝纫 / `PHOTO` 拍照 / `TECH` 机器 / `SPORT` 运动 / `STUDY` 学习 / `PEOPLE` 人 / `SOUND` 声音 / `RAIN` 雨 / `MEMORY` 记忆 / `CAT` 猫 / `DRAWING` 制图 / `LAW` 规则 / `MONEY` 钱 / `ANOMALY` 异常

---

## 二、形象与出图提示词约定

统一后缀（两条都自动追加，条目里只写差异部分）：

- **头像**：`..., portrait bust shot, semi-realistic anime style, soft diffused light, detailed iris, subtle skin shading, clean plain background, no text`
- **立绘**：`..., full body, standing, semi-realistic anime style, detailed outfit and props, soft rim light, plain gradient background, no text`

风格锚点：**二次元写实**（semi-realistic anime），不要纯赛璐璐、不要厚涂、不要 3D 渲染。避免 `chibi`。

---

## 三、总表

---

## 四、角色详设

---

## 五、关系网（供对话与支线设计参考）

## 六、特殊角色的出场规则（供程序参考，不进 json）

## 七、字段补充：礼物偏好与作息

本轮按评审意见新增两个字段，并给原 5 位"只有姓"的角色补全名。

| 字段 | 约定 |
|------|------|
| `giftPreferences` | `{ liked: [resourceId], disliked: [resourceId] }`。liked 3–4 项、disliked 1–2 项，**必须引用 `resources.json` 中已存在的 id**（470 项，转换脚本会校验） |
| `schedule` | `[{ startMinute, placeId, travelMinutes, activity }]`。`startMinute` 为 0–1439 的当日分钟数；`placeId` 见下方占位表；`travelMinutes` 为从上一处走到本处所需分钟 |

## 八、礼物偏好 giftPreferences

liked 给"送礼加好感"，disliked 给"送礼扣好感"，每条后面标注理由，方便你判断是否合理。

> 说明：`disliked` 多数不是"讨厌这个东西"，而是"这个东西会勾起某段经历"，负面礼物设计成剧情钩子比单纯扣好感更有用。

---

## 九、作息 schedule

---

## 十、作息变体 scheduleVariants（N3）

同一套作息在不同日子/天气下的"另一版一天"。这是**生病、旅游、出行、放假**的通用底座：
不需要为每种情况单独写系统，只写一条变体 + 一个门控。

| 字段 | 约定 |
|------|------|
| `id` | 建议 `<角色短名>.<用途>`，全局唯一 |
| `label` | 玩家能看懂的短标签（"雨天""外出"），档案页作息预览会显示 |
| `fromDateKey` / `toDateKey` | 生效日期区间（yyyy-MM-dd，**含端点**）；不写 = 不限 |
| `weekdays` | `MONDAY`..`SUNDAY`；不写 = 不限 |
| `conditions` | 与产出规则同一套世界条件（`weatherIn` / `seasonIn` / `timeOfDayIn` / `betweenMinutes` / `not` / `all`），**全部满足**才生效 |
| `priority` | 越大越优先；并列按 id 字典序取大者（确定性，不会每次刷新换一版） |
| `schedule` | 这一版作息；**空数组 = 这段时间他不在**（旅游/住院），地图上不出现 |

> 三条口径：① 世界状态只做门控、不参与随机挑选；② 同一天同一时刻结果唯一；
> ③ 变体整体替换基础作息，但**承诺覆盖仍在最上层**（答应的事高于一切日程）。

---