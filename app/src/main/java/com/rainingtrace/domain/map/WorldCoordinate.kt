package com.rainingtrace.domain.map

/**
 * RT-DOM-002: WGS84 地理坐标。
 *
 * 业务层用它表达"现实位置"，但 cell identity 不允许直接使用浮点经纬度
 * （见 05_领域模型 §9），必须经 [HexGrid] 投影为 [HexCellId]。
 */
data class WorldCoordinate(
    val latDegrees: Double,
    val lngDegrees: Double,
) {
    init {
        require(latDegrees in -90.0..90.0) { "latitude out of range: $latDegrees" }
        require(lngDegrees in -180.0..180.0) { "longitude out of range: $lngDegrees" }
    }
}
