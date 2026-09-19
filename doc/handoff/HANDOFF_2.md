# RainingTrace / 雨迹 — 开发进度交接（战争迷雾改造完成）

> 更新时间：2026-09-14（新对话开工前请先读本文件 + GDD_v3.md）
> 上一版：HANDOFF_1.md（导航骨架完成时的状态，其中多条内容已被本次改造覆盖）
> ⚠️ 已被 **HANDOFF_3.md**（2026-09-19）取代，请优先读 HANDOFF_3；本文保留作历史记录。
> ⚠️ 本文 §3「真机…有 GMS」的记载有误：实测该机为国内版 ROM，无 Play 商店、无 HMS Core。

## 1. 当前一句话状态

**"现实是输入层"的战争迷雾架构已全部落地并真机验收通过：**
Fake 点击移动 / 真实 GPS 双定位、去噪轨迹点（唯一空间真相）、米制半径开雾、
视口分块六边形迷雾（25/40/60/100m 可配）、今日轨迹线、五栏导航 + 设置页。
**默认定位仍是 Fake**（设置里可切 GPS，室外已验证无 bug）。

## 2. 本次会话完成的两大块

### A. 应用外壳（HANDOFF_1 之后第一批）

- 雨后手账风主题（Color/Type/Shapes，米纸/墨青/苔绿/琥珀，衬线标题）
- 底部五栏：世界 / 家 / 摄像（中间强调，全屏沉浸，内拍照/AR 模式切换）/ 消息 / 我的
- 顶栏雨滴+分区名；Navigation Compose；摄像为覆盖层压在 Tab 上
- 「我的」：我的日记入口 + 设置入口
- 地图右侧浮层开关：迷雾、今日轨迹

### B. 战争迷雾四刀（S1→S4，用户逐刀真机验收）

1. **S1 定位层**：`track_points` 表（去噪后稳定点，唯一空间真相）；
   `RecordTrackPointUseCase`（精度>50m/位移<8m/速度>35m·s⁻¹/时间异常过滤；
   GPS 走全部规则，Fake 点只过时间/位移）；今日轨迹 LineString；
   记忆与足迹坐标化（不再以 cellId 为位置真相）；观察冷却改为查足迹事件（重启不重置）
2. **S2 迷雾战争化**：`cellsWithinMeters`（圆与六边形相交判定，30m 到过/60m 见过）；
   视口驱动渲染（camera idle → visibleRegion）；**分块六边形掩膜**
   （未探索深夜雾 0.82 / 见过薄雾 0.42 / 到过淡染色）；地名 SymbolLayer（字体栈复用底图）；
   地点不再写 SPECIAL 格
3. **S3 格子可配 + 地图约束**：`GridManager`（当前档位六边形实时切换）；
   设置页 25/40/60/100m 四档，换档 = 清旧档迷雾 → 从全部轨迹点重建（轨迹/地点/记忆不动）；
   地图最小缩放 14 级（硬限制，迷雾不随缩小消失）；
   相机中心世界边界（北湖 ±0.2°纬 / ±0.26°经，约 25×30km）
4. **S4 真实定位**：`AndroidLocationProvider`（FusedLocation 高精度 5s/2s，
   GMS 异常降级 LocationManager GPS+NETWORK，无权限静默不崩，只前台）；
   `SwitchableLocationProvider`（DataStore 设置热切换，Fake 保留为调试模式）；
   GPS 模式自动请求定位权限，拒绝后左上角"点此重试"chip；设置页定位方式单选

### 关键决策记录（下个 AI 必须知道）

- **轨迹点是唯一空间真相**，迷雾格子只是可随时重建的投影；位置（地点/记忆/足迹/玩家）一律连续经纬度
- fog 表主键带档位前缀：`gm:12:-7`（`g<levelKey>:<q>:<r>`），Room v2，旧 v1 数据已 destructive（用户当时拍板"删库重来"）
- 迷雾渲染**没有用**大矩形挖洞（maplibre geojson 环方向在 Java 序列化/native 间表现异常，踩过 SIGABRT），
  最终方案是视口内逐格填充——不要回头试挖洞
- style 刚加载时 `visibleRegion` 可能返回垃圾值（纬度 -101），在 native style 回调里抛异常会 abort；
  `emitViewport` 必须先做合法性校验
- 格间缝隙距离 = (√3−1)·size（40m 格为 34.6m），不是格中心距 √3·size；开雾几何按"圆与多边形相交"算
- 去噪对 Fake 源豁免精度/瞬移规则（用户点地图是可信输入，快速连点要能瞬移）

## 3. 关键约定（仍然有效）

