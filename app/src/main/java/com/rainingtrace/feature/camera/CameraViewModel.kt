package com.rainingtrace.feature.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.domain.map.HexGrid
import com.rainingtrace.domain.map.LocationProvider
import com.rainingtrace.domain.memory.CreateMemoryUseCase
import com.rainingtrace.domain.memory.MemoryDraft
import com.rainingtrace.domain.memory.Mood
import com.rainingtrace.platform.camera.CameraXController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CameraPhase { EDITING, SAVING }

data class CameraUiState(
    val text: String = "",
    val mood: Mood? = null,
    val capturedPhotoUri: String? = null,
    val phase: CameraPhase = CameraPhase.EDITING,
    val error: String? = null,
)

/**
 * 随手拍 → 记忆。照片是可选输入：无照片也能存文字记忆（降级路径）。
 */
class CameraViewModel(
    private val grid: HexGrid,
    private val locationProvider: LocationProvider,
    private val cameraController: CameraXController,
    private val createMemory: CreateMemoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** 每次进入相机屏重置草稿（ViewModel 作用域跨导航保留）。 */
    fun reset() {
        _uiState.value = CameraUiState()
    }

    fun onTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(text = text, error = null)
    }

    fun onMoodPicked(mood: Mood) {
        _uiState.value = _uiState.value.copy(mood = mood, error = null)
    }

    fun onCapture(onUnavailable: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                cameraController.capture(currentCoordinate())
            }.onSuccess { media ->
                _uiState.value = _uiState.value.copy(
                    capturedPhotoUri = media.localUri,
                    error = null,
                )
            }.onFailure {
                onUnavailable()
                _uiState.value = _uiState.value.copy(
                    error = "拍照失败，可以先只写字条",
                )
            }
        }
    }

    fun canSave(): Boolean {
        val s = _uiState.value
        return s.phase == CameraPhase.EDITING && (s.text.isNotBlank() || s.capturedPhotoUri != null)
    }

    fun onSave(onSaved: () -> Unit) {
        val s = _uiState.value
        if (!canSave()) return
        _uiState.value = s.copy(phase = CameraPhase.SAVING)
        viewModelScope.launch {
            val draft = MemoryDraft(
                coordinate = currentCoordinate(),
                text = s.text,
                mood = s.mood,
                media = s.capturedPhotoUri?.let { uri ->
                    com.rainingtrace.domain.memory.CapturedMedia(
                        localUri = uri,
                        capturedAtEpochMs = System.currentTimeMillis(),
                        coordinate = currentCoordinate(),
                    )
                },
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

    private fun currentCoordinate() =
        locationProvider.latest?.coordinate ?: grid.origin
}
