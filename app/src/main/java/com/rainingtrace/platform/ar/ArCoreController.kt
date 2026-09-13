package com.rainingtrace.platform.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import android.Manifest
import androidx.core.content.ContextCompat
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.ar.ArAnchorType
import com.rainingtrace.domain.ar.ArController
import com.rainingtrace.domain.ar.ArObject
import com.rainingtrace.domain.ar.ArSessionState
import com.rainingtrace.domain.ar.PlacedArObject
import com.rainingtrace.domain.map.LocationProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RT-AR-001~004: ARCore 实现（AR-0：本地平面放置一个世界对象）。
 *
 * 设计约束（06_地图专项 §9/§11）：
 * - ARCore 类型只存在于本文件；domain/feature 只见 [ArController]。
 * - 相机背景用标准 OES 纹理渲染；放置的虚拟对象投影为屏幕坐标，
 *   由 feature 层用 Compose 叠加精灵（MVP 不做 3D 模型管线）。
 * - 所有失败路径落到显式状态，绝不卡死在黑屏。
 */
class ArCoreController(
    private val context: Context,
    private val locationProvider: LocationProvider,
    @Suppress("unused") private val clock: WorldClock,
) : ArController, GLSurfaceView.Renderer {

    private val _state = MutableStateFlow(ArSessionState.INITIALIZING)
    override val state: StateFlow<ArSessionState> = _state.asStateFlow()

    private val _placedObjects = MutableStateFlow<List<PlacedArObject>>(emptyList())
    override val placedObjects: StateFlow<List<PlacedArObject>> = _placedObjects.asStateFlow()

    private var session: Session? = null
    private var displayRotationDegrees = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    @Volatile
    private var _hasTracked = false

    /** 是否曾经进入过跟踪（用于 UI 提示"移动手机扫描地面"）。 */
    override val hasTracked: Boolean get() = _hasTracked

    private val anchors = mutableListOf<Pair<ArObject, Anchor>>()
    private val pendingTap = object {
        @Volatile var x: Float = Float.NaN
        @Volatile var y: Float = Float.NaN
        fun consume(): Pair<Float, Float>? {
            if (x.isNaN()) return null
            val p = x to y
            x = Float.NaN
            return p
        }
    }

    private var backgroundTexture = 0
    private var shaderProgram = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0
    private val quadCoords: FloatBuffer = directBuffer(FULL_RECTANGLE_COORDS)
    private val texCoords: FloatBuffer = directBuffer(FULL_RECTANGLE_TEX_COORDS)

    init {
        // RT-AR-001 能力检测：创建失败即 UNSUPPORTED（不弹安装，MVP 降级即可）
        try {
            session = Session(context)
            _state.value = ArSessionState.SUPPORTED
        } catch (e: UnavailableException) {
            android.util.Log.w(TAG, "ARCore unavailable", e)
            _state.value = ArSessionState.UNSUPPORTED
        } catch (e: Exception) {
            android.util.Log.e(TAG, "session create failed", e)
            _state.value = ArSessionState.ERROR
        }
    }

    override fun onResume() {
        val s = session ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = ArSessionState.PERMISSION_DENIED
            return
        }
        try {
            s.resume()
            if (_state.value == ArSessionState.TRACKING_LOST) _state.value = ArSessionState.INITIALIZING
        } catch (e: CameraNotAvailableException) {
            _state.value = ArSessionState.ERROR
        }
    }

    override fun onPause() {
        session?.pause()
    }

    override fun onUserTap(xPx: Float, yPx: Float) {
        pendingTap.x = xPx
        pendingTap.y = yPx
    }

    // ---- GLSurfaceView.Renderer ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (session == null) return
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        backgroundTexture = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, backgroundTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        session?.setCameraTextureName(backgroundTexture)
        shaderProgram = buildProgram()
        positionAttrib = GLES20.glGetAttribLocation(shaderProgram, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(shaderProgram, "a_TexCoordinate")
        textureUniform = GLES20.glGetUniformLocation(shaderProgram, "u_Texture")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        session?.setDisplayGeometry(displayRotationDegrees, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = session ?: return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val frame = try {
            s.update()
        } catch (e: CameraNotAvailableException) {
            _state.value = ArSessionState.ERROR
            return
        }
        val camera = frame.camera
        if (camera.trackingState == TrackingState.TRACKING) {
            _hasTracked = true
        }

        handleTap(frame)
        drawBackground(frame)

        val tracking = camera.trackingState == TrackingState.TRACKING
        _state.value = when (_state.value) {
            ArSessionState.READY, ArSessionState.TRACKING_LOST ->
                if (tracking) ArSessionState.READY else ArSessionState.TRACKING_LOST
            ArSessionState.SUPPORTED, ArSessionState.INITIALIZING ->
                if (tracking) ArSessionState.READY else ArSessionState.INITIALIZING
            else -> _state.value
        }
        updateProjections(frame)
    }

    override fun setDisplayRotation(degrees: Int) {
        displayRotationDegrees = degrees
        if (viewportWidth > 0) {
            session?.setDisplayGeometry(degrees, viewportWidth, viewportHeight)
        }
    }

    // ---- internals ----

    private fun handleTap(frame: Frame) {
        val tap = pendingTap.consume() ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        val hits: List<HitResult> = frame.hitTest(tap.first, tap.second)
        val placement: HitResult? = hits.firstOrNull { hit ->
            val trackable = hit.trackable
            trackable is Plane &&
                trackable.trackingState == TrackingState.TRACKING &&
                trackable.isPoseInPolygon(hit.hitPose)
        }
        if (placement == null) return
        val obj = ArObject(
            id = UUID.randomUUID().toString(),
            assetId = ASSET_LAKE_SPIRIT,
            anchorType = ArAnchorType.LOCAL_PLANE,
            worldCoordinate = locationProvider.latest?.coordinate,
        )
        val anchor = placement.createAnchor()
        anchors.add(obj to anchor)
    }

    private fun updateProjections(frame: Frame) {
        if (anchors.isEmpty()) {
            if (_placedObjects.value.isNotEmpty()) _placedObjects.value = emptyList()
            return
        }
        val camera = frame.camera
        val view = FloatArray(16)
        val proj = FloatArray(16)
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(proj, 0, DEPTH_NEAR, DEPTH_FAR)
        val mvp = FloatArray(16)
        android.opengl.Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        val tracked = camera.trackingState == TrackingState.TRACKING
        val projected = anchors.map { (obj, anchor) ->
            val t = anchor.pose.translation
            val clip = FloatArray(4)
            clip[0] = mvp[0] * t[0] + mvp[4] * t[1] + mvp[8] * t[2] + mvp[12]
            clip[1] = mvp[1] * t[0] + mvp[5] * t[1] + mvp[9] * t[2] + mvp[13]
            clip[3] = mvp[3] * t[0] + mvp[7] * t[1] + mvp[11] * t[2] + mvp[15]
            val visible = tracked && clip[3] > 0f
            val nx = if (visible) (clip[0] / clip[3] + 1f) / 2f else 0f
            val ny = if (visible) (1f - clip[1] / clip[3]) / 2f else 0f
            PlacedArObject(obj, nx, ny, visible)
        }
        // 节流：位置变化小于阈值不发布，避免 60fps 重组
        val old = _placedObjects.value
        val changed = old.size != projected.size ||
            projected.indices.any { i ->
                val a = old[i]
                val b = projected[i]
                a.visible != b.visible ||
                    kotlin.math.abs(a.normalizedX - b.normalizedX) > EPS ||
                    kotlin.math.abs(a.normalizedY - b.normalizedY) > EPS
            }
        if (changed) _placedObjects.value = projected
    }

    private fun drawBackground(frame: Frame) {
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            texCoords,
        )
        texCoords.position(0)

        GLES20.glUseProgram(shaderProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, backgroundTexture)
        GLES20.glUniform1i(textureUniform, 0)

        quadCoords.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        texCoords.position(0)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
    }

    private fun buildProgram(): Int {
        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            return shader
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(program)
        return program
    }

    private fun directBuffer(array: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(array.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(array); position(0) }

    companion object {
        private const val TAG = "ArCoreController"
        const val ASSET_LAKE_SPIRIT = "ar.spirit.lake"
        private const val DEPTH_NEAR = 0.1f
        private const val DEPTH_FAR = 100f
        private const val EPS = 0.01f

        // 全屏矩形（NDC）与初始纹理坐标（后续每帧由 transformCoordinates2d 覆写）
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f,
        )
        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f,
        )

        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoordinate;
            varying vec2 v_TexCoordinate;
            void main() {
                v_TexCoordinate = a_TexCoordinate;
                gl_Position = a_Position;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoordinate;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoordinate);
            }
        """
    }
}