- 手写 DI（AppContainer），不用 Hilt；Compose stateless + UiState；domain 禁 Android SDK
- 外部 SDK 全走 adapter/interface；所有时间注入 WorldClock
- 单测 69 个全绿（去噪/米制开雾/重建幂等/换档/冷却持久化等纯 JVM 覆盖）
- 真机：Honor 100（MAA-AN00，MagicOS/Android 16，有 GMS），设备未装 ARCore（AR 屏走降级卡）
- 手机/电脑 adb：`D:\Android\Sdk\platform-tools\adb.exe`
- **复杂实机交互由用户手动测试并回报**（AI 只做构建/安装/启动 logcat，不做密集自动点击；
  MapLibre 双击缩放手势会吞 500ms 内的连点，自动 tap 测试不可靠）
- 世界原点仍是北湖参考坐标 39.7326,116.1712（待真机校准）；cellSize 默认 40m

## 4. 数据与设置现状

- Room v2：`track_points`（ts 索引）/ `exploration_cells`（档位前缀主键）/
  `footprint_events`（lat/lng）/ `memories`（lat/lng）/ `inventory_items`
- DataStore `rt_settings`：`grid_level`（s/m/l/xl，默认 m）、`location_mode`（FAKE/GPS，默认 FAKE）
- Manifest 已声明 FINE/COARSE/BACKGROUND 定位权限；**代码只前台采集，不申请 BACKGROUND**

## 5. 常用命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug   # 测试+构建
& "D:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

## 6. NEXT 建议（用户尚未排期，开工前先确认）

P0 打磨（原 doc/11 切片之外已浮现的缺口）：

1. **背包/图鉴入口**：观察北湖得到的"观察记录"目前无处可看（inventory 已有数据与仓储，缺 UI）
2. **地点详情卡**：点北湖标记 → 详情/观察冷却/距离；现在只有靠近 150m 弹底部卡
3. **记忆打磨**：日记时间线日期分组；记忆详情"在地图查看"（坐标已有，落点+亮区）；
   相机页重做（心情横滑、选中态、缩略图回看、无照片文字记忆）
4. **世界状态**：时间/天气 chip（WeatherState/FakeWeatherProvider 早已存在但未接线）
5. **更多地点**：FakePlaceRepository 仍只有北湖；可按校区加图书馆/食堂等验证多地点迷雾/标记
6. **定位体验细节**：GPS 信号质量指示；Fused 在 MagicOS 省电下若回调稀疏需加兜底（用户实测暂未反馈）
7. AR：HANDOFF_1 的罗盘 AR 兜底仍未做（设备 ARCore 缺失，降级卡是现状）

P1 再说：Supabase 同步、NPC 骨架、种植/制作、真实天气 API。

## 7. 主要代码地图

```
domain/
  track/      TrackPoint, RecordTrackPointUseCase, RevealFogFromPointUseCase,
              RebuildFogFromTrackUseCase, ChangeGridLevelUseCase
  map/        HexGrid（cellsWithinMeters/cellsInRect）、GridSpec（GridLevel 档位）、
              GridManager（当前档位持有者）、MapVisuals（MapViewport/adapter 接口）、
              LocationProvider(LocationSource)
  settings/   AppSettingsRepository, LocationMode
  exploration ObservePlaceUseCase（冷却查足迹）
  memory/     CreateMemoryUseCase（gridManager 现算 MEMORIZED 格）
data/
  local/      Room v2（Entities/Daos）  repository/  各 Room 仓储 + settings/DataStoreSettingsRepository
platform/
  location/   Fake / Android(Fused+降级) / Switchable
  map/        MapLibreAdapter（视口回调、三级雾色、轨迹线、地名、缩放与世界边界）
feature/
  shell/      RainingTraceApp（NavHost 五栏+journal+settings+camera）
  map/        MapScreen + MapViewModel（轨迹/视口/权限状态机）
  settings/   SettingsScreen + VM（格子档位、定位方式）
  camera/ar/journal/home/messages/profile 同前
```

## 8. 风险/坑清单（新对话别再踩）

- 迷雾挖洞多边形方案已证伪，用分块掩膜
- `LIKE :prefix` 必须 SQL 侧拼 `%`（`LIKE :prefix || '%'`），曾因少通配符导致重启迷雾丢失
- MapLibre style 异步换代期 getSourceAs 抛 IllegalStateException 要 safeSource + pending 重放
- 同一地图不要重复 setStyle；MapView 生命周期手动驱动（无 Lifecycle 对象）
- 切 Tab 地图 dispose/重建后靠 VM.refresh() + adapter attach 代际 pending 重放
- Room 改 schema 需显式 migration；当前 `fallbackToDestructiveMigration(false)` 仅限开发期
- 新增 DB 表/列时记得更新测试里的 fake 仓储接口（AppSettingsRepository、TrackRepository、ExplorationRepository 都在近期加过方法）
