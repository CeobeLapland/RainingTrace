package com.rainingtrace.platform.map

import android.graphics.Color
import android.util.Log
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapLayer
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.PlaceVisual
import com.rainingtrace.domain.map.PlayerMarkerVisual
import com.rainingtrace.domain.map.WorldCoordinate
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
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

    private var tapListener: ((WorldCoordinate) -> Unit)? = null

    /** attach 代际：detach 后旧的异步 style 回调一律作废，避免写已销毁的 native 对象。 */
    private var attachGeneration = 0

    /** 由 feature 层在 MapView 就绪后调用。可重复 attach（页面切换后重建地图）。 */
    fun attach(mapLibreMap: MapLibreMap) {
        // 同一张地图且 style 已就绪：不重复 setStyle（重复加载会使旧 style 失效）
        if (map === mapLibreMap && style != null) return
        val generation = ++attachGeneration
        this.map = mapLibreMap
        this.style = null
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
                Log.d(TAG, "map tap ${latLng.latitude},${latLng.longitude}")
                tapListener?.invoke(
                    WorldCoordinate(latLng.latitude, latLng.longitude),
                )
                true
            }
            flushPending()
        }
    }

    /** MapView 销毁时调用：丢弃缓存的 style/map，停止一切渲染写入。 */
    fun detach() {
        attachGeneration++
        style = null
        map = null
    }

    fun onMapTap(listener: (WorldCoordinate) -> Unit) {
        tapListener = listener
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
                        addStringProperty(PROP_PLACE_NAME, it.name)
                    }
                },
            ),
        )
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
        }
    }

    private fun flushPending() {
        pendingCamera?.let { setCamera(it); pendingCamera = null }
        pendingCells?.let { renderCells(it); pendingCells = null }
        pendingPlayer?.let { renderPlayer(it); pendingPlayer = null }
        pendingPlaces?.let { renderPlaces(it); pendingPlaces = null }
    }

    private fun installSourcesAndLayers(loaded: Style) {
        loaded.addSource(GeoJsonSource(CELLS_SOURCE, EMPTY_FC))
        loaded.addSource(GeoJsonSource(PLAYER_SOURCE, EMPTY))
        loaded.addSource(GeoJsonSource(PLACES_SOURCE, EMPTY_FC))

        // 每个迷雾状态一个 fill 层（filter 驱动），避免表达式版本差异风险。
        FOG_COLORS.forEach { (state, color) ->
            loaded.addLayer(
                FillLayer("fill_${state.name}", CELLS_SOURCE).apply {
                    setProperties(
                        PropertyFactory.fillColor(color),
                        PropertyFactory.fillOpacity(FILL_OPACITY),
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
        loaded.addLayer(
            CircleLayer(PLACES_LAYER, PLACES_SOURCE).apply {
                setProperties(
                    PropertyFactory.circleColor(PLACE_COLOR),
                    PropertyFactory.circleRadius(PLACE_RADIUS),
                    PropertyFactory.circleStrokeColor(Color.WHITE),
                    PropertyFactory.circleStrokeWidth(PLACE_STROKE),
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

    companion object {
        private const val TAG = "MapLibreAdapter"

        // 第一阶段托管矢量瓦片（01_技术栈：允许原型使用），无需 API key。
        // OpenFreeMap liberty：全球街道级矢量瓦片，校园缩放可用。
        const val STYLE_URI = "https://tiles.openfreemap.org/styles/liberty"

        private const val CELLS_SOURCE = "rt-cells"
        private const val PLAYER_SOURCE = "rt-player"
        private const val PLACES_SOURCE = "rt-places"
        private const val PLAYER_LAYER = "rt-player-dot"
        private const val PLACES_LAYER = "rt-places-dot"
        private const val PROP_FOG = "fog"
        private const val PROP_PLACE_NAME = "placeName"
        private const val FILL_OPACITY = 0.45f
        private const val OUTLINE_WIDTH = 0.8f
        private const val PLAYER_RADIUS = 7f
        private const val PLAYER_STROKE = 2f
        private const val PLACE_RADIUS = 6f
        private const val PLACE_STROKE = 2f
        private const val CAMERA_ANIM_MS = 600

        private val OUTLINE_COLOR = Color.parseColor("#33000000")
        private val PLAYER_COLOR = Color.parseColor("#D06B3A")
        private val PLACE_COLOR = Color.parseColor("#2F6FB2")

        private val FOG_COLORS: Map<CellFogState, Int> = mapOf(
            CellFogState.UNKNOWN to Color.parseColor("#5A6B7A"),
            CellFogState.DISCOVERED to Color.parseColor("#8FB8A8"),
            CellFogState.VISITED to Color.parseColor("#4E9B7A"),
            CellFogState.MEMORIZED to Color.parseColor("#E0B84E"),
            CellFogState.SPECIAL to Color.parseColor("#9B59B6"),
        )

        private val EMPTY: Feature = Feature.fromGeometry(Point.fromLngLat(0.0, 0.0))
        private val EMPTY_FC: FeatureCollection = FeatureCollection.fromFeatures(emptyList())
    }
}
