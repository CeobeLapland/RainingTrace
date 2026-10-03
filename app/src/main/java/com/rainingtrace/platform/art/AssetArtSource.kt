package com.rainingtrace.platform.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.rainingtrace.core.art.ArtSource

/**
 * 从 `assets/art/` 读美术图（命名约定见 `ArtPaths`）。读不到返回 null，回退由调用方决定。
 *
 * 解码口径与 `core/ui/LocalImage` 一致：先测尺寸、按 `inSampleSize` 降采样，
 * 避免大图 OOM。
 *
 * **密度必须跟屏幕走**（下面 [decode] 里有说明）：这是"美术图与程序画的占位看起来一样大"
 * 的唯一条件，也是真机上最容易踩的坑。
 */
class AssetArtSource(private val context: Context) : ArtSource {

    /**
     * 缓存按**字节数**计上限；**永不 recycle**——
     * 地图的 native style 会长期持有位图引用，回收就是 use-after-free。
     */
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    override fun bitmap(path: String, targetPx: Int): Bitmap? {
        val key = "$path@$targetPx"
        cache.get(key)?.let { return it }
        val decoded = runCatching { decode(path, targetPx) }.getOrNull() ?: return null
        cache.put(key, decoded)
        return decoded
    }

    /**
     * assets 的流**不能读两遍**（第二遍拿到的是空流），所以先整体读进内存再解两遍：
     * 第一遍只测尺寸，第二遍按 inSampleSize 真解。
     */
    private fun decode(path: String, targetPx: Int): Bitmap? {
        val bytes = context.assets.open(path).use { it.readBytes() }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx &&
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            // 别让 BitmapFactory 自己按密度缩放：我们要图上画的像素数，缩放自己下面算。
            inScaled = false
            // MapLibre 需要能 PinBuffer，所以必须 ARGB_8888（别用 HARDWARE / RGB_565）。
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        // 密度决定 MapLibre 的 pixelRatio（= density/160），也就决定了针在屏幕上的大小。
        // 程序画的占位针来自 `Bitmap.createBitmap`，它的密度**就是当前屏幕密度**，
        // 所以美术图必须用同一口径；否则同一张 72×94 的图会按像素原尺寸渲染，
        // 看起来比回退的针大 3 倍（真机上实测过，不是理论值）。
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?.also { it.density = context.resources.displayMetrics.densityDpi }
    }

    private companion object {
        /** 约 12MB：够放几十张 72×94 的针与方形图标。 */
        const val CACHE_BYTES = 12 * 1024 * 1024
    }
}