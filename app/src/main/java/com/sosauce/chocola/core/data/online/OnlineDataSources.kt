package com.sosauce.chocola.core.data.online

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Call

/**
 * Data source stack used by the player:
 *
 * ResolvingDataSource (bymusic-online: → real URL + headers; other URIs untouched)
 *   └ DefaultDataSource (file/content/asset/… handled exactly like before)
 *       └ remote (http/https only): LazyDataSource → CacheDataSource → OkHttpDataSource
 *
 * This is equivalent to ExoPlayer's default for local URIs; only http(s) traffic gains
 * the cache, and the network stack is created only when an http(s) URI is opened.
 */
@OptIn(UnstableApi::class)
object OnlineDataSources {

    fun create(
        context: Context,
        resolver: ResolvingDataSource.Resolver,
        cache: OnlinePlaybackCache,
        callFactory: () -> Call.Factory
    ): DataSource.Factory {
        val remote = DataSource.Factory {
            LazyDataSource {
                CacheDataSource.Factory()
                    .setCache(cache.get())
                    .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(callFactory()))
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
            }
        }
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(context, remote), resolver)
    }
}
