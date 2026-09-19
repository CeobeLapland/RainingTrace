package com.rainingtrace.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.R
import com.rainingtrace.core.ui.LocalImage
import com.rainingtrace.core.ui.label
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.map.placeStyle
import com.rainingtrace.domain.exploration.PlaceActionRejectReason
import com.rainingtrace.domain.exploration.PlaceYieldPreview
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.settings.MapFilterSettings
import com.rainingtrace.domain.settings.MemoryTimeFilter
import com.rainingtrace.platform.map.MapLibreAdapter
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 地图屏：AndroidView 包装 MapView，业务只通过 [MapRendererAdapter] 交互。
 * MapView 类型不允许越过本文件进入 domain（06_地图专项 §8）。
 *
 * GPS 模式首次进入自动请求定位权限；拒绝后显示可点的授权提示；Fake 模式不请求权限。
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    worldStatus: WorldStatusViewModel,
    mapAdapter: MapRendererAdapter,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val world by worldStatus.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    // GPS 模式 + 还没处理过权限：已授权直接同步，未授权弹一次系统请求
    LaunchedEffect(uiState.locationMode, uiState.locationPermission) {
        if (uiState.locationMode == com.rainingtrace.domain.settings.LocationMode.GPS &&
            uiState.locationPermission == LocationPermission.UNKNOWN
        ) {
            if (hasLocationPermission()) {
                viewModel.onPermissionResult(true)
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                MapView(context).also { mapView ->
                    mapView.onCreate(null)
                    mapViewRef.value = mapView
                    mapView.getMapAsync { mapLibreMap: MapLibreMap ->
                        val adapter = mapAdapter as? MapLibreAdapter ?: return@getMapAsync
                        adapter.attach(mapLibreMap)
                        adapter.onMapTap { coord ->
                            viewModel.onMapTapped(coord)
                        }
                        adapter.onPlaceTap { placeId ->
                            viewModel.onPlaceTapped(placeId)
                        }
                        adapter.onViewportChanged { viewport ->
                            viewModel.onViewportChanged(viewport)
                        }
                        adapter.setCamera(viewModel.initialCamera())
                    }
                }
            },
            update = { /* MapView 自管理渲染；状态经 adapter 推送 */ },
            onRelease = { mapView ->
                (mapAdapter as? MapLibreAdapter)?.detach()
                mapView.onDestroy()
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 手动驱动 MapView 生命周期（MapLibre Native 无 Lifecycle 对象）。
        // factory 执行时 lifecycle 可能已 RESUMED，observer 收不到历史事件，
        // 必须立即按当前状态同步一次。
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                val mapView = mapViewRef.value ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            mapViewRef.value?.let { mapView ->
                val state = lifecycleOwner.lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // 从其他 Tab 返回：MapView 已重建，补一次状态重渲染。
        DisposableEffect(lifecycleOwner, viewModel) {
            val refreshObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
            lifecycleOwner.lifecycle.addObserver(refreshObserver)
            onDispose { lifecycleOwner.lifecycle.removeObserver(refreshObserver) }
        }

        // 左上角手账浮片：探索计数常驻；定位模式/权限提示。
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapChip(text = "足迹 · 已探索 ${uiState.revealedCount} 格")
            MapChip(text = "${world.weatherLabel} · ${world.timeOfDayLabel} · ${world.timeLabel}")
            if (uiState.trackingEnabled &&
                uiState.locationMode == com.rainingtrace.domain.settings.LocationMode.GPS
            ) {
                MapChip(text = "足迹记录中", emphasized = true)
            }
            when (uiState.locationMode) {
                com.rainingtrace.domain.settings.LocationMode.FAKE ->
                    MapChip(text = "Fake 定位 · 点按地图移动", emphasized = true)

                com.rainingtrace.domain.settings.LocationMode.GPS -> when (uiState.locationPermission) {
                    LocationPermission.DENIED ->
                        MapChip(
                            text = "未授权定位 · 点此重试",
                            emphasized = true,
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                        )
                    LocationPermission.GRANTED ->
                        MapChip(text = "GPS 定位中")
                    LocationPermission.UNKNOWN ->
                        MapChip(text = "正在请求定位…")
                }
            }
        }

        // 右侧图层开关：迷雾、今日轨迹
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LayerToggleButton(
                iconRes = com.rainingtrace.R.drawable.ic_fog,
                label = "迷雾",
                active = uiState.showFog,
                onClick = { viewModel.setShowFog(!uiState.showFog) },
            )
            LayerToggleButton(
                iconRes = com.rainingtrace.R.drawable.ic_track,
                label = "轨迹",
                active = uiState.showTrack,
                onClick = { viewModel.setShowTrack(!uiState.showTrack) },
            )
            LayerToggleButton(
                iconRes = com.rainingtrace.R.drawable.ic_settings,
                label = "筛选",
                active = uiState.showFilterPanel,
                onClick = viewModel::toggleFilterPanel,
            )
        }

        // 图层筛选面板：地点类型 + 记忆 + 时间
        if (uiState.showFilterPanel) {
            MapFilterPanel(
                filters = filters,
                onToggleType = viewModel::togglePlaceType,
                onToggleMemories = viewModel::toggleMemories,
                onSetTime = viewModel::setTimeFilter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
            )
        }

        // 底部信息优先级：聚焦记忆 → 聚焦某天轨迹 → 选中地点 → 附近地点列表。
        val focusedMemory = uiState.focusedMemory
        val focusedDay = uiState.focusedTrackDay
        val selected = uiState.selectedPlace
        when {
            focusedMemory != null -> MemoryFocusCard(
                memory = focusedMemory,
                onClose = viewModel::clearMemoryFocus,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )

            focusedDay != null -> TrackDayFocusCard(
                day = focusedDay,
                onClose = viewModel::clearTrackDayFocus,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )

            selected != null -> PlaceDetailCard(
                place = selected,
                distanceMeters = selected.coordinate.distanceMetersTo(
                    uiState.lastFix ?: selected.coordinate,
                ),
                previews = uiState.actionPreviews,
                onAction = viewModel::onPlaceAction,
                onClose = viewModel::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )

            uiState.nearbyPlaces.isNotEmpty() -> NearbyPlacesCard(
                places = uiState.nearbyPlaces,
                distanceOf = { p ->
                    p.coordinate.distanceMetersTo(uiState.lastFix ?: p.coordinate)
                },
                onSelect = viewModel::selectPlace,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        // Toast 反馈（3 秒自动消失）
        uiState.toast?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            LaunchedEffect(message) {
                delay(3000)
                viewModel.consumeToast()
            }
        }
    }
}

@Composable
private fun LayerToggleButton(
    iconRes: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (active) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun MapChip(
    text: String,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val container = if (emphasized) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val content = if (emphasized) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(percent = 50),
        tonalElevation = 0.dp,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 日记「在地图查看」聚焦卡：说明当前高亮的是哪条记忆。 */
@Composable
private fun MemoryFocusCard(
    memory: MemoryNode,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("记忆", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append(
                                MEMORY_TIME_FORMAT.format(
                                    Instant.ofEpochMilli(memory.createdAtEpochMs).atZone(MAP_ZONE),
                                ),
                            )
                            memory.mood?.let { append(" · ${it.label()}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (memory.text.isNotBlank()) {
                Text(
                    text = memory.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (memory.mediaRefs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    memory.mediaRefs.forEach { uri ->
                        LocalImage(
                            localUri = uri,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
            if (memory.audioRef != null) {
                Text(
                    text = "含一段语音，可在日记里回放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 轨迹日历「在地图查看」聚焦卡：说明当前画的是哪天的轨迹。 */
@Composable
private fun TrackDayFocusCard(
    day: FocusedTrackDay,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = TRACK_DAY_TITLE.format(day.date),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${distanceLabel(day.lengthMeters)} · " +
                            "${day.startLabel}–${day.endLabel} · ${day.pointCount} 个点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = "这是那一天走过的轨迹。关掉就回到今天。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun distanceLabel(meters: Double): String = if (meters < 1000) {
    "${meters.toInt()} m"
} else {
    String.format(java.util.Locale.SIMPLIFIED_CHINESE, "%.2f km", meters / 1000)
}

/**
 * 地点详情卡：缩略图占位 + 名称/类型/距离 + 说明 + 每个可执行动作（带"此刻产出"提示）。
 *
 * 提示用的是与结算同一套规则判定，所以"显示有产出"就等于"点下去能拿到"；
 * 世界状态（天气/时段/季节）一变，这里就跟着变。
 */
@Composable
private fun PlaceDetailCard(
    place: Place,
    distanceMeters: Double,
    previews: Map<PlaceActionType, PlaceYieldPreview>,
    onAction: (PlaceActionType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaceThumb(type = place.type, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${placeTypeLabel(place.type)} · 距离 ${distanceMeters.toInt()} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (place.description.isNotBlank()) {
                Text(
                    text = place.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            // 动作竖排：每个动作一行（按钮 + 此刻产出），动作变多也不会挤成一坨。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                place.actions.sortedBy { it.ordinal }.forEach { action ->
                    val preview = previews[action]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onAction(action) }) {
                            Text(actionLabel(action))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = previewLabel(preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (preview is PlaceYieldPreview.Ready) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 产出提示文案；还没算出来时留省略号，避免闪烁成"没有"。 */
private fun previewLabel(preview: PlaceYieldPreview?): String = when (preview) {
    null -> "…"
    is PlaceYieldPreview.Ready -> "此刻：${preview.resourceName} ×${preview.amount}"
    is PlaceYieldPreview.Unavailable -> when (preview.reason) {
        PlaceActionRejectReason.NOTHING_HERE -> "此刻没有"
        PlaceActionRejectReason.ON_COOLDOWN -> "刚来过，过会儿再来"
        PlaceActionRejectReason.ACTION_NOT_AVAILABLE -> "这里不能这么做"
        PlaceActionRejectReason.TOO_FAR -> "太远了"
        PlaceActionRejectReason.REWARD_FAILED -> "此刻拿不到"
    }
}

/** 附近地点列表：进入多个地点范围时逐个列出，点击任一项查看详情。 */
@Composable
private fun NearbyPlacesCard(
    places: List<Place>,
    distanceOf: (Place) -> Double,
    onSelect: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "附近地点",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            places.forEach { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = { onSelect(place) })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaceThumb(type = place.type, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${placeTypeLabel(place.type)} · ${distanceOf(place).toInt()} m",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 地点缩略图占位：用类型色圆角 + 标记字，后续可替换为照片。 */
@Composable
private fun PlaceThumb(type: PlaceType, size: androidx.compose.ui.unit.Dp) {
    val spec = placeStyle(type)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(color = Color(spec.argbColor), shape = RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = spec.glyph,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

private fun placeTypeLabel(type: PlaceType): String = when (type) {
    PlaceType.LAKE -> "湖泊"
    PlaceType.LIBRARY -> "图书馆"
    PlaceType.CANTEEN -> "食堂"
    PlaceType.DORM -> "宿舍"
    PlaceType.GARDEN -> "花园"
    PlaceType.PLAZA -> "广场"
    PlaceType.OTHER -> "地点"
}

private fun actionLabel(action: PlaceActionType): String = when (action) {
    PlaceActionType.OBSERVE -> "观察"
    PlaceActionType.COLLECT -> "采集"
}

/** 图层筛选面板：地点类型开关 + 记忆 + 时间。逻辑隐藏语义在 VM 侧保证。 */
@Composable
private fun MapFilterPanel(
    filters: MapFilterSettings,
    onToggleType: (PlaceType) -> Unit,
    onToggleMemories: () -> Unit,
    onSetTime: (MemoryTimeFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("只看这些地点", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlaceType.entries.forEach { type ->
                    PlaceTypeChip(
                        type = type,
                        selected = type in filters.shownPlaceTypes,
                        onClick = { onToggleType(type) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleMemories)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "显示记忆",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (filters.showMemories) "开" else "关",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (filters.showMemories) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text("记忆时间", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MemoryTimeFilter.entries.forEach { tf ->
                    TimeSegmentChip(
                        label = timeFilterLabel(tf),
                        selected = filters.memoryTimeFilter == tf,
                        onClick = { onSetTime(tf) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceTypeChip(
    type: PlaceType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spec = placeStyle(type)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                color = if (selected) Color(spec.argbColor) else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = spec.glyph,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(percent = 50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun timeFilterLabel(filter: MemoryTimeFilter): String = when (filter) {
    MemoryTimeFilter.ALL -> "全部"
    MemoryTimeFilter.TODAY -> "今天"
    MemoryTimeFilter.THIS_WEEK -> "近一周"
}

private val MAP_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val MEMORY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val TRACK_DAY_TITLE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.SIMPLIFIED_CHINESE)
