# P0 打磨 · 第一刀：导航骨架（顶部窄栏 + 底部五栏）实施计划

## Repository Research

### 用户确认的范围

- 先只做导航，不做地点详情/天气/背包/相机重设计（那些是后续刀）。
- 底部导航五项：**世界、家、摄像、消息、我的**。
- 点「摄像」直接打开相机；现有「拍」和「AR」收进摄像界面，成为可切换的两种模式（拍照 / AR）。
- 「日记」收进「我的」里，作为子页面。
- 视觉方向：雨后手账风（本刀只给壳打底，不逐屏翻新）。

### 现状关键事实（已核实）

- 导航是 [MainActivity.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/MainActivity.kt) 里的 `enum Route` + `when` 硬切；`BackHandler` 分散在各路由包装里。
- `androidx.navigation.compose` 依赖**已在 build.gradle.kts 声明但全工程未使用**（grep 仅命中注释）——启用它不算引入新框架。
- 地图屏 [MapScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapScreen.kt) 右上角挂着三个文字 FAB（AR / 记 / 拍），左上角是调试卡（裸 cellId +「点击地图=移动」）。
- 相机屏 [CameraScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/camera/CameraScreen.kt) 自带权限流、CameraX 绑定（bindToLifecycle 跟随 composition 的 LifecycleOwner）、拍照→文字/心情→保存流程。
- AR 屏 [ArScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/ar/ArScreen.kt) 自带 GLSurfaceView 生命周期管理、`BackHandler(onDone)`、左上角「← 返回」TextButton；本设备实际走 UNSUPPORTED 降级卡。
- 日记屏 [JournalScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/journal/JournalScreen.kt) 无自己的顶栏，返回按钮在 MainActivity 的包装里（悬浮 TextButton）。
- `feature/home`、`feature/messages`、`feature/settings`、`feature/inventory` 均为空目录（仅 .gitkeep）。
- [AppContainer.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/common/AppContainer.kt) 手写 DI，目前 `locationProvider` 固定为 Fake；尚无 Fake 开关（RT-BOOT-006 后续刀做）。
- [Theme.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/ui/theme/Theme.kt) 只有默认 Material You 动态取色，无品牌色/字体/形状。
- ViewModel 当前全部 activity 级；MapViewModel 有 `refresh()`，从其他屏返回地图时由 MainActivity 手动调用（重排探索状态并补渲染）。
- MapView 在路由切换时本来就会 dispose/recreate（现有 `when` 切换已验证此模式），MapLibreAdapter 的 attach 代际 + pending 重放机制覆盖重建竞态。
- 真机交互由用户手动验收，AI 只跑构建/测试/安装命令（HANDOFF 约定）。

## Files and Modules

### 新增

- `core/ui/theme/Color.kt`：雨后手账配色（米纸底、墨青文字、苔绿/雨青主色、琥珀点缀）。
- `core/ui/theme/Type.kt`：标题用系统 serif、正文默认无衬线的手账感字阶。
- `feature/shell/Destinations.kt`：路由常量与五个一级 Tab 定义。
- `feature/shell/RainingTraceApp.kt`：Scaffold（顶部窄栏 + 底部导航）+ NavHost + 各屏 ViewModel 装配。
- `feature/home/HomeScreen.kt`：宿舍占位空状态（手账风卡片，明确"以后会来"）。
- `feature/messages/MessagesScreen.kt`：痕迹/消息占位空状态。
- `feature/profile/MeScreen.kt`：简单头部 +「我的日记」入口。
- `feature/camera/CameraRoute.kt`：摄像 Tab 外壳——关闭钮 +「拍照 / AR」分段切换；内嵌现有 CameraScreen 与 ArScreen。
- `feature/journal/JournalRoute.kt`：Scaffold + 顶栏（标题「我的日记」+ 返回）包现有 JournalScreen。
- `res/drawable/ic_tab_world.xml`、`ic_tab_home.xml`、`ic_tab_camera.xml`、`ic_tab_messages.xml`、`ic_tab_me.xml`、`ic_rain_mark.xml`、`ic_close.xml`、`ic_chevron_right.xml`、`ic_back.xml`：自绘极简线性矢量图标（零新依赖）。

### 修改

- `core/ui/theme/Theme.kt`：接入 Color/Type/Shapes，`dynamicColor` 默认改为 `false`（品牌色优先）。
- `core/common/AppContainer.kt`：暴露 `val useFakeLocation: Boolean = true` 常量（RT-BOOT-006 时替换为 DataStore 设置，不新建设置页）。
- `MainActivity.kt`：删除 enum 路由与 AppRoot，改为调用 `RainingTraceApp(container)`。
- `feature/map/MapScreen.kt`：删除三个 FAB 及 `onTakePhoto/onOpenJournal/onOpenAr` 参数；左上调试卡改为两个克制的小浮片——探索格数印章（常驻）+「Fake · 点击地图移动」提示（仅 `useFakeLocation=true`）；增加 ON_RESUME 时 `viewModel.refresh()`，替代旧的手工回调刷新。
- `feature/ar/ArScreen.kt`：删除自带「← 返回」TextButton（关闭由摄像外壳承担）；`BackHandler` 保留，语义=退出摄像 Tab。
- `feature/journal/JournalScreen.kt`：仅调整内容 padding 以适配 Route 顶栏，逻辑不动。

