package com.rainingtrace.feature.ar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.opengl.GLSurfaceView
import com.rainingtrace.domain.ar.ArSessionState
import com.rainingtrace.platform.ar.ArCoreController

/**
 * AR-0 屏：GLSurfaceView 渲染相机背景，虚拟对象以 Compose 精灵叠加。
 *
 * 降级路径（06_地图专项 §11）：UNSUPPORTED/ERROR/PERMISSION_DENIED
 * 显示说明卡片，绝不黑屏。
 */
@Composable
fun ArScreen(
    controller: ArCoreController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by controller.state.collectAsStateWithLifecycle()
    val placed by controller.placedObjects.collectAsStateWithLifecycle()

    BackHandler(onBack = onDone)

    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(controller)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glView.onResume()
                    controller.setDisplayRotation(displayRotation(context))
                    controller.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    controller.onPause()
                    glView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 进入屏幕时 lifecycle 可能已 RESUMED
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            glView.onResume()
            controller.setDisplayRotation(displayRotation(context))
            controller.onResume()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { glView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    detectTapGestures { offset ->
                        controller.onUserTap(offset.x, offset.y)
                    }
                },
        )

        // 虚拟对象精灵（MVP：发光圆点代表"湖之精灵"）
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val boxWidth = constraints.maxWidth.toFloat()
            val boxHeight = constraints.maxHeight.toFloat()
            val spriteHalf = with(LocalDensity.current) { 28.dp.toPx() }
            placed.filter { it.visible }.forEach { obj ->
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (obj.normalizedX * boxWidth - spriteHalf).toInt(),
                                (obj.normalizedY * boxHeight - spriteHalf).toInt(),
                            )
                        }
                        .size(56.dp)
                        .background(Color(0x33AFFFE0), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(Color(0xFF7FE3C0), CircleShape),
                    )
                }
            }
        }

        // 状态提示
        when (state) {
            ArSessionState.UNSUPPORTED -> StatusCard(
                "这台设备暂不支持 ARCore",
                "AR 是雨迹的出口，但不是入口——没有它游戏照常运行。",
                Modifier.align(Alignment.BottomCenter),
            )
            ArSessionState.PERMISSION_DENIED -> StatusCard(
                "没有相机权限，AR 无法启动",
                "可以先去系统设置里允许相机，或直接返回。",
                Modifier.align(Alignment.BottomCenter),
            )
            ArSessionState.ERROR -> StatusCard(
                "AR 出错了",
                "稍后再试，或先返回地图。",
                Modifier.align(Alignment.BottomCenter),
            )
            ArSessionState.INITIALIZING -> StatusCard(
                "缓慢移动手机，扫描地面…",
                "看到光点后点击地面，放一只湖之精灵。",
                Modifier.align(Alignment.BottomCenter),
            )
            ArSessionState.TRACKING_LOST -> StatusCard(
                "跟踪丢失，移动慢一点",
                null,
                Modifier.align(Alignment.BottomCenter),
            )
            ArSessionState.READY -> StatusCard(
                if (placed.isEmpty()) "点击地面放置精灵" else "精灵已放置，可继续放置",
                null,
                Modifier.align(Alignment.BottomCenter),
            )
            ArSessionState.SUPPORTED -> Unit
        }

        TextButton(
            onClick = onDone,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Text("← 返回", color = Color.White)
        }
    }
}

@Composable
private fun StatusCard(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun displayRotation(context: android.content.Context): Int {
    val rotation = (context.getSystemService(android.content.Context.WINDOW_SERVICE)
        as android.view.WindowManager).defaultDisplay.rotation
    return when (rotation) {
        android.view.Surface.ROTATION_90 -> 90
        android.view.Surface.ROTATION_180 -> 180
        android.view.Surface.ROTATION_270 -> 270
        else -> 0
    }
}
