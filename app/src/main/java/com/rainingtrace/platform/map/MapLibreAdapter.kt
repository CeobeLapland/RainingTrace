package com.rainingtrace.platform.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.util.Log
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapLayer
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.MemoryVisual
import com.rainingtrace.domain.map.PlaceStyleSpec
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import com.rainingtrace.domain.map.placeStyle
import com.rainingtrace.domain.memory.Mood
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * RT-MAP-001/002/003: MapLibre 渲染适配器。
 *
 * 职责边界（06_地图专项 §1）：只做 render/camera/gesture/visual layer。
 * 六边形数据由 domain 的 [HexCellVisual] 提供（GeoJSON source），
 * 迷雾颜色按 CellFogState 分层 filter 渲染。
 */
class MapLibreAdapter : MapRendererAdapter {

    private var map: MapLibreMap? = null
    private var style: Style? = null

    private var pendingCamera: MapCamera? = null
    private var pendingCells: List<HexCellVisual>? = null
    private var pendingPlayer: PlayerMarkerVisual? = null
    private var pendingPlaces: List<PlaceVisual>? = null
    private var pendingMemories: List<MemoryVisual>? = null
    private var pendingFocus: WorldCoordinate? = null
    private var pendingTrack: List<WorldCoordinate>? = null

    /** 图层期望可见性；style 就绪/换代后据此重放。 */
    private val layerVisibility = mutableMapOf<MapLayer, Boolean>()

    private var tapListener: ((WorldCoordinate) -> Unit)? = null
    private var placeTapListener: ((String) -> Unit)? = null
    private var viewportListener: ((com.rainingtrace.domain.map.MapViewport) -> Unit)? = null
    private var cameraIdleListener: MapLibreMap.OnCameraIdleListener? = null

    /** attach 代际：detach 后旧的异步 style 回调一律作废，避免写已销毁的 native 对象。 */
    private var attachGeneration = 0

    /** 由 feature 层在 MapView 就绪后调用。可重复 attach（页面切换后重建地图）。 */
    fun attach(mapLibreMap: MapLibreMap) {
        // 同一张地图且 style 已就绪：不重复 setStyle（重复加载会使旧 style 失效）
        if (map === mapLibreMap && style != null) return
        val generation = ++attachGeneration
        // 旧地图的监听先摘掉
        cameraIdleListener?.let { map?.removeOnCameraIdleListener(it) }
        this.map = mapLibreMap
        this.style = null
        val listener = MapLibreMap.OnCameraIdleListener { emitViewport(mapLibreMap) }
        cameraIdleListener = listener
        mapLibreMap.addOnCameraIdleListener(listener)
        // 最小缩放：防止缩太远导致视口格数爆炸；世界边界限制相机中心（城市尺度）。
        mapLibreMap.setMinZoomPreference(MIN_ZOOM)
        mapLibreMap.setLatLngBoundsForCameraTarget(WORLD_BOUNDS)
        mapLibreMap.setStyle(Style.Builder().fromUri(STYLE_URI)) { loadedStyle ->
            if (generation != attachGeneration) {
                Log.d(TAG, "style callback from stale generation, ignored")
                return@setStyle
            }
            if (loadedStyle == null) {
                Log.e(TAG, "style load FAILED uri=$STYLE_URI")
                return@setStyle
            }
            Log.i(TAG, "style loaded ok uri=$STYLE_URI")
            this.style = loadedStyle
            installSourcesAndLayers(loadedStyle)
            mapLibreMap.addOnMapClickListener { latLng ->
                handleTap(mapLibreMap, latLng)
                true
            }
            flushPending()
            // style 就绪时补发一次视口（初始相机的 idle 可能已错过）
            emitViewport(mapLibreMap)
        }
    }

    /** MapView 销毁时调用：丢弃缓存的 style/map，停止一切渲染写入。 */
    fun detach() {
        attachGeneration++
        cameraIdleListener?.let { map?.removeOnCameraIdleListener(it) }
        cameraIdleListener = null
        viewportListener = null
        style = null
        map = null
    }

