package com.rainingtrace.domain.memory

import kotlinx.coroutines.flow.StateFlow

/**
 * 语音记录与回放（06_地图专项 §12：外部能力一律走适配层）。
 *
 * 录音是**可选输入**：没有麦克风权限或设备不支持时，调用方必须能降级为纯文字记忆。
 * MVP 每条记忆最多一段语音。
 */
interface AudioNoteController {

    val isRecording: StateFlow<Boolean>

    /** 正在回放的本地 URI；null = 未在播放。 */
    val playingRef: StateFlow<String?>

    /** 开始录音；返回 false 表示不可用（权限/设备），调用方应降级。 */
    fun startRecording(): Boolean

    /** 停止并返回录音产物；录音过短或失败返回 null。 */
    fun stopRecording(): CapturedAudio?

    /** 放弃当前录音（删除临时文件）。 */
    fun cancelRecording()

    fun play(localUri: String)

    fun stopPlayback()

    /** 离开页面时释放录音器与播放器。 */
    fun release()
}
