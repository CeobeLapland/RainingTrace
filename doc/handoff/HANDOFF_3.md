# RainingTrace / 雨迹 — 开发进度交接（记忆打磨 + 图层与地点完成）

> 更新时间：2026-09-19（新对话开工前请先读本文件 + doc/02_AGENTS.md + GDD_v3.md）
> 上一版：HANDOFF_2.md（战争迷雾架构完成时的状态，其中坐标/设备记录已被本次修正）

## 1. 当前一句话状态

**地图（迷雾/轨迹/地点/记忆/筛选）与记忆（日记/语音/多图/在地图查看）两条线都已真机验收闭环。**
战争迷雾、双定位、背包图鉴、图层筛选（含持久化）、未探索问号、地点详情卡、日记按天分组、相机拍照/录音/多图、记忆"在地图查看"高亮——全部落地。
**AR 已完成调研并阶段性封存**（本机不可用，见 §7）。

## 2. 本次会话完成的内容

### A. 地图层
1. **地点图标**：彩色水滴位图（颜色+类型字）替换圆点；随缩放变大；未探索地点显示灰色"?"。
2. **地点详情卡**：点图标/附近列表 → 底部悬浮卡（缩略图占位+名称/类型/距离/描述+全部动作按钮）；附近多地点 → 列表逐个选。
3. **图层筛选面板**（地图右上"筛选"）：地点类型开关 + 记忆开关 + 记忆时间（全部/今天/近一周）。**逻辑隐藏语义**（关掉的不渲染、不进附近列表、点不到）。
4. **记忆进地图**：记忆按心情着色的圆点 + 时间小字（HH:mm）。
5. **筛选持久化** → DataStore（`rt_settings`），重启保留。

### B. 记忆/日记层
6. **日记按天分组**（今天/昨天/9月17日 星期三+条数）；单测覆盖日期分组与跨日。
7. **日记「在地图查看」**：点记忆 → 跳回世界页，相机移到该坐标（zoomed 18）、琥珀高亮环、底部记忆卡。
8. **相机页重做**：全屏沉浸 + 底部圆角面板；**多张照片**（≤9，缩略图横滑/删除/大图回看）、**一段语音**（MediaRecorder 录音/回放，录音计时，<0.8s 丢弃，权限按需申请）、心情横滑选中态、无照片/无语音纯文字降级。

### C. 数据层
9. **Room v2 → v3 显式迁移**（非删库）：`memories` 加 `audioRef TEXT`；schema 已导出 `3.json`。项目首个真正的 Migration。

### D. 技术细节（下个 AI 必须知道）
- **MapLibre `Expression.match` 会校验分支标签唯一性**：把默认值放 index 2 而另一分支重复时，整个 `icon-image`/`circle-color` 属性设置失败→图层不渲染（图标消失/记忆变黑点）。**已改用"每类型/每心情一个静态图层 + `eq` 过滤"**，与迷雾层同构。别再回头用 match 做数据驱动颜色/图标。
- 地点图标类型在 GeoJSON 里写**小写枚举名**（`placeType.name.lowercase()`），静态层 filter 也用 `eq` 小写匹配。
- 跨屏「在地图查看」**不用导航参数**（地图是常驻 Tab，会与 saveState/restoreState 打架），用 `MemoryFocusRequest`（写方 request，读方 collect+consume）。
- 心情文案统一在 `core/ui/MoodLabels.kt`（`Mood.label()`）；图片解码统一在 `core/ui/LocalImage.kt`；语音条统一 `core/ui/AudioNoteChip.kt`。

## 3. 关键约定（仍然有效）

- 手写 DI（AppContainer），不用 Hilt；Compose stateless + UiState；domain 禁 Android SDK。
- 外部 SDK 全走 adapter/interface；所有时间注入 WorldClock。
- 单测 **72 个全绿**（`.\gradlew.bat :app:testDebugUnitTest`）。
- **真机：Honor 100（MAA-AN00）。多条实测记录已修正，见 §7**。
- **复杂实机交互由用户手动测试并回报**；AI 只做构建/安装/启动/单测/低阶 logcat。
- 世界原点仍是北湖参考坐标 39.7326,116.1712（待真机校准）；cellSize 默认 40m。

## 4. 数据与设置现状

- **Room v3**：`track_points`（ts 索引）/ `exploration_cells`（档位前缀主键）/ `footprint_events`（lat/lng）/ `memories`（lat/lng, **audioRef**）/ `inventory_items`。v2→v3 走 `MIGRATION_2_3`（ALTER TABLE ADD COLUMN），v1→v2 仍 destructive（历史，勿回退）。
- DataStore `rt_settings`：`grid_level`（s/m/l/xl）、`location_mode`（FAKE/GPS）、**`shown_place_types` / `show_memories` / `memory_time`**（图层筛选）。
- Manifest 已含 FINE/COARSE/BACKGROUND 定位、CAMERA、**RECORD_AUDIO**、INTERNET、POST_NOTIFICATIONS。
- 记忆数据形态：`mediaRefs`（多张，`Char(0)` 分隔）、`audioRef`（可空）。

