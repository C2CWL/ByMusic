package com.sosauce.chocola.core.data.online

import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.sosauce.chocola.core.domain.online.OnlineCacheKey
import com.sosauce.chocola.core.domain.online.OnlineMediaResolver
import com.sosauce.chocola.core.domain.online.OnlinePlaybackUri
import com.sosauce.chocola.core.domain.online.OnlineUrlPolicy
import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.ResolveRequest
import com.sosauce.chocola.core.domain.online.ResolvedMedia
import com.sosauce.chocola.core.domain.online.ResolvedMediaMemo
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Replaces `bymusic-online:` placeholder URIs with the real URL right before ExoPlayer
 * loads them (links from plugins usually expire, so they are fetched at play time).
 *
 * Every other URI (content://, file://, local paths) is returned untouched, so local
 * playback does not go through any online code.
 *
 * Runs on ExoPlayer's loading thread, which is allowed to block.
 */
@OptIn(UnstableApi::class)
class OnlineDataSourceResolver(
    private val resolverProvider: () -> OnlineMediaResolver,
    private val qualityProvider: () -> QualityRequest = { QualityRequest.autoFromTop() },
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    private val memo: ResolvedMediaMemo = ResolvedMediaMemo()
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val id = OnlinePlaybackUri.parse(dataSpec.uri.toString()) ?: return dataSpec

        val request = ResolveRequest(id, qualityProvider())
        val memoKey = ResolvedMediaMemo.keyOf(request)
        val resolved = memo.get(memoKey) ?: resolveBlocking(request).also { memo.put(memoKey, it) }

        return dataSpec.buildUpon()
            .setUri(resolved.url.toUri())
            .setKey(OnlineCacheKey.of(id, resolved.servedTier, resolved.url))
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.requestHeaders())
            .build()
    }

    private fun resolveBlocking(request: ResolveRequest): ResolvedMedia {
        val resolved = try {
            runBlocking {
                withTimeout(timeoutMillis) { resolverProvider().resolve(request) }
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Online resolve timed out after ${timeoutMillis}ms", e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Online resolve failed: ${e.message}", e)
        }
        if (!OnlineUrlPolicy.isAllowed(resolved.url)) {
            throw IOException("Online resolver returned a URL that is not http(s)")
        }
        return resolved
    }

    companion object {
        /**
         * Whole resolve, all quality tiers included. Plugins get up to 8 s per MusicFree
         * quality key (4 keys), so this is a little above 4 × 8 s.
         */
        const val DEFAULT_TIMEOUT_MS = 35_000L
    }
}
