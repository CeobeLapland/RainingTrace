# RainingTrace / 雨迹 — 开发进度交接（HANDOFF）

> 更新时间：2026-09-13（新上下文开工前请先读本文件 + doc/02_AGENTS.md）
> 产品愿景：GDD_v3.md；工程宪法：doc/ 目录（Vibe Coding 文档包）

## 1. 当前状态一句话

MVP（doc/11 首个可玩切片）的 P0 硬指标已完成约 80%：真机（Honor 100, MAA-AN00）上可玩闭环
"走到湖边 → 揭雾 → 发现地点 → 观察得资源 → 拍照/写字条成记忆 → AR → 重启数据还在" 已验证。
**唯一没做的 MVP 硬指标是真实定位（现在靠"点击地图=移动"的 Fake 定位）。**

## 2. 已完成（按提交顺序）

| 提交 | 内容 | 对应任务树 |
|---|---|---|
| 90d7313 | HexCellId/WorldCoordinate/HexGrid(axial投影/邻居/半径/多边形)、WorldClock、WeatherState、ExplorationState、RevealNearbyCellsUseCase | RT-DOM-001/002/008/009、RT-MAP-004/005 |
| 34fbe6b | Place、ResourceDefinition、InventoryState+Add/Remove、FootprintEvent、MemoryNode、FakePlaceRepository(北湖) | RT-DOM-003~007 |
| 6d139bd | Room v1（exploration_cells/footprint_events/memories/inventory_items，schema 已导出 app/schemas/1.json） | P0 Persistence |
| 0e11305+2b4afc7 | MapLibreAdapter(GeoJSON 六边形+迷雾分层)、FakeLocationProvider、AppContainer 手写DI、MapScreen/MapViewModel、OpenFreeMap 底图、MapView 手动生命周期 | RT-BOOT-003、RT-MAP-001~003/006、RT-LOC-001/002 |
| 1cd9e35 | ObservePlaceUseCase(120m范围/10min冷却/发奖/写足迹)、Room三仓库、地点标记渲染、底部地点卡片+观察按钮 | RT-PLACE-001~005 |
| fa387f9+7fd1e5b | 修复切页崩溃（Style 换代竞态：safeSource try-catch+pending 重放+attach 防重）；相机绑定修复（PreviewView 移出 factory）；系统返回键 BackHandler | — |
| 8114fcb | 日记照片缩略图（BitmapFactory 降采样，无新库）；AR-0：ArController 接口+状态机、ArCoreController(GL相机背景+平面hitTest放置+屏幕投影精灵)、ArScreen、地图「AR」入口 | RT-MEM-002、RT-AR-001~004 |

测试：约 60 个 JVM 单测全绿（`.\gradlew.bat :app:testDebugUnitTest`）。
真机已验证：地图/迷雾/观察/拍照/记忆/日记/重启持久化/切页返回。

## 3. 关键决策与约定（新 AI 必须遵守）

- **手写 DI**（AppContainer），不用 Hilt（人工确认 2026-09-13）
- 底图：`https://tiles.openfreemap.org/styles/liberty`（免费无 key；正式版要换，P1 议题）
- 世界原点：北湖 `39.7326, 116.1712`（公开资料参考值，**待真机校准**）；cellSize=80m，REVEAL_RADIUS=2，RENDER_RADIUS=6
- MapLibre 坑：①MapView 无 Lifecycle 对象，要手动驱动 onStart/onResume/onPause/onStop + onCreate(null)；②Style 异步换代期间 `getSourceAs` 抛 IllegalStateException，必须走 `safeSource()` 捕获并暂存 pending；③同一 MapLibreMap 不要重复 setStyle
- 导航：单 Activity + `Route` 枚举（MAP/CAMERA/JOURNAL/AR），未引 Navigation 库；子屏必须加 `BackHandler`
- 冷却目前是内存态（重启重置）；发奖目前客户端本地结算（MVP 无后端，P1 移服务端）
- 真机测试分工：**复杂实机交互由用户手动测试**，AI 只跑 logcat/安装类单命令（用户明确要求）
- 手机：Honor 100（MAA-AN00，MagicOS）。adb 在 `D:\Android\Sdk\platform-tools\adb.exe`；USB 安装会弹确认，需用户在手机上点允许
- 设备未装 ARCore（GMS 在），AR 屏当前走"不支持"降级卡片（这本身是验收点）

