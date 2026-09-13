package com.rainingtrace.platform.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.memory.CapturedMedia
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * CameraX 采集适配器（06_地图专项 §12）。
 *
 * 职责：拍照 → 存 app 私有目录 → 返回 [CapturedMedia]。
 * 业务层不接触 CameraX 类型；进入后台/失败可降级（调用方处理异常）。
 */
class CameraXController(
    private val context: Context,
    private val clock: WorldClock,
) {
    private var imageCapture: ImageCapture? = null

    private val executor: Executor by lazy { ContextCompat.getMainExecutor(context) }

    /** 绑定预览后由 feature 层调用，准备就绪即可拍照。 */
    fun buildImageCapture(): ImageCapture =
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
            .also { imageCapture = it }

    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    suspend fun initializeProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                executor,
            )
        }

    /**
     * 拍摄一张照片，保存到 app 私有 files 目录。
     * @throws ImageCaptureException / IllegalStateException 由调用方降级处理
     */
    suspend fun capture(coordinate: com.rainingtrace.domain.map.WorldCoordinate? = null): CapturedMedia {
        val capture = imageCapture ?: error("camera not prepared")
        val file = File(context.filesDir, "photos/mem_${clock.now().toEpochMilli()}.jpg")
            .also { it.parentFile?.mkdirs() }
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        suspendCancellableCoroutine<Unit> { cont ->
            capture.takePicture(
                output,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        cont.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }

        return CapturedMedia(
            localUri = Uri.fromFile(file).toString(),
            capturedAtEpochMs = clock.now().toEpochMilli(),
            coordinate = coordinate,
        )
    }
}
