package com.rainingtrace.feature.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.memory.AudioNoteController
import com.rainingtrace.domain.memory.CapturedAudio
import com.rainingtrace.domain.memory.CapturedMedia
import com.rainingtrace.domain.memory.CreateMemoryUseCase
import com.rainingtrace.domain.memory.MemoryDraft
import com.rainingtrace.domain.memory.Mood
import com.rainingtrace.platform.camera.CameraXController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CameraPhase { EDITING, SAVING }

data class CameraUiState(
    val text: String = "",
    val mood: Mood? = null,
    /** 已拍下的照片（本地 URI），可多张。 */
    val photoUris: List<String> = emptyList(),
    /** 已录的一段语音。 */
    val audio: CapturedAudio? = null,
    val isRecording: Boolean = false,
    /** 录音计时（毫秒），仅在录音中刷新。 */
    val recordingElapsedMs: Long = 0L,
    /** 正在回放的语音 URI；null = 未在播放。 */
    val playingRef: String? = null,
    val phase: CameraPhase = CameraPhase.EDITING,
    val error: String? = null,
)

/**
 * 随手拍 → 记忆。三类输入都可选，至少给一样：
 * 多张照片 / 一段语音 / 纯文字（降级路径，相机与麦克风都不可用时仍可记录）。
 */
class CameraViewModel(
    private val clock: WorldClock,
    private val grid: HexGrid,
    private val locationProvider: LocationProvider,
    private val cameraController: CameraXController,
    private val audioNote: AudioNoteController,
    private val createMemory: CreateMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var recordingStartedAtMs = 0L
    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            audioNote.isRecording.collect { recording ->
                _uiState.value = _uiState.value.copy(
                    isRecording = recording,
                    recordingElapsedMs = if (recording) _uiState.value.recordingElapsedMs else 0L,
                )
                if (recording) startTicker() else stopTicker()
            }
        }
        viewModelScope.launch {
            audioNote.playingRef.collect { ref ->
                _uiState.value = _uiState.value.copy(playingRef = ref)
            }
        }
    }

    /** 每次进入相机屏重置草稿（ViewModel 作用域跨导航保留）。 */
    fun reset() {
        onLeave()
        _uiState.value = CameraUiState()
    }

    /** 离开相机屏：停掉录音与回放，避免占着麦克风/音频焦点。 */
    fun onLeave() {
        stopTicker()
        audioNote.stopPlayback()
        if (audioNote.isRecording.value) audioNote.cancelRecording()
    }

    fun onTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(text = text, error = null)
    }

    /** 再点一次同一个心情 = 取消选择。 */
    fun onMoodPicked(mood: Mood) {
        val next = if (_uiState.value.mood == mood) null else mood
        _uiState.value = _uiState.value.copy(mood = next, error = null)
    }

    fun onCapture(onUnavailable: () -> Unit) {
        if (_uiState.value.photoUris.size >= MAX_PHOTOS) {
            _uiState.value = _uiState.value.copy(error = "最多 $MAX_PHOTOS 张照片")
            return
        }
        viewModelScope.launch {
            runCatching { cameraController.capture(currentCoordinate()) }
                .onSuccess { media: CapturedMedia ->
                    _uiState.value = _uiState.value.copy(
                        photoUris = _uiState.value.photoUris + media.localUri,
                        error = null,
                    )
                }
                .onFailure {
                    onUnavailable()
                    _uiState.value = _uiState.value.copy(error = "拍照失败，可以先写字条")
                }
        }
    }

    fun onRemovePhoto(uri: String) {
        _uiState.value = _uiState.value.copy(
            photoUris = _uiState.value.photoUris - uri,
            error = null,
        )
    }

    /** 录音开关：开始 / 停止。失败时回调 onUnavailable 由 UI 降级提示。 */
    fun onToggleRecording(onUnavailable: () -> Unit) {
        if (_uiState.value.isRecording) {
            val recorded = audioNote.stopRecording()
            stopTicker()
            _uiState.value = _uiState.value.copy(
                isRecording = false,
                recordingElapsedMs = 0L,
                audio = recorded ?: _uiState.value.audio,
                error = if (recorded == null) "录音太短了，再按一次重新录" else null,
            )
            return
        }
        if (!audioNote.startRecording()) {
            onUnavailable()
            _uiState.value = _uiState.value.copy(error = "无法录音，可以先写字条")
            return
        }
        recordingStartedAtMs = clock.now().toEpochMilli()
        _uiState.value = _uiState.value.copy(
            isRecording = true,
            recordingElapsedMs = 0L,
            error = null,
        )
    }

    fun onRemoveAudio() {
        audioNote.stopPlayback()
        _uiState.value = _uiState.value.copy(audio = null, error = null)
    }

    /** 麦克风权限被拒：只提示，不影响照片/文字（降级路径）。 */
    fun onAudioPermissionDenied() {
        _uiState.value = _uiState.value.copy(error = "没有麦克风权限，可以先写字条")
    }

    /** 语音回看：再点一次停止播放。 */
    fun onTogglePlayback(uri: String) {
        if (_uiState.value.playingRef == uri) {
            audioNote.stopPlayback()
        } else {
            audioNote.play(uri)
        }
    }

    fun canSave(): Boolean {
        val s = _uiState.value
        return s.phase == CameraPhase.EDITING &&
            !s.isRecording &&
            (s.text.isNotBlank() || s.photoUris.isNotEmpty() || s.audio != null)
    }

    fun onSave(onSaved: () -> Unit) {
        val s = _uiState.value
        if (!canSave()) return
        _uiState.value = s.copy(phase = CameraPhase.SAVING)
        audioNote.stopPlayback()
        viewModelScope.launch {
            val coordinate = currentCoordinate()
            val draft = MemoryDraft(
                coordinate = coordinate,
                text = s.text,
                mood = s.mood,
                media = s.photoUris.map { uri ->
                    CapturedMedia(
                        localUri = uri,
                        capturedAtEpochMs = clock.now().toEpochMilli(),
                        coordinate = coordinate,
                    )
                },
                audio = s.audio,
            )
            runCatching { createMemory(draft) }
                .onSuccess { onSaved() }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        phase = CameraPhase.EDITING,
                        error = "保存失败，请重试",
                    )
                }
        }
    }

    private fun startTicker() {
        stopTicker()
        ticker = viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                _uiState.value = _uiState.value.copy(
                    recordingElapsedMs = clock.now().toEpochMilli() - recordingStartedAtMs,
                )
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun currentCoordinate() =
        locationProvider.latest?.coordinate ?: grid.origin

    companion object {
        /** 单条记忆的照片上限：够用又不至于把卡片撑爆。 */
        const val MAX_PHOTOS = 9
        private const val TICK_MS = 200L
    }
}
