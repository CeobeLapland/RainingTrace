# RainingTrace / 雨迹 — 测试、验收与 Definition of Done

## 1. 测试金字塔

```text
              Manual / Device
                 ▲
           Instrumentation
                 ▲
            Repository
                 ▲
              Domain
```

P0 逻辑尽量在 Domain 测试解决。

---

## 2. Unit Test 必须覆盖

### 时间

- 跨天
- 跨季节
- DST/时区边界（涉及真实时间时）

### 定位

- 低精度
- stale sample
- GPS jump
- stationary
- fast movement

### 探索

- cell reveal
- duplicate reveal
- revisit
- boundary cell

### 资源

- 条件不满足
- 资源已采过
- 稀有资源
- cooldown

### 经济

- 钱不足
- 重复提交
- 负数输入
- 并发购买

### 记忆

- 保存
- 修改
- 删除
- 上传失败重试

### 世界事件

- start
- active
- end
- seed reproducibility

---

## 3. Fake-first 测试

必须能用 Fake 完成：

```text
FakeLocation
FakeWeather
FakeClock
FakeWorld
FakeRepository
FakeCamera
FakeAR
```

不应要求真机 GPS 才能测试“探索规则”。

---

## 4. UI Test

P0 至少：

1. 启动
2. 地图加载
3. 点击地点
4. 执行动作
5. 背包刷新
6. 写记忆
7. 返回地图
8. 重新打开数据保持

---

## 5. Manual device test

真实 Android 手机上验证：

- 权限
- 定位
- 旋转屏幕
- 后台/前台
- 相机
- AR
- 网络断开
- 电量低
- GPS 精度变化

---

## 6. Definition of Done

任务只有满足以下条件才能标记完成：

### Code

- [ ] 编译
- [ ] lint/format
- [ ] 无明显 dead code
- [ ] 无 secret
- [ ] 无未处理 coroutine error

### Architecture

- [ ] Domain 无平台依赖
- [ ] 外部 SDK 通过 adapter
- [ ] 没有新架构绕过 AGENTS

### Test

- [ ] happy path
- [ ] failure path
- [ ] boundary case

### Product

- [ ] Acceptance criteria 全部满足
- [ ] loading / empty / error 有处理
- [ ] offline 方案存在或明确不支持

### Observability

- [ ] 关键错误可从日志定位
- [ ] 不记录敏感数据

### Git

- [ ] diff 审查
- [ ] commit

---

## 7. Demo DoD

“首个可玩 Demo”不是：

> 所有功能都做了 10%。

而是：

> 一个玩家可以完成一次完整、有情绪、有反馈的循环。

---

## 8. 性能验收

P0 不追求最终性能，但必须避免：

- 主线程数据库查询
- 大量 Compose 重组
- 每次 GPS 更新刷新整个地图
- 每个 POI 都创建独立重组件
- Camera/AR 生命周期泄漏

---

## 9. Bug 优先级

### S0

数据损坏 / 大规模隐私泄漏 / 无法启动。

### S1

P0 核心循环无法完成。

### S2

重要功能错误但存在 workaround。

### S3

一般 UX/视觉错误。

### S4

纯 polish。
