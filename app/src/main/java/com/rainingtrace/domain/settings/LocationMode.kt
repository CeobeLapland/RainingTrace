package com.rainingtrace.domain.settings

/**
 * 定位模式：真实 GPS / Fake（点击地图移动，调试用）。
 * 与 LocationSource（单个定位点的来源标记）不同，这是用户选择的**提供方**。
 */
enum class LocationMode {
    FAKE,
    GPS,
    ;

    companion object {
        val DEFAULT = FAKE
    }
}
