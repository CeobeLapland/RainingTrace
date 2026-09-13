package com.rainingtrace.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.domain.memory.Mood
import com.rainingtrace.platform.camera.CameraXController

/**
 * 随手拍 → 记忆编辑器（单屏完成，MVP 简化）。
 *
 * 流程：授权 → 预览 → 拍照 → 写字条/选心情 → 存入日记。
 * 相机不可用/被拒 → 降级为"无照片写记忆"（GDD：任何功能必须有降级路径）。
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    cameraController: CameraXController,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 权限就绪 + PreviewView 就绪 → 绑定 CameraX
    LaunchedEffect(hasPermission, previewView) {
        val view = previewView
        if (hasPermission && view != null && !cameraUnavailable) {
            runCatching {
                val provider = cameraController.initializeProvider()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = view.surfaceProvider
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraController.cameraSelector,
                    preview,
                    cameraController.buildImageCapture(),
                )
            }.onFailure {
                android.util.Log.e("CameraScreen", "bind failed", it)
                cameraUnavailable = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (!hasPermission || cameraUnavailable) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (cameraUnavailable) {
                        "相机不可用，可以先写一条无照片的记忆。"
                    } else {
                        "没有相机权限也可以写记忆。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            previewView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.capturedPhotoUri?.let {
                    Text(
                        text = "已拍照 ✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = { viewModel.onCapture(onUnavailable = { cameraUnavailable = true }) },
                    enabled = uiState.phase != CameraPhase.SAVING,
                ) { Text(if (uiState.capturedPhotoUri == null) "拍照" else "重拍") }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.text,
            onValueChange = viewModel::onTextChanged,
            label = { Text("这一刻对你意味着什么…") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Mood.entries.forEach { mood ->
                OutlinedButton(
                    onClick = { viewModel.onMoodPicked(mood) },
                    shape = CircleShape,
                ) {
                    Text(mood.label(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                Text("取消")
            }
            Button(
                onClick = { viewModel.onSave(onSaved = onDone) },
                enabled = viewModel.canSave(),
                modifier = Modifier.weight(1f),
            ) { Text("存入日记") }
        }
    }
}

private fun Mood.label(): String = when (this) {
    Mood.CALM -> "平静"
    Mood.HAPPY -> "开心"
    Mood.CURIOUS -> "好奇"
    Mood.LONELY -> "孤独"
    Mood.EXCITED -> "兴奋"
    Mood.MELANCHOLY -> "低落"
}
