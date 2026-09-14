package com.rainingtrace.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.domain.map.placeStyle
import com.rainingtrace.platform.map.MapLibreAdapter
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

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
            MapChip(text = "${world.weatherLabel} · ${world.timeLabel}")
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
        }

        // 底部地点信息：
        // 有选中地点 → 详情卡；否则观察范围内有地点 → 附近列表（多个可逐个选）。
        val selected = uiState.selectedPlace
        if (selected != null) {
            PlaceDetailCard(
                place = selected,
                distanceMeters = selected.coordinate.distanceMetersTo(
                    uiState.lastFix ?: selected.coordinate,
                ),
                onObserve = viewModel::onObserveClicked,
                onClose = viewModel::clearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        } else if (uiState.nearbyPlaces.isNotEmpty()) {
            NearbyPlacesCard(
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

/** 地点详情卡：缩略图占位 + 名称/类型/距离 + 说明 + 全部可执行动作。 */
@Composable
private fun PlaceDetailCard(
    place: Place,
    distanceMeters: Double,
    onObserve: () -> Unit,
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
            // 地点可做的动作不止一个：为每个动作渲染一个按钮。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                place.actions.sortedBy { it.name }.forEach { action ->
                    Button(onClick = onObserve) {
                        Text(actionLabel(action))
                    }
                }
            }
        }
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
}
