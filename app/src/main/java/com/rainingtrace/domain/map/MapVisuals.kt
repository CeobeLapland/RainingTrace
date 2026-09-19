package com.rainingtrace.domain.map

import com.rainingtrace.domain.exploration.CellFogState
import kotlin.math.cos

/**
 * 地图渲染的领域侧视图模型（06_地图专项 §8）。
 *
 * Domain 定义"要画什么"，platform 适配器决定"怎么画"；
 * MapView 等 SDK 类型不允许越过 platform 层。
 */
data class MapCamera(
    val center: WorldCoordinate,
    val zoom: Double,
)

data class HexCellVisual(
    val cellId: HexCellId,
    /** 闭合前的六个顶点（逆时针）。 */
    val polygon: List<WorldCoordinate>,
    val fogState: CellFogState,
)

data class PlayerMarkerVisual(
    val coordinate: WorldCoordinate,
)

data class PlaceVisual(
    val placeId: String,
    val name: String,
    val coordinate: WorldCoordinate,
    val placeType: PlaceType,
    /** false = 未探索，渲染为 "?" 且不显示名字。 */
    val revealed: Boolean = true,
)

/** 记忆节点的地图标记：坐标 + 心情（决定颜色）+ 时间小字（HH:mm）。 */
data class MemoryVisual(
    val coordinate: WorldCoordinate,
    val mood: com.rainingtrace.domain.memory.Mood?,
    val timeLabel: String,
)

/**
 * 地点呈现规格：颜色 + 标记字。
 * 纯数据（ARGB Int），platform 画位图、feature 画缩略图共用，避免两处配色漂移。
 */
data class PlaceStyleSpec(val argbColor: Int, val glyph: String)

fun placeStyle(type: PlaceType): PlaceStyleSpec = when (type) {
    PlaceType.LAKE -> PlaceStyleSpec(0xFF2E6FA3.toInt(), "湖")
    PlaceType.LIBRARY -> PlaceStyleSpec(0xFF7D5BA6.toInt(), "馆")
    PlaceType.CANTEEN -> PlaceStyleSpec(0xFFC9903B.toInt(), "食")
    PlaceType.DORM -> PlaceStyleSpec(0xFFD06B3A.toInt(), "宿")
    PlaceType.GARDEN -> PlaceStyleSpec(0xFF3E8E70.toInt(), "苑")
    PlaceType.PLAZA -> PlaceStyleSpec(0xFF5B8FB9.toInt(), "场")
    PlaceType.OTHER -> PlaceStyleSpec(0xFF8A93A6.toInt(), "点")
    // 自然资源点：偏暖的自然色，与人文地点的蓝紫系拉开。
    PlaceType.ORCHARD -> PlaceStyleSpec(0xFF6E9A2E.toInt(), "果")
    PlaceType.BERRY_BUSH -> PlaceStyleSpec(0xFF9C3B62.toInt(), "莓")
    PlaceType.MUSHROOM_PATCH -> PlaceStyleSpec(0xFF8A6A4A.toInt(), "菌")
}

/**
 * 地图上要画哪些地点标记（06_地图专项 §7 POI 分层 + 筛选语义）。
 *
 * - 已揭示 + 类型没被筛掉 → 彩色图标 + 名字；
 * - 未揭示的**人文地点** → 灰色 "?"（走过了才知道这儿有东西），这是既有的探索暗示；
 * - 未揭示的**自然资源点** → 完全不画：给个 "?" 等于免费开图，资源点必须自己走近撞见。
 */
fun placeVisualsFor(
    places: List<Place>,
    isRevealed: (Place) -> Boolean,
    shownTypes: Set<PlaceType>,
): List<PlaceVisual> = buildList {
    places.forEach { place ->
        val revealed = isRevealed(place)
        when {
            revealed && place.type in shownTypes ->
                add(PlaceVisual(place.id, place.name, place.coordinate, place.type, revealed = true))

            !revealed && place.type.category == PlaceCategory.PLACE ->
                add(PlaceVisual(place.id, "", place.coordinate, place.type, revealed = false))

            else -> Unit
        }
    }
}

/**
 * 相机视口的经纬度包围盒（SW 角 + NE 角）与缩放级别。
 * 迷雾按视口渲染：只铺满当前屏幕外扩区域的格子，数据量与世界大小无关。
 */
data class MapViewport(
    val southWest: WorldCoordinate,
    val northEast: WorldCoordinate,
    val zoom: Double,
) {
    fun contains(c: WorldCoordinate): Boolean =
        c.latDegrees in southWest.latDegrees..northEast.latDegrees &&
            c.lngDegrees in southWest.lngDegrees..northEast.lngDegrees

    /** 顶点中任一点落在包围盒内，即认为多边形与视口相交（格远小于视口，够用）。 */
    fun intersectsPolygon(vertices: List<WorldCoordinate>): Boolean =
        vertices.any(::contains)

    /**
     * 按比例外扩包围盒（雾遮罩要比屏幕大一圈，拖动时不露边）。
     * 经度跨度按中纬度余弦修正，保持各方向近似等米宽。
     */
    fun expanded(factor: Double): MapViewport {
        val midLat = (southWest.latDegrees + northEast.latDegrees) / 2.0
        val halfLat = (northEast.latDegrees - southWest.latDegrees) / 2.0 * factor
        val halfLng = (northEast.lngDegrees - southWest.lngDegrees) / 2.0 *
            factor / cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
        val midLng = (southWest.lngDegrees + northEast.lngDegrees) / 2.0
        return MapViewport(
            southWest = WorldCoordinate(midLat - halfLat, midLng - halfLng),
            northEast = WorldCoordinate(midLat + halfLat, midLng + halfLng),
            zoom = zoom,
        )
    }

    /** 外扩矩形外环（顺时针四个角，首尾不重复）。 */
    fun rectangleRing(): List<WorldCoordinate> = listOf(
        WorldCoordinate(southWest.latDegrees, southWest.lngDegrees),
        WorldCoordinate(southWest.latDegrees, northEast.lngDegrees),
        WorldCoordinate(northEast.latDegrees, northEast.lngDegrees),
        WorldCoordinate(northEast.latDegrees, southWest.lngDegrees),
    )
}

enum class MapLayer {
    CELLS,
    PLAYER,
    PLACES,
    TRACK,
    FOG_MASK,
    MEMORY,
}

interface MapRendererAdapter {
    fun setCamera(camera: MapCamera)
    fun renderCells(cells: List<HexCellVisual>)
    fun renderPlayer(marker: PlayerMarkerVisual?)
    fun renderPlaces(places: List<PlaceVisual>)

    /** 记忆标记点；空列表时清空。 */
    fun renderMemories(memories: List<MemoryVisual>)

    /**
     * 「在地图查看」的聚焦高亮环（与筛选无关，始终可见）；null 表示清除。
     * 日记跳转地图时用它标出目标，避免落在密集标记里找不到。
     */
    fun renderFocus(coordinate: WorldCoordinate?)

    /** 今日/区间轨迹折线；少于 2 个点时清空。 */
    fun renderTrack(points: List<WorldCoordinate>)

    /** 相机停止移动时回调最新视口（驱动按视口渲染）。 */
    fun onViewportChanged(listener: ((MapViewport) -> Unit)?)

    /** 图层显隐开关（地图浮层按钮）。 */
    fun setLayerVisible(layer: MapLayer, visible: Boolean)

    fun clearLayer(layer: MapLayer)
}