## 5. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
# 检查迁移/Room/崩溃/地图加载：
& "D:\Android\Sdk\platform-tools\adb.exe" logcat -d -v time | Select-String "Room|Migration|FATAL|MapLibreAdapter|Mbgl"
```

## 6. NEXT（用户尚未排期，开工前先确认）

P0 打磨剩余：
1. **更多地点 + 开发者模式**：FakePlaceRepository 已收敛成"好追加的集中列表"，当前 6 地点（北湖/图书馆/食堂一/中心广场/湖心花园/宿舍3号楼）。开发者模式（可编辑地点/事件/NPC/AR）是 GDD §21 基础设施，但**建议等 P1 数据驱动 schema 再说**，本轮别顺手糊半成品。
2. **时间/天气 chip 已接线**（地图左上"晴·HH:mm"，WeatherProvider=假数据固定晴）；可接真实天气 API（P1）。
3. **地点详情卡的缩略图占位**（当前是类型色方块+字），可换真实照片。
4. **观察奖励目前只有"观察记录"一种**；世界状态影响产出（如雨天湖边掉"湖泊记忆碎片"）可作为第一个"天气影响玩法"的例子。
5. **定位体验细节**：GPS 信号质量指示；Fused 在 MagicOS 省电下回调稀疏的兜底。

P1 主干（doc/03 任务树）：Supabase 同步、真实天气、NPC 骨架、家园/宿舍、种植制作、轻经济。

## 7. 重要：AR 调研结论（本机已阶段性封存）

**技术路线全部调研完毕，写入 `doc/12_AR实现路径与阶段规划.md`。结论：**

- **本机（Honor 100 / MAA-AN00 / 国内版 ROM）真 3D 两条原生路线全不可用**：
  - ARCore：无 Play 商店 → 装不了 ARCore 运行时。
  - 华为 AR Engine：实测**无 HMS Core、无 AR Engine 服务、华为应用市场搜不到「AR测量」**，且这些不是用户能自行补上的（需系统级服务+出厂标定）。
- **本机只剩两条可用路线**：
  - **罗盘 AR**（零依赖：摄像头+方向传感器+GPS，非真 3D，约 200~300 行）。
  - **EasyAR Sense**（不依赖 ARCore/HMS，可真 3D；**个人版免费但不可商用+有水印+自定义相机每次 100 秒**；专业版 ¥258/月、经典版 ¥8999）。**是本机唯一真 3D 希望，若做需先跑官方 Sample 实测**。
- 现有 `ArCoreController` + `ArScreen` 的"屏幕投影 + Compose 精灵叠加"架构是**资产**：真 3D 路线只需新增一个 `ArController` 适配器，domain/feature 不动。
- **顺带修正历史文档错误**：HANDOFF_1/2 记录该机"有 GMS"，与实际（无 Play、无 HMS）不符，**以实测为准**。

## 8. 代码地图（要点）

```
feature/
  camera/CameraScreen|CameraViewModel（相机页重做：多图+录音+心情横滑）
  journal/JournalScreen|JournalViewModel|JournalRoute（按天分组、语音回放、在地图查看）
  map/MapScreen|MapViewModel|WorldStatusViewModel（地点详情、筛选面板、聚焦高亮、时间天气）
  profile/MeScreen（背包/图鉴入口）、inventory/…（图鉴）
platform/
  audio/AndroidAudioNoteController（MediaRecorder/MediaPlayer）
  map/MapLibreAdapter（每类型静态图标层 + 记忆心情层 + 聚焦环 + 时间小字）
domain/
  memory/MemoryTimeline（分组）、MemoryFocusRequest（跨屏）、AudioNoteController、CreateMemoryUseCase（多图+audio）
  map/MapVisuals（placeStyle/PlaceVisual.revealed/MemoryVisual），settings/MapFilterSettings
core/ui/ LocalImage、MoodLabels、AudioNoteChip
data/local/ Database(v3, MIGRATION_2_3)、Entities(MemoryEntity.audioRef)
```

## 9. 坑/风险清单（下个 AI 别再踩）

- **MapLibre 不要用数据驱动 `match` 做 icon-image / circle-color**（分支标签唯一性校验会让整层失败）；用"每类型静态层 + eq 过滤"。
- MapLibre style 异步换代期 `getSourceAs` 抛 IllegalStateException → `safeSource` + pending 重放；同一地图不要重复 setStyle；MapView 生命周期手动驱动。
- 地点图标 GeoJSON 属性与 filter 都用**小写枚举名**，大小写不一致会导致图标全灰或消失。
- 切 Tab 地图 dispose/重建后靠 VM.refresh() + attach 代际 pending 重放；聚焦环要在 refresh 里补画。
- **Room 改 schema 必须写 Migration**（v2→v3 已示范）；新增列记得同步 schema 导出与仓储映射。手动 `ALTER` 与 schema 3.json 不一致会崩。
- 相机离开组合（含"摄像 Tab 内切到 AR 模式"）必须显式 unbind CameraX（同一 NavBackStackEntry 仍 RESUMED，会与 ARCore 抢相机）；语音离开页面要停录音/回放。
- MediaRecorder `stop()` 未 start 或过短会抛异常，AndroidAudioNoteController 已用 runCatching 包裹并丢短录音。
- `LIKE :prefix` 需 SQL 侧拼 `%`；迷雾挖洞已证伪用分块掩膜。