## 4. 没做完的

### MVP P0 收尾（下一刀）
1. **RT-LOC-003/004/005 真实定位**：AndroidLocationProvider（FusedLocation，无 GMS 降级 LocationManager）+ accuracy/新鲜度过滤 + 与 Fake 可切换（AppContainer 里换实现即可）。做完"点击=移动"退为调试功能
2. RT-MEM-005 日记时间线已有雏形；缺"从记忆跳回地图位置"
3. 简单背包/图鉴入口（让"观察记录"可见）——小工作量
4. Fake mode 开关（RT-BOOT-006）：设置页切换 Fake/真实定位

### 之后（doc/03 的 P1）
server sync（Supabase）、真实天气、NPC 骨架、家园/宿舍、种植制作、轻经济——按任务树顺序。

### 待决策
- **AR 替代方案**（本设备无 ARCore）：见下节"AR 路线"
- 湖坐标校准；OpenFreeMap 替换供应商；RLS/服务端权威（P1 开始时）

## 5. AR 路线（设备不支持 ARCore 时的选择）

1. **侧载 ARCore APK 试一下（成本≈0，先做这个）**：设备有 GMS。装 "Google Play Services for AR"（APKMirror 取最新版）。我们的代码零改动：装上且 Session 创建成功 → 现有平面放置直接可用；若设备未认证被拒 → 仍走降级卡片
2. **手搓"罗盘 AR"（推荐兜底，难度中低）**：CameraX 预览（已跑通）+ SensorManager ROTATION_VECTOR 求方位/俯仰 + GPS 目标点→方位角投影→Compose 精灵（ArScreen 的叠加模式现成）。效果=精灵"钉在现实方向上"，转头它跟着移动；无平面/无距离感/罗盘有磁干扰漂移。约 200~300 行，一个切片能做完，正好也是 AR-1（地点绑定 AR）的雏形
3. Honor/Huawei AR Engine：新 SDK + 适配层，L1 决策，不推荐现在碰
4. 8th Wall/WebXR：WebXR 的 AR 模块底层同样要 ARCore，绕不开；商业 SDK 成本高
5. 手搓 SLAM/深度：不要做

结论：先 ①（十分钟验证），不行做 ②（"大概能用"完全够 MVP 的"AR 是出口"体验验证）。

## 6. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -v brief | Select-String "MapLibreAdapter|ArCoreController|CameraScreen|AndroidRuntime|FATAL"
```

## AR 替代方案（按推荐顺序）
① 侧载 ARCore 试一下 —— 成本几乎为零，先做这个 你手机有 GMS（Google 服务框架），只是没装 "Google Play Services for AR"（ARCore）。从 APKMirror 下载对应你机型的最新版装上， 我们的代码一行都不用改 ——`Session` 创建成功，现有的平面放置立刻能用。风险是 Honor 未通过 Google 认证的设备可能被 ARCore 拒绝（那样还是走降级卡片，不亏）。

② 手搓"罗盘 AR" —— 你问的"大概能用"就是这个，难度中低 原理：CameraX 相机预览（我们已经跑通了）+ 手机罗盘（旋转矢量传感器）+ GPS。把你的位置和目标点（比如北湖某坐标）算出方位角，转头时精灵"钉"在那个方向上。

- 能做到：虚拟生物/物品"站在现实的方向和地点上"，转头它跟着移动，走近它变大
- 做不到：贴地、遮挡、精确锚定（罗盘有 ±5~15° 磁干扰漂移，距离靠 GPS 也就 ±10m）
- 工作量：约 200~300 行，一个切片（1 个 domain 接口 + 1 个传感器 provider + ArScreen 加一种渲染模式）。现有 ArScreen 的"Compose 精灵叠加"架构可以直接复用，等于把"点击平面放置"换成"按方位角投影"
- 额外好处：这正好是文档里 AR-1（地点绑定 AR）的雏形，不是白做的
③ Honor AR Engine —— 华为系自己的 AR SDK，能力接近 ARCore，但要新增第三方 SDK + 适配层，属于 L1 架构决策，现在不值得碰。

④ WebXR/8th Wall —— 底层同样依赖 ARCore，绕不开，还引入 WebView，不推荐。

⑤ 手搓 SLAM/深度估计 —— 别做，那是"高级"而不是"大概能用"。
