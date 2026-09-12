# RainingTrace / 雨迹 — MVP 首个可玩切片

## 1. 唯一目标

证明下面这句话成立：

> **我走到一个现实中的地方，游戏发现我到了；我做了一件事；世界给我反馈；我留下了一段属于自己的痕迹；下次回来，这个世界还记得。**

---

## 2. 场景

地点固定为：

> 一个校园里的湖。

这是因为湖同时支持：

- 真实地点
- 水体视觉
- 钓鱼未来扩展
- 天气差异
- 时间差异
- 生物
- AR
- 记忆

---

## 3. 玩家流程

```text
打开应用
 ↓
看到校园湖附近地图
 ↓
六边形迷雾覆盖
 ↓
玩家走向湖边
 ↓
地图逐步揭开
 ↓
出现“湖泊”地点
 ↓
地点状态：可观察
 ↓
玩家点击「观察」
 ↓
发现“雨后水面异常反光”
 ↓
获得观察记录
 ↓
玩家拍一张照片
 ↓
创建记忆节点
 ↓
获得一枚“湖泊记忆碎片”
 ↓
开启 AR
 ↓
湖边出现一只虚拟小生物
 ↓
玩家拍摄/互动
 ↓
退出
 ↓
再次打开
 ↓
记忆还在，湖泊图鉴进度还在
```

---

## 4. P0 必做

### 地图

- MapLibre
- 湖区中心
- 六边形 overlay
- Fog

### 位置

- Fake location
- real location adapter
- accuracy filter

### 地点

- Lake
- Place detail
- Observe action

### 资源

- ObservationRecord
- MemoryFragment

### 记忆

- Photo
- Mood
- Tags
- Text
- Location

### AR

- one object
- local placement

### Persistence

- Room
- app restart data preserved

---

## 5. P0 不做

- 真实天气 API
- 登录
- 云端同步
- 市场
- 交易
- NPC AI
- 完整钓鱼
- 城市地图
- 多人 AR
- 世界导演
- 稳定 Geospatial VPS

这些都是未来内容。

---

## 6. 三个必须回答的问题

### Q1：探索是否有诱因？

如果没有任何奖励，只把迷雾打开，很可能不够。

### Q2：记忆是否有情绪价值？

如果照片只是进入相册，产品没有差异化。

### Q3：AR 是否真的改变体验？

如果 AR 只是“看起来酷”，就不能成为核心。

---

## 7. Demo 成功标准

至少出现一次：

> “我本来只是去湖边，但我居然想看看下一次回来会不会不一样。”

这比：

> “技术跑通了。”

重要得多。

---

## 8. 下一阶段触发条件

如果 P0 Demo 成功：

进入 P1：

- server sync
- world state
- weather
- NPC
- home

如果 P0 Demo 不成立：

不要继续扩系统。

优先改：

- 地点行为
- 探索奖励
- 记忆表现
- AR interaction
