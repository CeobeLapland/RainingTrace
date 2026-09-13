package com.rainingtrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rainingtrace.core.ui.theme.RainingTraceTheme
import com.rainingtrace.feature.shell.RainingTraceApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RainingTraceApplication).appContainer
        setContent {
            RainingTraceTheme {
                RainingTraceApp(container = container)
            }
        }
    }
}
