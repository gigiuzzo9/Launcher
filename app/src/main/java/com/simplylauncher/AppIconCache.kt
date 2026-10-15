package com.simplylauncher

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

object AppIconCache {

    private val cache = LruCache<String, ImageBitmap>(200)

    fun get(packageName: String, app: AppInfo): ImageBitmap {
        cache.get(packageName)?.let { return it }
        val bitmap = app.icon.toBitmap(96, 96).asImageBitmap()
        cache.put(packageName, bitmap)
        return bitmap
    }

    fun clear() {
        cache.evictAll()
    }
}
