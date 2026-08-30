package com.gravijet.daydrop

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.gravijet.daydrop.data.local.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps a generous image cache so yesterday's cards - and the ones you saved -
 * still show their photos with no connection.
 */
class DayDropApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Parse the bundled JSON while the window is still being put together,
        // so the feed screen finds it ready and renders on its first frame
        // instead of waiting on the file system.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ContentRepository.get(this@DayDropApp).warmUp() }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(48L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(220)
        .build()
}
