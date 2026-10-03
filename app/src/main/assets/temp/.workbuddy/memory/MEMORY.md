# 项目长期记忆 · 校园版星露谷物语

## 数据文件约定（工作区 temp 目录）
- `resources_design.md` → `resources.json`：资源设计稿与最终数据，**md 是唯一真源**，改内容后跑 `convert_to_json.py` 重新生成，不要手改 json。
- `recipes_design.md` → `recipes.json`：配方设计稿与最终数据，同上。
- `convert_to_json.py`：md → json 转换脚本，可重复运行，运行前自动把已有 json 备份为 `.bak_<时间戳>`。

## Schema（来自官方示例，勿擅自更改）
- 两个 json 顶层均为 `{ schemaVersion, removedIds, entries }`，`removedIds` 当前恒为空数组。
- 资源条目：`id`(res.*)、`name`、`category`、`rarity`、`tags`(字符串数组)、`description`；**仅 CRAFT 类额外带 `stackLimit`**。
- 配方条目：`id`(recipe.*)、`inputs`[{resourceId, amount}]、`output`{resourceId, amount}。示例无工位字段，如需可用 `station` 扩展。
- id 命名：资源 `res.` + 英文小写下划线；配方 `recipe.` + 产物 id 去掉 `res.` 前缀。
- 枚举：category = NATURE / KNOWLEDGE / CULTURE / MEMORY / ANOMALY / CRAFT；rarity = COMMON / UNCOMMON / RARE / ANOMALY。

## 转换前必须满足的校验（脚本内已内置，失败即中止）
1. 资源 id 全局唯一。
2. 配方 id 全局唯一。
3. 配方 inputs/output 的 resourceId 必须都能在 resources.json 中找到（零悬空引用）。
4. 资源名称全局唯一（脚本按名称反查 id，重名会报错）。

## 已定型的设计取向
- 六类资源数量按「自然/制造多，记忆/异常少」分配，异常资源挂在雨天/深夜/镜子等条件上。
- 知识类与异常类多数是"发现物"而非"合成物"，允许不被任何配方引用；当前 470 项资源中 157 项未被配方引用，属预期结果。
- 强调一物多用：基础材料（手抄纸、铜线、松木板等）被大量配方共享，不设独占。
- 图标提示词统一后缀：`game item icon, single object, centered, flat vector shading, soft rim light, clean background, no text`。

## 工作方式
- 本项目内容先出 md 设计稿供用户评审，**确认后才生成 json**（用户明确要求的工作流）。
- 项目在 git 仓库中，原始示例 json 可用 `git show HEAD:app/src/main/assets/temp/resources.json` 找回。
