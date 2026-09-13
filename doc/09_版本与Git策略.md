# RainingTrace / 雨迹 — Git、版本与回滚策略

**一般情况下不用你交，我自己交**

## 1. 分支

推荐：

```text
main
  ↑
feature/RT-xxxx-short-name
  ↑
AI/local worktree
```

不建议 AI agent 长期直接改 main。

---

## 2. Commit 格式

```text
feat(map): add hex reveal reducer
fix(location): filter stale samples
refactor(memory): isolate media metadata
 test(world): add rainy event fixture
docs(vibe): add agent rules
chore(build): update dependency catalog
```

---

## 3. Checkpoint 频率

### 可以回滚的大小

一个 checkpoint 应当满足：

> “这个 commit 单独 checkout 也应该大致可理解。”

避免：

```text
feat: everything
```

---

## 4. AI 大改保护

执行大任务前：

```bash
git status
git add .
git commit -m "chore: checkpoint before <task>"
```

然后开始 AI 操作。

---

## 5. Diff 规则

如果 AI 的 diff：

- 超出任务范围 2 倍
- 修改了 20+ 个不相关文件
- 删除大量原代码
- 修改公共 domain API

暂停审查。

---

## 6. Rollback

优先：

```bash
git revert <commit>
```

不要在不了解历史的情况下：

```bash
git reset --hard
```

---

## 7. DB rollback

DB migration 与应用版本必须配对。

不能简单假设：

> Git 回滚 = DB 自动回滚。

需要明确 forward-compatible migration。

---

## 8. Content version

游戏内容独立版本化：

```text
appVersion
contentVersion
worldRulesVersion
schemaVersion
```

避免客户端升级后无法读取旧玩家数据。
