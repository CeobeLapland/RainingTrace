package com.rainingtrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.core.ui.theme.RainingTraceTheme
import com.rainingtrace.feature.map.MapScreen
import com.rainingtrace.feature.map.MapViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RainingTraceApplication).appContainer
        setContent {
            RainingTraceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapRoute(
                        container = container,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun MapRoute(
    container: com.rainingtrace.core.common.AppContainer,
    modifier: Modifier = Modifier,
) {
    val viewModel: MapViewModel = viewModel {
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
    MapScreen(
        viewModel = viewModel,
        mapAdapter = container.mapRenderer,
        modifier = modifier,
    )
}

@Composable
fun AppTitle(modifier: Modifier = Modifier) {
    Text(
        text = "雨迹 RainingTrace",
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier,
    )
}