    fun onMapTap(listener: (WorldCoordinate) -> Unit) {
        tapListener = listener
    }

    /** 点击地点图标记回调地点 id；优先于 [onMapTap] 的背景移动。 */
    fun onPlaceTap(listener: (String) -> Unit) {
        placeTapListener = listener
    }

    /**
     * 先命中地点标记（图标/地名层）→ 报地点 id；否则当作裸地图点击 → 报坐标。
     */
    private fun handleTap(mapLibreMap: MapLibreMap, latLng: LatLng) {
        val screen = mapLibreMap.projection.toScreenLocation(latLng)
        val features = runCatching {
            mapLibreMap.queryRenderedFeatures(screen, *placeLayerIds().toTypedArray())
        }.getOrNull()
        val placeId = features
            ?.mapNotNull { f -> runCatching { f.getStringProperty(PROP_PLACE_ID) }.getOrNull() }
            ?.firstOrNull { it.isNotBlank() }
        if (!placeId.isNullOrBlank()) {
            Log.d(TAG, "place tap $placeId")
            placeTapListener?.invoke(placeId)
            return
        }
        Log.d(TAG, "map tap ${latLng.latitude},${latLng.longitude}")
        tapListener?.invoke(WorldCoordinate(latLng.latitude, latLng.longitude))
    }

    override fun onViewportChanged(listener: ((com.rainingtrace.domain.map.MapViewport) -> Unit)?) {
        viewportListener = listener
    }

    private fun emitViewport(mapLibreMap: MapLibreMap) {
        // style 刚加载、相机尚未出首帧时 visibleRegion 可能是垃圾值（如纬度 -101），
        // 在 native style 回调里抛异常会 abort 进程，必须先校验。
        val bounds = runCatching { mapLibreMap.projection.visibleRegion.latLngBounds }
            .getOrNull() ?: return
        if (bounds.latitudeSouth !in -85.0..85.0 || bounds.latitudeNorth !in -85.0..85.0) return
        if (bounds.longitudeWest !in -180.0..180.0 || bounds.longitudeEast !in -180.0..180.0) return
        if (bounds.latitudeNorth <= bounds.latitudeSouth ||
            bounds.longitudeEast <= bounds.longitudeWest
        ) {
            return
        }
        viewportListener?.invoke(
            com.rainingtrace.domain.map.MapViewport(
                southWest = WorldCoordinate(bounds.latitudeSouth, bounds.longitudeWest),
                northEast = WorldCoordinate(bounds.latitudeNorth, bounds.longitudeEast),
                zoom = mapLibreMap.cameraPosition.zoom,
            ),
        )
    }

    override fun setCamera(camera: MapCamera) {
        val mapLibreMap = map
        if (mapLibreMap == null || style == null) {
            pendingCamera = camera
            return
        }
        mapLibreMap.easeCamera(cameraUpdate(camera), CAMERA_ANIM_MS)
    }

    override fun renderCells(cells: List<HexCellVisual>) {
        val loaded = style
        if (loaded == null) {
            pendingCells = cells
            return
        }
        val source = loaded.safeSource(CELLS_SOURCE)
            ?: run { pendingCells = cells; return }
        source.setGeoJson(toFeatureCollection(cells))
    }

    override fun renderPlayer(marker: PlayerMarkerVisual?) {
        val loaded = style
        if (loaded == null) {
            pendingPlayer = marker
            return
        }
        val source = loaded.safeSource(PLAYER_SOURCE)
            ?: run { pendingPlayer = marker; return }
        source.setGeoJson(
            if (marker == null) EMPTY else Feature.fromGeometry(toPoint(marker.coordinate)),
        )
    }

