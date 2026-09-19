package com.rainingtrace.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rainingtrace.R
import com.rainingtrace.core.ui.LocalImage
import com.rainingtrace.core.ui.label
import com.rainingtrace.domain.memory.Mood
import com.rainingtrace.platform.camera.CameraXController
import kotlinx.coroutines.launch

/**
 * 随手拍 → 记忆编辑器（全屏沉浸）。
 *
 * 布局：相机预览铺满，底部一张圆角编辑面板（心情横滑 / 文字 / 媒体条 / 保存）。
 * 三类输入都可选且至少给一样：多张照片、一段语音、纯文字。
 * 相机或麦克风不可用 → 对应控件禁用并提示，仍可完成记忆（降级路径）。
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.CAMERA))
    }
    var hasAudioPermission by remember {
        mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO))
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var recordAfterGrant by remember { mutableStateOf(false) }
    var enlargedPhoto by remember { mutableStateOf<String?>(null) }

    // PreviewView 在 remember 中创建一次，避免在 AndroidView.factory 里写状态
    // 触发重组、进而取消绑定协程（LeftCompositionCancellationException）。
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    // 麦克风权限按需申请：只在用户真的按下录音时才弹。
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        when {
            granted && recordAfterGrant -> {
                recordAfterGrant = false
                viewModel.onToggleRecording(onUnavailable = {})
            }
            !granted -> {
                recordAfterGrant = false
                viewModel.onAudioPermissionDenied()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 权限就绪 → 绑定 CameraX。
    // 离开组合（含摄像 Tab 内切到 AR 模式）必须显式解绑：同一 NavBackStackEntry
    // 仍处于 RESUMED，CameraX 不会因生命周期自动释放，会与 ARCore 抢相机。
    DisposableEffect(hasCameraPermission, cameraUnavailable, lifecycleOwner) {
        var provider: androidx.camera.lifecycle.ProcessCameraProvider? = null
        val job = if (hasCameraPermission && !cameraUnavailable) {
            scope.launch {
                runCatching {
                    val p = cameraController.initializeProvider()
                    provider = p
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    p.unbindAll()
                    p.bindToLifecycle(
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
        } else {
            null
        }
        onDispose {
            job?.cancel()
            provider?.unbindAll()
        }
    }

    // 离开相机屏：停录音与回放，别占着麦克风。
    DisposableEffect(Unit) {
        onDispose { viewModel.onLeave() }
    }

    val cameraReady = hasCameraPermission && !cameraUnavailable
    val saving = uiState.phase == CameraPhase.SAVING

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (cameraReady) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CameraUnavailableHint(hasCameraPermission)
        }

        EditorPanel(
            uiState = uiState,
            cameraReady = cameraReady,
            saving = saving,
            onText = viewModel::onTextChanged,
            onMood = viewModel::onMoodPicked,
            onCapture = { viewModel.onCapture(onUnavailable = { cameraUnavailable = true }) },
            onRemovePhoto = viewModel::onRemovePhoto,
            onEnlarge = { enlargedPhoto = it },
            onMic = {
                if (uiState.isRecording) {
                    viewModel.onToggleRecording(onUnavailable = {})
                } else if (hasAudioPermission) {
                    viewModel.onToggleRecording(onUnavailable = {})
                } else {
                    recordAfterGrant = true
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onTogglePlayback = viewModel::onTogglePlayback,
            onRemoveAudio = viewModel::onRemoveAudio,
            onCancel = onDone,
            onSave = { viewModel.onSave(onSaved = onDone) },
            canSave = viewModel.canSave(),
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        enlargedPhoto?.let { uri ->
            PhotoViewer(uri = uri, onClose = { enlargedPhoto = null })
        }
    }
}

@Composable
private fun EditorPanel(
    uiState: CameraUiState,
    cameraReady: Boolean,
    saving: Boolean,
    onText: (String) -> Unit,
    onMood: (Mood) -> Unit,
    onCapture: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onEnlarge: (String) -> Unit,
    onMic: () -> Unit,
    onTogglePlayback: (String) -> Unit,
    onRemoveAudio: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MoodRow(selected = uiState.mood, onPick = onMood)

            OutlinedTextField(
                value = uiState.text,
                onValueChange = onText,
                placeholder = { Text("这一刻对你意味着什么…") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            MediaRow(
                uiState = uiState,
                cameraReady = cameraReady,
                saving = saving,
                onCapture = onCapture,
                onRemovePhoto = onRemovePhoto,
                onEnlarge = onEnlarge,
                onMic = onMic,
                onTogglePlayback = onTogglePlayback,
                onRemoveAudio = onRemoveAudio,
            )

            uiState.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = onSave,
                    enabled = canSave && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (saving) "保存中…" else "存入日记")
                }
            }
        }
    }
}

/** 心情横滑：选中态用主色实心，再点一次取消。 */
@Composable
private fun MoodRow(
    selected: Mood?,
    onPick: (Mood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "心情",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Mood.entries.forEach { mood ->
            val isSelected = mood == selected
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.clickable { onPick(mood) },
            ) {
                Text(
                    text = mood.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 媒体条：左＝已拍内容（可横滑回看/删除），右＝录音与快门。 */
@Composable
private fun MediaRow(
    uiState: CameraUiState,
    cameraReady: Boolean,
    saving: Boolean,
    onCapture: () -> Unit,
    onRemovePhoto: (String) -> Unit,
    onEnlarge: (String) -> Unit,
    onMic: () -> Unit,
    onTogglePlayback: (String) -> Unit,
    onRemoveAudio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.photoUris.isEmpty() && uiState.audio == null) {
                Text(
                    text = "还没有照片或语音",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.photoUris.forEach { uri ->
                PhotoThumb(
                    uri = uri,
                    onEnlarge = { onEnlarge(uri) },
                    onRemove = { onRemovePhoto(uri) },
                )
            }
            uiState.audio?.let { audio ->
                AudioThumb(
                    durationMs = audio.durationMs,
                    isPlaying = uiState.playingRef == audio.localUri,
                    onToggle = { onTogglePlayback(audio.localUri) },
                    onRemove = onRemoveAudio,
                )
            }
        }

        MicButton(
            isRecording = uiState.isRecording,
            elapsedMs = uiState.recordingElapsedMs,
            enabled = !saving,
            onClick = onMic,
        )
        ShutterButton(enabled = cameraReady && !saving, onClick = onCapture)
    }
}

@Composable
private fun PhotoThumb(
    uri: String,
    onEnlarge: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(THUMB_SIZE)) {
        LocalImage(
            localUri = uri,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onEnlarge),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(BADGE_SCRIM)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "删除照片",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** 已录语音：点按播放/停止，右上角可删。 */
@Composable
private fun AudioThumb(
    durationMs: Long,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(THUMB_SIZE)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .clickable(onClick = onToggle),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(
                    if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play,
                ),
                contentDescription = if (isPlaying) "停止" else "播放",
                tint = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(BADGE_SCRIM)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "删除语音",
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun MicButton(
    isRecording: Boolean,
    elapsedMs: Long,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isRecording) RECORDING_RED else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = if (isRecording) "停止录音" else "录一段语音",
                tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (isRecording) {
            Text(
                text = formatDuration(elapsedMs),
                style = MaterialTheme.typography.labelMedium,
                color = RECORDING_RED,
            )
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.25f else 0.10f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color(0x55FFFFFF)),
        )
    }
}

/** 大图回看：点任意处关闭。 */
@Composable
private fun PhotoViewer(uri: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VIEWER_SCRIM)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        LocalImage(
            localUri = uri,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            targetPx = VIEWER_TARGET_PX,
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(BADGE_SCRIM),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 相机不可用时的降级底：仍然能写字条/录音。 */
@Composable
private fun CameraUnavailableHint(hasPermission: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasPermission) "相机不可用" else "没有相机权限",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "不影响记录：可以写一段话，或录一段语音。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xCCFFFFFF),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** 毫秒 → "m:ss"。 */
private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

private val THUMB_SIZE = 58.dp
private val BADGE_SCRIM = Color(0xCC000000)
private val VIEWER_SCRIM = Color(0xF2000000)
private val RECORDING_RED = Color(0xFFD9534F)
private const val VIEWER_TARGET_PX = 1080
