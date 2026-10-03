package com.rainingtrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.rainingtrace.core.art.LocalArtSource
import com.rainingtrace.core.ui.theme.RainingTraceTheme
import com.rainingtrace.feature.shell.RainingTraceApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RainingTraceApplication).appContainer
        setContent {
            RainingTraceTheme {
                // 美术图在这里注入一次，整个组合树（含地图详情卡那种深层私有组件）都能取到；
                // 取不到时 ArtIcon 自动回退到占位，所以没 provide 的场景也不会崩。
                CompositionLocalProvider(LocalArtSource provides container.artSource) {
                    RainingTraceApp(container = container)
                }
            }
        }
    }
}
