# 美术图标管道：assets 约定 + 占位回退 —— 实施计划

## Context

现在全项目的"图标"都是代码里的占位：地图上的地点针是 [MapLibreAdapter.placePinBitmap()](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/platform/map/MapLibreAdapter.kt#L662-L692) **现场画出来的水滴位图**（颜色 + 一个汉字），NPC 头像是稳定色块 + 姓氏首字（[NpcAvatar.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/ui/NpcAvatar.kt)），物品图标是按**类别**给的一个汉字（`categoryGlyph`）。`assets/` 下只有 `content/`，全项目没有任何图片库（无 Coil/Glide）。

用户要**一次性把地点/NPC/物品/配方图标画完**，并批量补充内容。目标：**美术只需往 `assets/` 丢图片（必要时改 JSON），不必改 Kotlin**；且**缺图零崩溃、自动回退到现有占位**。

已确认的三个方向：
1. **地点图标按 `PlaceType` 配**（不是按具体地点）——符合地图惯例，地图图层结构不动。
2. **`assets/` + 名字约定 + 占位回退**——加内容不必改代码。
3. **先接管道，再批量画**——先给死命名与画布规格，避免画完对不上。

本轮**不**做（有意）：给 JSON 加 `icon` 字段（约定即接口，加字段等于多一处会漂移的真相）；缺图清单 UI（缺图在界面上就表现为"还是汉字/色块"，对单人美术就是最直观的清单）；高分辨率管线（依赖 MapLibre 的 pixelRatio 行为，见下）。

## 画布规格与命名约定（**这是给美术的接口，先看这段**）

| 用途 | 路径 | 驱动 | 尺寸 | 约束 |
|---|---|---|---|---|
| 地图·地点针 | `assets/art/pin/<placetype小写>.png` | `PlaceType` | 72×94 | **针尖必须在画布底边中点、贴边**（`iconAnchor=BOTTOM`，留透明边针尖就会飘）；带透明通道 |
| 地图·未探索针 | `assets/art/pin/_unrevealed.png` | 固定 | 72×94 | 同上 |
| 地图·NPC 针 | `assets/art/pin/npc_stay.png` / `npc_walk.png` | 姿态 | 72×94 | 同上 |
| 地点缩略图 | `assets/art/place/<placetype小写>.png` | `PlaceType` | 72×72 | 被裁成 14dp 圆角，主体别贴边 |
| NPC 头像 | `assets/art/npc/<npcId>.png` | npcId | 72×72 | 被裁成 1/4 边长圆角，主体别贴边 |
| 物品图标 | `assets/art/item/<resourceId>.png` | resourceId | 72×72 | 方形 |

- 例：`art/pin/lake.png`、`art/pin/mushroom_patch.png`、`art/place/library.png`、`art/npc/npc.lin.png`、`art/item/res.rope.png`（文件名就是 id 原样，**含点**，别替换成下划线）。
- 格式 **PNG / ARGB_8888**。**先按上表的 1x 尺寸出图**——这与今天 `Bitmap.createBitmap(72,94)` 的渲染尺寸完全一致，行为像素级不变。
- **保留矢量/分层源文件**。将来要提清晰度，只需改一个常量 + 真机核对（MapLibre 的 `iconSize` 是密度相关的，`Bitmap.getDensity()` 会改变 `pixelRatio`；3x 图若不处理密度会**放大 3 倍**。这是本轮刻意不碰的坑）。
- 加**新 `PlaceType`**（商店/车站/医院…）或新 `ResourceCategory` **仍需改 Kotlin**（`when` 是穷尽的 → 编译期报错，响亮），加完自动多一张图位。

## 实施（4 步，每步可独立验证）

### 第 1 步：命名约定的纯函数（无 Android，可单测）

新增 `domain/art/ArtPaths.kt`：把上表的路径拼法集中成唯一真相。放 domain 的理由与既有 [MapVisuals.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/map/MapVisuals.kt) 完全一致（那里已经住着 `placeStyle` 的配色/字形，注释写着"将来换美术只改这一个函数"）；且它是**零 Android 依赖**的纯函数，能直接单测。

```kotlin
object ArtPaths {
    fun pin(type: PlaceType): String
    fun place(type: PlaceType): String
    fun unrevealedPin(): String
    fun npcPin(walking: Boolean): String
    fun npcAvatar(npcId: String): String
    fun item(resourceId: String): String
}
```

测试 `ArtPathsTest`（纯 JVM）：断言拼出来的名字与约定逐字一致（`pin(LAKE) == "art/pin/lake.png"` 等）。这条测试就是"命名约定"的守门人。

### 第 2 步：读取抽象 + assets 实现

- `core/art/ArtSource.kt`：`interface ArtSource { fun bitmap(path: String, targetPx: Int): Bitmap? }`（**读不到返回 null，绝不抛**）+ `val LocalArtSource = compositionLocalOf<ArtSource?> { null }`。
  为什么接口在 core：core 本来就不是 Android-free（[LocalImage.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/ui/LocalImage.kt) 直接用 `BitmapFactory`）。
- `platform/art/AssetArtSource.kt`：`context.assets.open(path)` → `readBytes()` → `decodeByteArray`（**先测尺寸再降采样**，与 `LocalImage.decode` 同口径）+ `LruCache`（按 byteCount 计，key = `"path@targetPx"`；**永不 recycle**——地图的 native style 会持引用）。
  读 assets 是 Android 专有，与 `MapLibreAdapter`/`AndroidLocationProvider` 同归 platform。
  注意：assets 流不能读两遍做 bounds，所以先 `readBytes()` 再 `decodeByteArray` 两遍（Android 已知流坑）。
- `core/ui/ArtIcon.kt`：`@Composable fun ArtIcon(path: String?, contentDescription: String?, modifier: Modifier, targetPx: Int = 96, fallback: @Composable () -> Unit)`。内部 `LocalArtSource.current` + `produceState(path, targetPx)` + `Dispatchers.IO`（照 `LocalImage` 的写法）；**path 为 null 或解码失败 → 直接渲染 `fallback()`**。
- `AppContainer`：加 `val artSource: ArtSource by lazy { AssetArtSource(appContext) }`。
- `RainingTraceApp(container)`（[唯一调用点](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/MainActivity.kt#L17)）根部 `CompositionLocalProvider(LocalArtSource provides container.artSource)`。用 CompositionLocal 而不是层层传参：`PlaceThumb` 是 `MapScreen` 里的私有深层组件，传参要穿透详情卡/附近列表；而**默认值是 null**，所以任何没 provide 的场景（预览、未来的 UI 测试）自动回退、零崩溃。

### 第 3 步：Compose 侧四处接入（旧占位当 fallback）

把下面每处改成"`ArtIcon` + 原代码当 fallback"，**`placeStyle` / `npcStyle` / `categoryGlyph` 一行都不动**（既是回退，也保住 `PlaceVisualsTest`/`NpcVisualsTest` 对"每个类型都有字形"的断言）：

| 位置 | 改法 |
|---|---|
| [MapScreen.PlaceThumb](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapScreen.kt#L939-L955)（详情卡 L748、附近列表 L915） | `ArtIcon(ArtPaths.place(type), …)`，fallback = 现在的色块+汉字 |
| [MapScreen.PlaceTypeChip](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapScreen.kt#L1056-L1079)（筛选面板圆点） | 同上 |
| [NpcAvatar](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/ui/NpcAvatar.kt)（消息列表 L81） | `ArtIcon(ArtPaths.npcAvatar(npcId), …)`，fallback = 现在的色块+首字 |
| `categoryGlyph` 的两处（[InventoryScreen.CategoryBadge](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/inventory/InventoryScreen.kt#L378-L402)、`WarehouseScreen` 内联色块） | 改成按 **resourceId** 取图（`ArtPaths.item(resourceId)`），fallback = 现在的类别汉字 |

注意最后一处：现在物品徽章只拿到 `ResourceCategory`，接图要**把 resourceId 传进来**（背包/仓库列表本来就有 `definition`，改动只在徽章函数的参数）。

### 第 4 步：地图针接入

- `MapLibreAdapter` 构造加 `artSource: ArtSource? = null`（有默认值 → 不改也是合法状态），`AppContainer` 里注入。
  **为什么不给 `attach` 加 Context**：`attach` 的契约是"可重复 attach、幂等"（[MapLibreAdapter.kt#L75-L78](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/platform/map/MapLibreAdapter.kt#L75-L78)），塞生命周期对象进去会破坏它；构造参数只有一行改动、diff 最小。
- `installSourcesAndLayers` 里三处 `addImage`：先试 `artSource?.bitmap(path, PIN_PX)`，为 null 才落到现在的 `placePinBitmap(placeStyle(type))` / `unrevealedPinBitmap()` / `npcStyle`。
  **回退必须写在 `addImage` 之前**，不能靠现有 `runCatching` 兜——否则图层引用的名字没被注册，整层静默不渲染。
- **`addImage` 必须在创建该 style 的那个主线程回调里同步做**（MapLibre 的 style 换代会有 stale 检查，异步 addImage 会抛甚至写已释放的 native 对象）。所以解码要快：16 张 72×94 约 50–150ms，可接受；如需预热，在 `attach()` 里（setStyle 之前、正等网络拉 style）用 IO 线程把 pin 系列先解进缓存。

## 验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```
- 单测应从 **377 保持全绿**并多出 `ArtPathsTest`。
- **丢图验证（关键）**：放一张 `assets/art/place/lake.png` → 重装启动 → 地图里北湖的详情卡缩略图与"附近地点"该出图；**删掉它再跑 → 应回退成原来的色块+汉字**（这条回退是整件事的安全网，必须手验）。
- 地图针：放 `assets/art/pin/lake.png`（针尖贴底边中点）→ 北湖的地图针换成你的图；缺失 → 还是现在的汉字水滴。
- **注意迭代成本**：图标走 `assets/`，**打包在 APK 里**，所以换图要重新编译+安装（不像内容 JSON 可以 `adb push` 覆盖层 + 点「重新读取内容」）。一次性批量出图正好合适。
- 复杂实机交互由用户手动验收；AI 只做构建/安装/启动/单测。

## 风险与提醒

- **美术检查（缺图 WARN）本轮不做**，但要知道底线：`ContentValidator.validate` 被 `ContentValidatorTest` 断言了 `report.items.size == 0`（[L33/L49](file:///e:/DreamingPath/RainingTrace/app/src/test/java/com/rainingtrace/domain/content/ContentValidatorTest.kt#L33-L49)）。将来若要做缺图清单，必须用**可选参数**（默认 null = 跳过检查）从 `ContentStore` 显式传入，否则会打断既有测试。
- `PlaceStyleSpec` / `placeStyle` / `npcStyle` / `categoryGlyph` **全部保留不动**——它们既是回退，也是既有测试的锚点。全仓除 `MapVisuals` 与 `MapLibreAdapter` 外无其他引用（无序列化/反射耦合）。
- 位图用 **ARGB_8888**，别用 HARDWARE/RGB_565（MapLibre 需要 PinBuffer）。
- 缓存**不要 recycle**：native style 持引用，回收会导致 use-after-free。
- 加了 `ArtIcon` 之后，物品徽章的参数从"类别"变成"resourceId"——这是唯一需要动调用点的地方（背包/仓库各一处）。

## 关键文件

- 新增：[ArtPaths.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/domain/art)（domain/art）、`core/art/ArtSource.kt`、`core/ui/ArtIcon.kt`、`platform/art/AssetArtSource.kt`、`assets/art/{pin,place,npc,item}/`（先放 `.gitkeep`）
- 修改：[MapLibreAdapter.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/platform/map/MapLibreAdapter.kt)、[MapScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/map/MapScreen.kt)、[NpcAvatar.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/ui/NpcAvatar.kt)、[InventoryScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/inventory/InventoryScreen.kt)、[WarehouseScreen.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/warehouse/WarehouseScreen.kt)、[AppContainer.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/core/common/AppContainer.kt)、[RainingTraceApp.kt](file:///e:/DreamingPath/RainingTrace/app/src/main/java/com/rainingtrace/feature/shell/RainingTraceApp.kt)
- 新增测试：`app/src/test/java/com/rainingtrace/domain/art/ArtPathsTest.kt`