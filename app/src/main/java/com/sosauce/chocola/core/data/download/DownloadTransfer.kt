package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.data.plugin.PluginHttpPolicy
import com.sosauce.chocola.core.data.plugin.PluginMediaSource
import com.sosauce.chocola.core.data.plugin.PluginNetwork
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DownloadTransfer(base: OkHttpClient) {
    private val client = base.newBuilder().callTimeout(5, TimeUnit.MINUTES).build()

    suspend fun fetch(source: PluginMediaSource, target: File): Boolean {
        val url = PluginNetwork.parseUrl(source.url) ?: return false
        val headers = PluginHttpPolicy.sanitizeHeaders(source.headers)
        val request = Request.Builder().url(url).headers(headers)
        if (headers["User-Agent"] == null) {
            source.userAgent?.let {
                PluginHttpPolicy.sanitizeHeaders(mapOf("User-Agent" to it))["User-Agent"]
                    ?.let { value -> request.header("User-Agent", value) }
            }
        }
        return transfer(request.removeHeader("Range").build(), target) == ResponseResult.Complete
    }

    private suspend fun transfer(request: Request, target: File): ResponseResult =
        // Open before enqueuing so a canceled callback cannot recreate a deleted temp file.
        target.outputStream().use { output ->
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(ResponseResult.Rejected)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use { read(it, output) { call.isCanceled() } }
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                            return
                        }
                        continuation.resume(result)
                    }
                })
            }
        }

    private fun read(response: Response, output: OutputStream, canceled: () -> Boolean): ResponseResult {
        val rangeSize = when (response.code) {
            200 -> null
            206 -> {
                // Some CDNs return 206 even without Range. Accept only the entire object.
                val range = FULL_RANGE.matchEntire(response.header("Content-Range").orEmpty())
                    ?: return ResponseResult.Rejected
                val total = range.groupValues[2].toLongOrNull() ?: return ResponseResult.Rejected
                if (total !in 1L..MAX_BYTES || range.groupValues[1].toLongOrNull() != total - 1) {
                    return ResponseResult.Rejected
                }
                total
            }
            else -> return ResponseResult.Rejected
        }
        val body = response.body ?: return ResponseResult.Rejected
        val size = body.contentLength()
        if (size == 0L || size > MAX_BYTES || (rangeSize != null && size >= 0 && size != rangeSize)) return ResponseResult.Rejected
        var total = 0L
        val input = body.byteStream()
        val buffer = ByteArray(64 * 1024)
        while (!canceled()) {
            val count = try {
                input.read(buffer)
            } catch (_: IOException) {
                return ResponseResult.Rejected
            }
            if (count == -1) break
            total += count
            if (total > MAX_BYTES) return ResponseResult.Rejected
            output.write(buffer, 0, count)
        }
        return if (!canceled() && total > 0 && (size < 0 || total == size) && (rangeSize == null || total == rangeSize)) ResponseResult.Complete
        else ResponseResult.Rejected
    }

    private sealed interface ResponseResult {
        data object Complete : ResponseResult
        data object Rejected : ResponseResult
    }

    private companion object {
        val FULL_RANGE = Regex("bytes 0-([0-9]+)/([0-9]+)", RegexOption.IGNORE_CASE)
        const val MAX_BYTES = 256L * 1024 * 1024
    }
}
