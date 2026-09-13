package com.rainingtrace.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.domain.map.distanceMetersTo
import com.rainingtrace.platform.map.MapLibreAdapter
import kotlinx.coroutines.delay
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * 地图屏：AndroidView 包装 MapView，业务只通过 [MapRendererAdapter] 交互。
 * MapView 类型不允许越过本文件进入 domain（06_地图专项 §8）。
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    mapAdapter: MapRendererAdapter,
    useFakeLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

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

        // 左上角手账浮片：探索计数常驻；Fake 定位提示仅 Fake 模式可见。
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MapChip(text = "足迹 · 已探索 ${uiState.revealedCount} 格")
            if (useFakeLocation) {
                MapChip(
                    text = "Fake 定位 · 点按地图移动",
                    emphasized = true,
                )
            }
        }

        // 附近地点卡片：出现"可观察"动作入口
        uiState.nearbyPlace?.let { place ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "距离 ${place.coordinate.distanceMetersTo(uiState.lastFix ?: place.coordinate).toInt()} m",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(onClick = viewModel::onObserveClicked) {
                        Text("观察")
                    }
                }
            }
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
private fun MapChip(
    text: String,
    emphasized: Boolean = false,
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
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