    override fun renderPlaces(places: List<PlaceVisual>) {
        val loaded = style
        if (loaded == null) {
            pendingPlaces = places
            return
        }
        val source = loaded.safeSource(PLACES_SOURCE)
            ?: run { pendingPlaces = places; return }
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                places.map {
                    Feature.fromGeometry(toPoint(it.coordinate)).apply {
                        addStringProperty(PROP_PLACE_ID, it.placeId)
                        addStringProperty(PROP_PLACE_NAME, if (it.revealed) it.name else "")
                        addStringProperty(
                            PROP_PLACE_TYPE,
                            if (it.revealed) it.placeType.name.lowercase() else UNREVEALED_KEY,
                        )
                    }
                },
            ),
        )
    }

    override fun renderMemories(memories: List<MemoryVisual>) {
        val loaded = style
        if (loaded == null) {
            pendingMemories = memories
            return
        }
        val source = loaded.safeSource(MEMORY_SOURCE)
            ?: run { pendingMemories = memories; return }
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                memories.map {
                    Feature.fromGeometry(toPoint(it.coordinate)).apply {
                        addStringProperty(PROP_MEMORY_MOOD, (it.mood?.name ?: "").lowercase())
                        addStringProperty(PROP_MEMORY_TIME, it.timeLabel)
                    }
                },
            ),
        )
    }

    override fun renderFocus(coordinate: WorldCoordinate?) {
        val loaded = style
        if (loaded == null) {
            pendingFocus = coordinate
            return
        }
        val source = loaded.safeSource(FOCUS_SOURCE)
            ?: run { pendingFocus = coordinate; return }
        source.setGeoJson(
            if (coordinate == null) {
                EMPTY_FC
            } else {
                FeatureCollection.fromFeature(Feature.fromGeometry(toPoint(coordinate)))
            },
        )
    }

    override fun renderTrack(points: List<WorldCoordinate>) {
        val loaded = style
        if (loaded == null) {
            pendingTrack = points
            return
        }
        val source = loaded.safeSource(TRACKS_SOURCE)
            ?: run { pendingTrack = points; return }
        // 去掉连续重复点：0 长度线段是退化几何。
        val distinct = mutableListOf<WorldCoordinate>()
        points.forEach { c ->
            if (distinct.lastOrNull() != c) distinct.add(c)
        }
        source.setGeoJson(
            if (distinct.size < 2) {
                EMPTY_FC
            } else {
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(LineString.fromLngLats(distinct.map { toPoint(it) })),
                )
            },
        )
    }

    override fun setLayerVisible(layer: MapLayer, visible: Boolean) {
        layerVisibility[layer] = visible
        applyLayerVisibility(layer, visible)
    }

    /**
     * MapLibre 的 Style 在异步换 style 期间调用 getSourceAs 会抛
     * IllegalStateException；此时数据已存入 pending，下一次 flush 会重放，
     * 因此安全吞掉即可，不让渲染竞态崩溃进程。
     */
    private fun Style.safeSource(id: String): GeoJsonSource? =
        try {
            getSourceAs(id)
        } catch (e: IllegalStateException) {
            Log.d(TAG, "style transitioning, skip render for $id")
            null
        }

    override fun clearLayer(layer: MapLayer) {
        when (layer) {
            MapLayer.CELLS -> renderCells(emptyList())
            MapLayer.PLAYER -> renderPlayer(null)
            MapLayer.PLACES -> renderPlaces(emptyList())
            MapLayer.MEMORY -> renderMemories(emptyList())
            MapLayer.TRACK -> renderTrack(emptyList())
            MapLayer.FOG_MASK -> Unit // FOG 是 UNKNOWN/DISCOVERED 格填充，随 renderCells 刷新
        }
    }

    private fun flushPending() {
        pendingCamera?.let { setCamera(it); pendingCamera = null }
        pendingCells?.let { renderCells(it); pendingCells = null }
        pendingPlayer?.let { renderPlayer(it); pendingPlayer = null }
        pendingPlaces?.let { renderPlaces(it); pendingPlaces = null }
        pendingMemories?.let { renderMemories(it); pendingMemories = null }
        pendingFocus?.let { renderFocus(it); pendingFocus = null }
        pendingTrack?.let { renderTrack(it); pendingTrack = null }
        // style 换代后图层是新建的，按期望可见性重放一次。
        layerVisibility.forEach { (layer, visible) -> applyLayerVisibility(layer, visible) }
    }

    private fun applyLayerVisibility(layer: MapLayer, visible: Boolean) {
        val loaded = style ?: return
        val value = if (visible) Property.VISIBLE else Property.NONE
        layerIdsOf(layer).forEach { id ->
            runCatching { loaded.getLayer(id)?.setProperties(PropertyFactory.visibility(value)) }
        }
    }

    /** 一个领域图层可能对应多个原生层。 */
    private fun layerIdsOf(layer: MapLayer): List<String> = when (layer) {
        MapLayer.CELLS -> CellFogState.entries.map { "fill_${it.name}" } + "cells_outline"
        MapLayer.PLAYER -> listOf(PLAYER_LAYER)
        MapLayer.PLACES -> placeLayerIds()
        MapLayer.MEMORY -> memoryLayerIds()
        MapLayer.TRACK -> listOf(TRACK_LAYER)
        // 迷雾开关只遮暗未知/见过格；到过格的苔绿/琥珀染色属于"已发现"，保持可见。
        MapLayer.FOG_MASK -> listOf(
            "fill_${CellFogState.UNKNOWN.name}",
            "fill_${CellFogState.DISCOVERED.name}",
        )
    }

    private fun installSourcesAndLayers(loaded: Style) {
        loaded.addSource(GeoJsonSource(CELLS_SOURCE, EMPTY_FC))
        loaded.addSource(GeoJsonSource(PLAYER_SOURCE, EMPTY))
        loaded.addSource(GeoJsonSource(PLACES_SOURCE, EMPTY_FC))
        loaded.addSource(GeoJsonSource(MEMORY_SOURCE, EMPTY_FC))
        loaded.addSource(GeoJsonSource(FOCUS_SOURCE, EMPTY_FC))
        loaded.addSource(GeoJsonSource(TRACKS_SOURCE, EMPTY_FC))

        // 注册每种地点类型的水滴位图（颜色+字）+ 未探索 "?" 位图，供图标层按类型 match 取图。
        PlaceType.entries.forEach { type ->
            val spec = placeStyle(type)
            runCatching { loaded.addImage(placeImageName(type), placePinBitmap(spec)) }
        }
        runCatching { loaded.addImage(UNREVEALED_IMAGE, unrevealedPinBitmap()) }

        // 战争迷雾 = 铺满视口的六边形格填充：
        // UNKNOWN 深夜色浓雾，DISCOVERED 薄雾（见过没到过），
        // VISITED/MEMORIZED/SPECIAL 极淡状态染色（到过）。
        CELL_FOG_STYLE.forEach { (state, style) ->
            loaded.addLayer(
                FillLayer("fill_${state.name}", CELLS_SOURCE).apply {
                    setProperties(
                        PropertyFactory.fillColor(style.color),
                        PropertyFactory.fillOpacity(style.opacity),
                        PropertyFactory.fillAntialias(true),
                    )
                    setFilter(Expression.eq(Expression.get(PROP_FOG), Expression.literal(state.name)))
                },
            )
        }
        loaded.addLayerAbove(
            LineLayer("cells_outline", CELLS_SOURCE).apply {
                setProperties(
                    PropertyFactory.lineColor(OUTLINE_COLOR),
                    PropertyFactory.lineWidth(OUTLINE_WIDTH),
                )
            },
            "fill_${CellFogState.SPECIAL.name}",
        )
        loaded.addLayerAbove(
            LineLayer(TRACK_LAYER, TRACKS_SOURCE).apply {
                setProperties(
                    PropertyFactory.lineColor(TRACK_COLOR),
                    PropertyFactory.lineWidth(TRACK_WIDTH),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineOpacity(TRACK_OPACITY),
                )
            },
            "cells_outline",
        )
        // 地点层：每种类型一个静态图标层 + 该类型的名字。
        // 不使用数据驱动的 icon-image(match)：MapLibre 会校验 match 分支标签唯一性，
        // 一旦报 "Branch labels must be unique" 整个属性设置失败、整层不渲染。
        // 静态 iconImage + eq 过滤与迷雾层同构，稳定可靠。
        val fontStack = loaded.layers.asReversed()
            .filterIsInstance<SymbolLayer>()
            .firstNotNullOfOrNull { runCatching { it.textFont.value }.getOrNull() }
        PlaceType.entries.forEach { type ->
            loaded.addLayer(
                SymbolLayer(placeLayerId(type), PLACES_SOURCE).apply {
                    setFilter(
                        Expression.eq(
                            Expression.get(PROP_PLACE_TYPE),
                            Expression.literal(type.name.lowercase()),
                        ),
                    )
                    setProperties(
                        PropertyFactory.iconImage(placeImageName(type)),
                        PropertyFactory.iconSize(iconSizeExpr()),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.textField(Expression.get(PROP_PLACE_NAME)),
                        PropertyFactory.textSize(PLACE_LABEL_SIZE),
                        PropertyFactory.textColor(PLACE_LABEL_COLOR),
                        PropertyFactory.textHaloColor(Color.WHITE),
                        PropertyFactory.textHaloWidth(PLACE_LABEL_HALO),
                        PropertyFactory.textOffset(arrayOf(0f, PLACE_LABEL_OFFSET_Y)),
                        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                        PropertyFactory.textAllowOverlap(true),
                    )
                    if (fontStack != null) {
                        setProperties(PropertyFactory.textFont(fontStack))
                    }
                },
            )
        }
        // 未探索地点：灰色 "?"（名字为空，不渲染文字）。
        loaded.addLayer(
            SymbolLayer(UNREVEALED_LAYER, PLACES_SOURCE).apply {
                setFilter(
                    Expression.eq(
                        Expression.get(PROP_PLACE_TYPE),
                        Expression.literal(UNREVEALED_KEY),
                    ),
                )
                setProperties(
                    PropertyFactory.iconImage(UNREVEALED_IMAGE),
                    PropertyFactory.iconSize(iconSizeExpr()),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                )
            },
        )
        // 记忆标记：每种心情一个静态颜色层（同样避开数据驱动 circle-color）。
        MEMORY_COLOR.forEach { (mood, color) ->
            loaded.addLayer(memoryLayer(mood, color))
        }
        // 无心情记忆：默认灰。
        loaded.addLayer(memoryLayer(null, MEMORY_DEFAULT_COLOR))
        // 记忆时间小字层。
        loaded.addLayer(memoryTimeLabelLayer(fontStack))
        // 聚焦高亮环（日记「在地图查看」）：空心环，压在最上层，不受筛选影响。
        loaded.addLayer(
            CircleLayer(FOCUS_LAYER, FOCUS_SOURCE).apply {
                setProperties(
                    PropertyFactory.circleColor(FOCUS_FILL_COLOR),
                    PropertyFactory.circleRadius(FOCUS_RADIUS),
                    PropertyFactory.circleStrokeColor(FOCUS_STROKE_COLOR),
                    PropertyFactory.circleStrokeWidth(FOCUS_STROKE_WIDTH),
                )
            },
        )
        loaded.addLayer(
            CircleLayer(PLAYER_LAYER, PLAYER_SOURCE).apply {
                setProperties(
                    PropertyFactory.circleColor(PLAYER_COLOR),
                    PropertyFactory.circleRadius(PLAYER_RADIUS),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(PLAYER_STROKE),
                )
            },
        )
    }

    private fun toFeatureCollection(cells: List<HexCellVisual>): FeatureCollection {
        val features = cells.map { cell ->
            val ring = cell.polygon.map { Point.fromLngLat(it.lngDegrees, it.latDegrees) }.toMutableList()
            cell.polygon.firstOrNull()?.let { ring.add(Point.fromLngLat(it.lngDegrees, it.latDegrees)) }
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply {
                addStringProperty(PROP_FOG, cell.fogState.name)
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun toPoint(coordinate: WorldCoordinate): Point =
        Point.fromLngLat(coordinate.lngDegrees, coordinate.latDegrees)

    private fun cameraUpdate(camera: MapCamera) =
        org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(
            org.maplibre.android.camera.CameraPosition.Builder()
                .target(LatLng(camera.center.latDegrees, camera.center.lngDegrees))
                .zoom(camera.zoom)
                .build(),
        )

    private fun placeImageName(type: PlaceType): String = "$PLACE_IMAGE_PREFIX${type.name}"

    private fun placeLayerId(type: PlaceType): String = "$PLACES_LAYER_PREFIX-${type.name.lowercase()}"

    private fun memoryLayerId(mood: Mood?): String =
        "$MEMORY_LAYER_PREFIX-${mood?.name?.lowercase() ?: NONE_MOOD_KEY}"

    private fun placeLayerIds(): List<String> = PlaceType.entries.map(::placeLayerId) + UNREVEALED_LAYER

    private fun memoryLayerIds(): List<String> =
        MEMORY_COLOR.keys.map { memoryLayerId(it) } + memoryLayerId(null) + MEMORY_TIME_LABEL_LAYER

    /** 一种心情的记忆圆点层（静态颜色 + eq 过滤）+ 时间小字。 */
    private fun memoryLayer(mood: Mood?, color: String): CircleLayer =
        CircleLayer(memoryLayerId(mood), MEMORY_SOURCE).apply {
            setFilter(
                Expression.eq(
                    Expression.get(PROP_MEMORY_MOOD),
                    Expression.literal(mood?.name?.lowercase() ?: ""),
                ),
            )
            setProperties(
                PropertyFactory.circleColor(color),
                PropertyFactory.circleRadius(MEMORY_RADIUS),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(MEMORY_STROKE),
                PropertyFactory.circleOpacity(MEMORY_OPACITY),
            )
        }

    /** 记忆时间标签层：圆点下方的极小小字（只画时间，如 "14:32"）。 */
    private fun memoryTimeLabelLayer(fontStack: Array<String>?): SymbolLayer =
        SymbolLayer(MEMORY_TIME_LABEL_LAYER, MEMORY_SOURCE).apply {
            setFilter(
                Expression.neq(Expression.get(PROP_MEMORY_TIME), Expression.literal("")),
            )
            setProperties(
                PropertyFactory.textField(Expression.get(PROP_MEMORY_TIME)),
                PropertyFactory.textSize(MEMORY_LABEL_SIZE),
                PropertyFactory.textColor(MEMORY_LABEL_COLOR),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(MEMORY_LABEL_HALO),
                PropertyFactory.textOffset(arrayOf(0f, MEMORY_LABEL_OFFSET_Y)),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textAllowOverlap(true),
            )
            if (fontStack != null) {
                setProperties(PropertyFactory.textFont(fontStack))
            }
        }

    /** 图标尺寸随缩放放大：近看更大，远看更小（车道级到街区级）。 */
    private fun iconSizeExpr(): Expression =
        Expression.step(
            Expression.zoom(),
            Expression.literal(0.9f),
            Expression.literal(16.0f),
            Expression.literal(1.25f),
            Expression.literal(19.0f),
            Expression.literal(1.7f),
        )

    /**
     * 画水滴图标位图：水滴形底 + 白描边 + 类型字。
     * iconAnchor=bottom，使水滴尖端对准真实坐标（像地图 POI）。
     */
    private fun placePinBitmap(spec: PlaceStyleSpec): Bitmap {
        val w = 72
        val h = 94
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(w / 2f, 6f)
            cubicTo(w * 0.96f, h * 0.32f, w * 0.92f, h * 0.80f, w / 2f, h * 0.97f)
            cubicTo(w * 0.08f, h * 0.80f, w * 0.04f, h * 0.32f, w / 2f, 6f)
            close()
        }
        // 白描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        // 水滴填充
        paint.style = Paint.Style.FILL
        paint.color = spec.argbColor
        canvas.drawPath(path, paint)
        // 类型字
        paint.color = Color.WHITE
        paint.typeface = Typeface.SANS_SERIF
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        val baseline = h * 0.58f
        canvas.drawText(spec.glyph, w / 2f, baseline, paint)
        return bmp
    }

    /** 未探索地点的灰色 "?" 水滴：半透明灰底，白问号。 */
    private fun unrevealedPinBitmap(): Bitmap {
        val w = 72
        val h = 94
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            moveTo(w / 2f, 6f)
            cubicTo(w * 0.96f, h * 0.32f, w * 0.92f, h * 0.80f, w / 2f, h * 0.97f)
            cubicTo(w * 0.08f, h * 0.80f, w * 0.04f, h * 0.32f, w / 2f, 6f)
            close()
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = UNREVEALED_PIN_COLOR
        canvas.drawPath(path, paint)
        paint.color = Color.WHITE
        paint.typeface = Typeface.SANS_SERIF
        paint.textSize = 40f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("?", w / 2f, h * 0.60f, paint)
        return bmp
    }

    companion object {
        private const val TAG = "MapLibreAdapter"

        // 第一阶段托管矢量瓦片（01_技术栈：允许原型使用），无需 API key。
        // OpenFreeMap liberty：全球街道级矢量瓦片，校园缩放可用。
        const val STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

        private const val CELLS_SOURCE = "rt-cells"
        private const val PLAYER_SOURCE = "rt-player"
        private const val PLACES_SOURCE = "rt-places"
        private const val MEMORY_SOURCE = "rt-memories"
        private const val TRACKS_SOURCE = "rt-tracks"
        private const val PLAYER_LAYER = "rt-player-dot"
        private const val PLACES_LAYER_PREFIX = "rt-places"
        private const val UNREVEALED_LAYER = "rt-places-unrevealed"
        private const val MEMORY_LAYER_PREFIX = "rt-memory"
        private const val MEMORY_TIME_LABEL_LAYER = "rt-memory-time"
        private const val FOCUS_SOURCE = "rt-focus"
        private const val FOCUS_LAYER = "rt-focus-ring"
        private const val TRACK_LAYER = "rt-track-line"
        private const val PLACE_IMAGE_PREFIX = "rt-pin-"
        private const val UNREVEALED_IMAGE = "rt-pin-unrevealed"
        private const val UNREVEALED_KEY = "__unrevealed__"
        private const val NONE_MOOD_KEY = "none"
        private const val UNREVEALED_PIN_COLOR = 0x55_8A93A6
        private const val PROP_FOG = "fog"
        private const val PROP_PLACE_ID = "placeId"
        private const val PROP_PLACE_NAME = "placeName"
        private const val PROP_PLACE_TYPE = "placeType"
        private const val PROP_MEMORY_MOOD = "memoryMood"
        private const val PLACE_LABEL_SIZE = 13f
        private const val PLACE_LABEL_HALO = 1.6f
        private const val PLACE_LABEL_OFFSET_Y = 4f
        private const val MEMORY_RADIUS = 5f
        private const val MEMORY_STROKE = 1.5f
        private const val MEMORY_OPACITY = 0.95f
        private const val MEMORY_LABEL_SIZE = 10f
        private const val MEMORY_LABEL_HALO = 1.4f
        private const val MEMORY_LABEL_OFFSET_Y = 3f
        private const val MEMORY_DEFAULT_COLOR = "#8A93A6"
        private const val PROP_MEMORY_TIME = "memoryTime"
        // 聚焦高亮环：空心琥珀环，不遮挡目标本身。
        private const val FOCUS_FILL_COLOR = "#1FD99A2B"
        private const val FOCUS_STROKE_COLOR = "#D99A2B"
        private const val FOCUS_RADIUS = 16f
        private const val FOCUS_STROKE_WIDTH = 2.5f
        // 记忆圆点心情→颜色（CSS hex 字符串，MapLibre 数据驱动颜色要求字符串）。
        private val MEMORY_COLOR: Map<Mood, String> = mapOf(
            Mood.CALM to "#2E6FA3",
            Mood.HAPPY to "#D99A2B",
            Mood.CURIOUS to "#3E8E70",
            Mood.LONELY to "#7D8EA7",
            Mood.EXCITED to "#D06B3A",
            Mood.MELANCHOLY to "#8A6FB5",
        )
        private const val OUTLINE_WIDTH = 0.5f
        private const val TRACK_WIDTH = 3.5f
        private const val TRACK_OPACITY = 0.85f
        private const val PLAYER_RADIUS = 7f
        private const val PLAYER_STROKE = 2f
        private const val CAMERA_ANIM_MS = 600

        /** 最小缩放：再小视口内格子数量会失控；此级别约覆盖 1km 宽。 */
        private const val MIN_ZOOM = 14.0

        /**
         * 相机中心活动边界（约 25×30km，覆盖一个区/小城）。
         * 以北湖为中心：纬 ±0.2°、经 ±0.26°（按 40°N 余弦修正，各向等米宽）。
         */
        private val WORLD_BOUNDS: org.maplibre.android.geometry.LatLngBounds =
            org.maplibre.android.geometry.LatLngBounds.Builder()
                .include(LatLng(WORLD_ORIGIN_LAT + 0.2, WORLD_ORIGIN_LNG - 0.26))
                .include(LatLng(WORLD_ORIGIN_LAT - 0.2, WORLD_ORIGIN_LNG + 0.26))
                .build()

        private const val WORLD_ORIGIN_LAT = 39.7326
        private const val WORLD_ORIGIN_LNG = 116.1712

        private val OUTLINE_COLOR = Color.parseColor("#14000000")
        private val PLAYER_COLOR = Color.parseColor("#D06B3A")
        private val PLACE_LABEL_COLOR = Color.parseColor("#2B3338")
        private val MEMORY_LABEL_COLOR = Color.parseColor("#3A444C")
        private val TRACK_COLOR = Color.parseColor("#3E8E70")
        private val FOG_DARK = Color.parseColor("#0D1620")

        private data class CellFogStyle(val color: Int, val opacity: Float)

        /** 三级迷雾：未探索深夜浓雾 / 见过薄雾 / 到过极淡染色。 */
        private val CELL_FOG_STYLE: Map<CellFogState, CellFogStyle> = mapOf(
            CellFogState.UNKNOWN to CellFogStyle(FOG_DARK, 0.82f),
            CellFogState.DISCOVERED to CellFogStyle(FOG_DARK, 0.42f),
            CellFogState.VISITED to CellFogStyle(Color.parseColor("#4E9B7A"), 0.10f),
            CellFogState.MEMORIZED to CellFogStyle(Color.parseColor("#C9903B"), 0.14f),
            CellFogState.SPECIAL to CellFogStyle(Color.parseColor("#7D5BA6"), 0.14f),
        )

        private val EMPTY: Feature = Feature.fromGeometry(Point.fromLngLat(0.0, 0.0))
        private val EMPTY_FC: FeatureCollection = FeatureCollection.fromFeatures(emptyList())
    }
}
