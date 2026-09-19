package com.rainingtrace.platform.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.memory.AudioNoteController
import com.rainingtrace.domain.memory.CapturedAudio
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 语音适配器：MediaRecorder 录音 + MediaPlayer 回放。
 *
 * 产出存 app 私有 files/audio，与照片同构（domain 只见 URI）。
 * 所有失败路径都不抛给调用方：录音失败返回 false、停止失败返回 null，
 * 由 feature 层降级为纯文字记忆。
 */
class AndroidAudioNoteController(
    private val context: Context,
    private val clock: WorldClock,
) : AudioNoteController {

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _playingRef = MutableStateFlow<String?>(null)
    override val playingRef: StateFlow<String?> = _playingRef.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartedAtMs = 0L
    private var player: MediaPlayer? = null

    override fun startRecording(): Boolean {
        if (_isRecording.value) return true
        stopPlayback()

        val file = File(context.filesDir, "audio/mem_${clock.now().toEpochMilli()}.m4a")
            .also { it.parentFile?.mkdirs() }
        val rec = newRecorder()
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(AUDIO_BITRATE)
            rec.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            recordingFile = file
            recordingStartedAtMs = clock.now().toEpochMilli()
            _isRecording.value = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "startRecording failed", e)
            runCatching { rec.release() }
            file.delete()
            false
        }
    }

    override fun stopRecording(): CapturedAudio? {
        val rec = recorder ?: return null
        val file = recordingFile
        recorder = null
        recordingFile = null
        _isRecording.value = false
        val durationMs = (clock.now().toEpochMilli() - recordingStartedAtMs).coerceAtLeast(0L)

        return try {
            rec.stop()
            rec.release()
            if (file == null || !file.exists() || file.length() == 0L) {
                file?.delete()
                null
            } else if (durationMs < MIN_DURATION_MS) {
                // 太短（多为误触）：直接丢弃，避免留下无意义的空录音。
                file.delete()
                null
            } else {
                CapturedAudio(
                    localUri = Uri.fromFile(file).toString(),
                    durationMs = durationMs,
                    capturedAtEpochMs = recordingStartedAtMs,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopRecording failed", e)
            runCatching { rec.release() }
            file?.delete()
            null
        }
    }

    override fun cancelRecording() {
        val rec = recorder ?: return
        recorder = null
        _isRecording.value = false
        runCatching { rec.stop() }
        runCatching { rec.release() }
        recordingFile?.delete()
        recordingFile = null
    }

    override fun play(localUri: String) {
        stopPlayback()
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(context, Uri.parse(localUri))
            mp.setOnCompletionListener { stopPlayback() }
            mp.setOnErrorListener { _, _, _ ->
                stopPlayback()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            _playingRef.value = localUri
        }.onFailure {
            Log.w(TAG, "play failed", it)
            stopPlayback()
        }
    }

    override fun stopPlayback() {
        player?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        player = null
        _playingRef.value = null
    }

    override fun release() {
        if (_isRecording.value) cancelRecording()
        stopPlayback()
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private companion object {
        const val TAG = "AndroidAudioNote"
        const val AUDIO_BITRATE = 96_000
        const val AUDIO_SAMPLE_RATE = 44_100

        /** 短于这个时长的录音视为误触，不保存。 */
        const val MIN_DURATION_MS = 800L
    }
}
