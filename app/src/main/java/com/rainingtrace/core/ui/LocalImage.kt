package com.rainingtrace.core.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地图片（app 私有目录）显示，不引入图片库。
 *
 * inSampleSize 按目标边长降采样，避免大图 OOM；
 * 解码失败（文件被清理等）静默不显示。
 * 尺寸交给调用方的 modifier 决定（缩略图 / 大图回看共用）。
 */
@Composable
fun LocalImage(
    localUri: String,
    modifier: Modifier = Modifier,
    targetPx: Int = DEFAULT_TARGET_PX,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, localUri, targetPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { decode(localUri, targetPx) }.getOrNull()?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private const val DEFAULT_TARGET_PX = 256

private fun decode(localUri: String, targetPx: Int): android.graphics.Bitmap? {
    val path = localUri.removePrefix("file://")
    val file = File(path)
    if (!file.exists()) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, opts)
}
