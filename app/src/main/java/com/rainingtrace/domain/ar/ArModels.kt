package com.rainingtrace.domain.ar

import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.flow.StateFlow

/**
 * RT-AR-001~004: AR 领域模型（AR-0 本地平面放置，ADR-005）。
 *
 * Domain 只处理 ArObject 与状态机；ARCore 类型不允许越过 platform 层。
 * 状态机（06_地图专项 §11）：任何状态都不能把用户卡死在黑屏相机页面。
 */
enum class ArSessionState {
    SUPPORTED,
    INITIALIZING,
    READY,
    TRACKING_LOST,
    UNSUPPORTED,
    PERMISSION_DENIED,
    ERROR,
}

enum class ArAnchorType {
    LOCAL_PLANE,
}

data class ArObject(
    val id: String,
    val assetId: String,
    val anchorType: ArAnchorType,
    /** AR-0 仅记录放置时的现实坐标（若有），不做世界锚定。 */
    val worldCoordinate: WorldCoordinate? = null,
)

/** 已放置对象 + 当前屏幕投影位置（0..1 归一化，左上原点）。 */
data class PlacedArObject(
    val obj: ArObject,
    val normalizedX: Float,
    val normalizedY: Float,
    val visible: Boolean,
)

interface ArController {
    val state: StateFlow<ArSessionState>

    val placedObjects: StateFlow<List<PlacedArObject>>

    /** 是否曾经进入过跟踪（UI 提示"移动手机扫描地面"）。 */
    val hasTracked: Boolean

    /** 屏幕旋转角度（0/90/180/270），session 几何需要。 */
    fun setDisplayRotation(degrees: Int)

    fun onResume()

    fun onPause()

    /** 用户点击预览画面（view 坐标 px），命中平面则放置对象。 */
    fun onUserTap(xPx: Float, yPx: Float)
}
