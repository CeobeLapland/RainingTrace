package com.rainingtrace.feature.camera

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rainingtrace.R
import com.rainingtrace.core.common.AppContainer
import com.rainingtrace.feature.ar.ArScreen
import com.rainingtrace.platform.ar.ArCoreController

enum class CameraMode { PHOTO, AR }

/**
 * 摄像 Tab：全屏沉浸。两种模式可切换：
 * - [CameraMode.PHOTO]：随手拍 → 记忆（原相机屏）。
 * - [CameraMode.AR]：AR 世界对象（原 AR 屏）。
 * 关闭/系统返回都回到来源一级 Tab。
 */
@Composable
fun CameraRoute(
    container: AppContainer,
    arController: ArCoreController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(CameraMode.PHOTO) }

    BackHandler { onClose() }

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            CameraMode.PHOTO -> {
                val cameraViewModel: CameraViewModel = viewModel {
                    CameraViewModel(
                        grid = container.grid,
                        locationProvider = container.locationProvider,
                        cameraController = container.cameraController,
                        createMemory = container.createMemory,
                    )
                }
                LaunchedEffect(Unit) { cameraViewModel.reset() }
                CameraScreen(
                    viewModel = cameraViewModel,
                    cameraController = container.cameraController,
                    onDone = onClose,
                )
            }

            CameraMode.AR -> {
                ArScreen(controller = arController, onDone = onClose)
            }
        }

        // 关闭：回来源 Tab
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(CONTROL_SCRIM)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // 模式切换：拍照 | AR
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(CONTROL_SCRIM)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModePill("拍照", mode == CameraMode.PHOTO) { mode = CameraMode.PHOTO }
            ModePill("AR", mode == CameraMode.AR) { mode = CameraMode.AR }
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent,
        label = "modePillBg",
    )
    val fg = if (selected) Color(0xFF263036) else Color.White
    Box(
        modifier = Modifier
            .height(32.dp)
            .width(72.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

private val CONTROL_SCRIM = Color(0x66000000)
