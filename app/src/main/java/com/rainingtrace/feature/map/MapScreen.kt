package com.rainingtrace.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.domain.map.MapRendererAdapter
import com.rainingtrace.platform.map.MapLibreAdapter
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
            onRelease = { mapView -> mapView.onDestroy() },
            modifier = Modifier.fillMaxSize(),
        )

        // 手动驱动 MapView 生命周期（MapLibre Native 无 Lifecycle 对象）
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
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Text(
                text = buildString {
                    append("已探索 ")
                    append(uiState.revealedCount)
                    append(" 格")
                    uiState.playerCell?.let {
                        append("\n所在格 ")
                        append(it.toStableString())
                    }
                    append("\n点击地图 = 移动（Fake 定位）")
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
