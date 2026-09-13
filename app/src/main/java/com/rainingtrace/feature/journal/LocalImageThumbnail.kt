package com.rainingtrace.feature.journal

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地照片缩略图：直接解码 app 私有目录文件，不引入图片库。
 *
 * inSampleSize 按目标边长降采样，避免大图 OOM；
 * 解码失败（文件被清理等）静默不显示。
 */
@Composable
fun LocalImageThumbnail(
    localUri: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, localUri) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeThumbnail(localUri, TARGET_PX) }.getOrNull()?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "memory photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .width(THUMB_DP.dp)
                .height(THUMB_DP.dp),
        )
    }
}

private const val TARGET_PX = 256
private const val THUMB_DP = 72

private fun decodeThumbnail(localUri: String, targetPx: Int): android.graphics.Bitmap? {
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
