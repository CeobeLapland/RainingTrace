package com.rainingtrace.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.rainingtrace.core.art.LocalArtSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 美术图标：有图显示图，**没图就画 [fallback]**。
 *
 * 这是"美术丢图即生效、缺图零崩溃"的落点——所有图标位都走它，
 * 于是"解码失败""文件不存在""没 provide ArtSource"三条都收敛成同一条回退路径。
 * 解码照 `LocalImage` 的口径放在 IO 线程（尺寸由调用方的 modifier 决定）。
 */
@Composable
fun ArtIcon(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetPx: Int = DEFAULT_ART_TARGET_PX,
    contentScale: ContentScale = ContentScale.Fit,
    fallback: @Composable () -> Unit,
) {
    val source = LocalArtSource.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx, source) {
        value = if (path == null || source == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { source.bitmap(path, targetPx) }.getOrNull()?.asImageBitmap()
            }
        }
    }

    val loaded = bitmap
    if (loaded == null) {
        fallback()
    } else {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private const val DEFAULT_ART_TARGET_PX = 96