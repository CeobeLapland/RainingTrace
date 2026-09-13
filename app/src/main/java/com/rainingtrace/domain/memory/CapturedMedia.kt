package com.rainingtrace.domain.memory

import com.rainingtrace.domain.map.WorldCoordinate

/**
 * 相机采集产物（06_地图专项 §12）。
 *
 * platform 层（CameraX）产出它，业务层只认这个结构，
 * 不允许 Memory/Journal 直接依赖 CameraX 类型。
 */
data class CapturedMedia(
    val localUri: String,
    val capturedAtEpochMs: Long,
    val mimeType: String = "image/jpeg",
    val coordinate: WorldCoordinate? = null,
)
