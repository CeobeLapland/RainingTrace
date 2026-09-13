package com.rainingtrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.core.common.AppContainer
import com.rainingtrace.core.ui.theme.RainingTraceTheme
import com.rainingtrace.feature.camera.CameraScreen
import com.rainingtrace.feature.camera.CameraViewModel
import com.rainingtrace.feature.journal.JournalScreen
import com.rainingtrace.feature.journal.JournalViewModel
import com.rainingtrace.feature.map.MapScreen
import com.rainingtrace.feature.map.MapViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RainingTraceApplication).appContainer
        setContent {
            RainingTraceTheme {
                AppRoot(container = container)
            }
        }
    }
}

private enum class Route { MAP, CAMERA, JOURNAL, AR }

/**
 * MVP 轻量导航：单 Activity + 状态切换。
 * 不引入 Navigation 依赖（切片够用即可，避免过早复杂化）。
 */
@Composable
fun AppRoot(container: AppContainer) {
    var route by remember { mutableStateOf(Route.MAP) }
    val mapViewModel: MapViewModel = viewModel {
        MapViewModel(
            grid = container.grid,
            locationProvider = container.locationProvider,
            mapRenderer = container.mapRenderer,
            revealNearbyCells = container.revealNearbyCells,
            markCellVisited = container.markCellVisited,
            observePlace = container.observePlace,
            placeRepository = container.placeRepository,
            explorationRepository = container.explorationRepository,
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (route) {
            Route.MAP -> MapScreen(
                viewModel = mapViewModel,
                mapAdapter = container.mapRenderer,
                onTakePhoto = { route = Route.CAMERA },
                onOpenJournal = { route = Route.JOURNAL },
                onOpenAr = { route = Route.AR },
                modifier = contentModifier,
            )

            Route.CAMERA -> {
                val cameraViewModel: CameraViewModel = viewModel {
                    CameraViewModel(
                        grid = container.grid,
                        locationProvider = container.locationProvider,
                        cameraController = container.cameraController,
                        createMemory = container.createMemory,
                    )
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { cameraViewModel.reset() }
                androidx.activity.compose.BackHandler {
                    mapViewModel.refresh()
                    route = Route.MAP
                }
                CameraScreen(
                    viewModel = cameraViewModel,
                    cameraController = container.cameraController,
                    onDone = {
                        mapViewModel.refresh()
                        route = Route.MAP
                    },
                    modifier = contentModifier,
                )
            }

            Route.AR -> com.rainingtrace.feature.ar.ArScreen(
                controller = container.arController as com.rainingtrace.platform.ar.ArCoreController,
                onDone = { route = Route.MAP },
                modifier = contentModifier,
            )

            Route.JOURNAL -> {
                val journalViewModel: JournalViewModel = viewModel {
                    JournalViewModel(memoryRepository = container.memoryRepository)
                }
                androidx.compose.runtime.LaunchedEffect(Unit) { journalViewModel.refresh() }
                androidx.activity.compose.BackHandler {
                    mapViewModel.refresh()
                    route = Route.MAP
                }
                androidx.compose.foundation.layout.Box(modifier = contentModifier) {
                    JournalScreen(viewModel = journalViewModel)
                    androidx.compose.material3.TextButton(
                        onClick = {
                            mapViewModel.refresh()
                            route = Route.MAP
                        },
                        modifier = Modifier.padding(8.dp),
                    ) { androidx.compose.material3.Text("← 地图") }
                }
            }
        }
    }
}
