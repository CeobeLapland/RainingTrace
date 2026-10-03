package com.rainingtrace.core.art

import android.graphics.Bitmap
import androidx.compose.runtime.compositionLocalOf

/**
 * 美术图的读取口。
 *
 * **读不到返回 null，绝不抛**：图标是锦上添花，缺图必须回退到占位，而不是让某一块界面崩掉。
 * 路径由 `ArtPaths` 的约定给出；实现放 platform（读 assets 是 Android 专有）。
 */
interface ArtSource {
    /** [targetPx] 是期望的边长上限，实现据此降采样；读不到返回 null。 */
    fun bitmap(path: String, targetPx: Int): Bitmap?
}

/**
 * 让深层 composable（如地图详情卡里的 `PlaceThumb`）不必层层传参就能取图。
 *
 * 默认 **null** → 任何没 provide 的场景（@Preview、将来的 UI 测试）自动走回退分支。
 */
val LocalArtSource = compositionLocalOf<ArtSource?> { null }