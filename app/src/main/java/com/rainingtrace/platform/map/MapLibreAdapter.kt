package com.rainingtrace.platform.map

import android.graphics.Color
import android.util.Log
import com.rainingtrace.domain.exploration.CellFogState
import com.rainingtrace.domain.map.HexCellVisual
import com.rainingtrace.domain.map.MapCamera
import com.rainingtrace.domain.map.MapLayer
import com.rainingtrace.domain.map.MapRendererAdapter
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
    private var attached = false

    private var pendingCamera: MapCamera? = null
    private var pendingCells: List<HexCellVisual>? = null
    private var pendingPlayer: PlayerMarkerVisual? = null

    private var tapListener: ((WorldCoordinate) -> Unit)? = null

    /** 由 feature 层在 MapView 就绪后调用。 */
    fun attach(mapLibreMap: MapLibreMap) {
        if (attached) return
        attached = true
        this.map = mapLibreMap
        mapLibreMap.setStyle(Style.Builder().fromUri(STYLE_URI)) { loadedStyle ->
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
        Log.d(TAG, "renderCells n=${cells.size} first=${cells.firstOrNull()?.fogState}")
        val source = loaded.getSourceAs<GeoJsonSource>(CELLS_SOURCE)
        if (source == null) {
            Log.e(TAG, "cells source missing")
            return
        }
        source.setGeoJson(toFeatureCollection(cells))
    }

    override fun renderPlayer(marker: PlayerMarkerVisual?) {
        val loaded = style
        if (loaded == null) {
            pendingPlayer = marker
            return
        }
        loaded.getSourceAs<GeoJsonSource>(PLAYER_SOURCE)?.setGeoJson(
            if (marker == null) EMPTY else Feature.fromGeometry(toPoint(marker.coordinate)),
        )
    }

    override fun clearLayer(layer: MapLayer) {
        when (layer) {
            MapLayer.CELLS -> renderCells(emptyList())
            MapLayer.PLAYER -> renderPlayer(null)
        }
    }

    private fun flushPending() {
        pendingCamera?.let { setCamera(it); pendingCamera = null }
        pendingCells?.let { renderCells(it); pendingCells = null }
        pendingPlayer?.let { renderPlayer(it); pendingPlayer = null }
    }

    private fun installSourcesAndLayers(loaded: Style) {
        loaded.addSource(GeoJsonSource(CELLS_SOURCE, EMPTY_FC))
        loaded.addSource(GeoJsonSource(PLAYER_SOURCE, EMPTY))

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
        private const val PLAYER_LAYER = "rt-player-dot"
        private const val PROP_FOG = "fog"
        private const val FILL_OPACITY = 0.45f
        private const val OUTLINE_WIDTH = 0.8f
        private const val PLAYER_RADIUS = 7f
        private const val PLAYER_STROKE = 2f
        private const val CAMERA_ANIM_MS = 600

        private val OUTLINE_COLOR = Color.parseColor("#33000000")
        private val PLAYER_COLOR = Color.parseColor("#D06B3A")

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