## Implementation Steps

1. **主题基础**：Color.kt / Type.kt / Theme.kt 改造（含 Shapes 圆角卡片），`dynamicColor=false`。此步只定义令牌，不逐屏改样式。
2. **图标资源**：9 个自绘 vector（24dp 线性，tint 跟随主题）。
3. **AppContainer**：加 `useFakeLocation` 常量。
4. **Shell 骨架**：Destinations + RainingTraceApp。
   - 路由：`world / home / camera / messages / me / journal`。
   - 一级页显示顶部窄栏（雨滴标记 + 分区名：世界/家/消息/我的）与底部 NavigationBar；摄像为中间强调项（圆形填充图标）。
   - `camera`、`journal` 隐藏底栏；`camera` 全屏沉浸（连顶栏也隐藏，自带悬浮控件）；`journal` 显示带返回的顶栏。
   - 记住离开摄像前的最后一个一级 Tab（默认 world），供关闭摄像后返回。
   - ViewModel 改为挂在各自 NavBackStackEntry 上（`viewModel { }`）：Map VM 随 world entry 存活；Camera VM 进入时 `reset()`（沿用现有逻辑）。
5. **一级目的地**：
   - world：迁入现有 MapScreen。
   - home / messages：占位空状态（图标 + 一句话 + 后续预告文案，不做死路感）。
   - me：头部 +「我的日记」列表项 → `navigate("journal")`。
6. **MapScreen 改造**：去 FAB、调试卡换浮片（Fake 提示受 `useFakeLocation` 控制）、ON_RESUME 刷新。
7. **CameraRoute**：
   - 顶部悬浮：左关闭（×）、居中分段控件「拍照 | AR」；底部留白不遮挡现有按钮。
   - 拍照模式：现有 CameraScreen 原样嵌入；保存成功 → 退出摄像回上一个一级 Tab。
   - AR 模式：内嵌现有 ArScreen（传 onDone = 关闭摄像）；其自带返回钮删除，模式切换/关闭都在外壳。
   - 路由级 BackHandler：两模式下系统返回都=关闭摄像回上一个一级 Tab。
8. **JournalRoute**：顶栏返回 `popBackStack()` 回「我的」；删除 MainActivity 里旧的悬浮返回包装与手工 refresh 包装。
9. **自检与交付**：构建 + 单测；安装到真机；输出手动验收清单给用户。

## Dependencies and Considerations

- 不新增任何依赖：Navigation Compose 已声明；图标全部自绘 vector；字体用系统 serif。
- 遵守工程宪法：feature → domain 依赖方向不变；本刀零领域逻辑变更，不动 Room schema、不改权限模型、不碰外部 SDK 边界。
- CameraX `bindToLifecycle` 跟随 CameraScreen 所在 NavBackStackEntry 生命周期：切走 Tab 自动 ON_STOP 解绑，回来重绑（现有 LaunchedEffect 已处理重绑）。
- AR 的 ArCoreController 是 AppContainer 懒加载单例，Session 在首次进 AR 模式时才构造，与现状一致；模式间切换靠 ArScreen 既有的 DisposableEffect pause/resume。
- MapView 重建竞态是已知坑（HANDOFF）：style 未就绪时渲染数据进 pending，attach 后 flushPending 重放；ON_RESUME refresh 复用这条安全路径。
- 文案沿用代码库现状（界面中文直接内联，不进 strings.xml）。

## Validation

- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 全绿（约 60 个既有 JVM 单测不回归）。
- 安装真机后用户手动验收：
  1. 五个 Tab 可切换，顶栏标题对应；中间「摄像」视觉强调。
  2. 世界页：无旧 FAB；探索格数浮片正常；Fake 提示可见且点击地图移动仍生效。
  3. 摄像 Tab 直接进相机；拍照→写字/心情→存入日记后自动回上一个 Tab。
  4. 摄像内切「AR」模式：本设备出现降级卡（不黑屏）；切回拍照正常。
  5. 我的 → 我的日记：列表正常、顶栏返回、系统返回键均回「我的」；刚存的记忆可见。
  6. 家 / 消息：占位页不崩溃。
  7. 各页系统返回键行为正确；杀进程重启数据还在。
- 无领域逻辑变更，不新增单测。

## Risks

- **切 Tab 后地图迷雾空白**：MapView  dispose 重建期间渲染可能丢失 → ON_RESUME `refresh()` + adapter pending 重放双保险；真机验收重点看这一项。
- **摄像切走后相机未释放**：风险低（bindToLifecycle 机制保证），验收时留意切 Tab 后相机指示灯/占用。
- **NavigationBar 中间摄像项做异形凸起 FAB 的布局风险**：本刀不做异形，用圆形填充强调样式，稳妥且风格统一。
- **范围蔓延**：地点详情、天气、背包、相机全屏重设计均为后续刀；本刀只做搬迁与壳，发现不顺手只记录不扩做。
