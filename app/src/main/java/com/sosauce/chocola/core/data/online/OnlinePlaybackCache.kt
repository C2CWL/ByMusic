package com.sosauce.chocola.core.data.online

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Disk cache for online playback ("边听边缓存"). Lives in the app cache dir, so the
 * system may clear it. Created lazily on the first online load; local-only users never
 * create it. Owned by PlaybackService and released in its onDestroy.
 */
@OptIn(UnstableApi::class)
class OnlinePlaybackCache(
    private val context: Context,
    private val maxBytes: Long = DEFAULT_MAX_BYTES
) {
    private var cache: SimpleCache? = null

    @Synchronized
    fun get(): Cache = cache ?: SimpleCache(
        File(context.cacheDir, DIR_NAME),
        LeastRecentlyUsedCacheEvictor(maxBytes),
        StandaloneDatabaseProvider(context)
    ).also { cache = it }

    @Synchronized
    fun release() {
        cache?.release()
        cache = null
    }

    companion object {
        const val DIR_NAME = "online_media"
        const val DEFAULT_MAX_BYTES = 512L * 1024 * 1024
    }
}
