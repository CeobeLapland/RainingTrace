package com.rainingtrace.domain.art

import com.rainingtrace.domain.map.PlaceType

/**
 * 美术图的路径约定（**约定即接口**，GDD §21 的内容外置思路的延伸）。
 *
 * 美术只需按这里的名字把 PNG 丢进 `assets/art/`，代码一行都不用改；
 * 缺图时各处会回退到原有占位（`placeStyle` 的色块+汉字、`npcStyle`、类别汉字），
 * 所以"画不完"永远不等于"游戏坏了"。
 *
 * 纯函数、零 Android 依赖：约定本身可以被单测钉住（见 `ArtPathsTest`）。
 * 文件名里的 `<id>` 一律**原样**使用（含点，如 `npc.lin.png` / `res.rope.png`）。
 */
object ArtPaths {

    const val PIN_DIR = "art/pin"
    const val PLACE_DIR = "art/place"
    const val NPC_DIR = "art/npc"
    const val ITEM_DIR = "art/item"

    /** 地图上的地点水滴针；**针尖要贴在画布底边中点**（渲染侧 iconAnchor = BOTTOM）。 */
    fun pin(type: PlaceType): String = "$PIN_DIR/${type.name.lowercase()}.png"

    /** 未探索地点的灰色针。 */
    fun unrevealedPin(): String = "$PIN_DIR/_unrevealed.png"

    /** 地图上的 NPC 针：停在某处 / 正在走动，两种姿态各一张。 */
    fun npcPin(walking: Boolean): String = "$PIN_DIR/npc_${if (walking) "walk" else "stay"}.png"

    /** 地点缩略图：详情卡、附近地点列表、图层筛选面板的圆点共用。 */
    fun place(type: PlaceType): String = "$PLACE_DIR/${type.name.lowercase()}.png"

    /**
     * NPC 头像（聊天、卡片、档案页）：`art/npc/npc.head.<id>.png`。
     * `npc.head.` 前缀是为了和立绘共用同一目录时互不混淆。
     */
    fun npcAvatar(npcId: String): String = "$NPC_DIR/npc.head.$npcId.png"

    /**
     * NPC 立绘（档案全屏页 / 地图卡片）：`art/npc/npc.body.<id>.png`。
     * 缺图时调用方应隐藏立绘区，回退到头像与文字。
     */
    fun npcPortrait(npcId: String): String = "$NPC_DIR/npc.body.$npcId.png"

    /** 物品图标（背包 / 仓库 / 图鉴）。 */
    fun item(resourceId: String): String = "$ITEM_DIR/$resourceId.png"
}