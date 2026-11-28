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

/**
 * Direct HTTP transfer, following MusicFree's behavior: trust the URL the plugin gave us
 * and save whatever the server returns. We no longer require a 200 status, a full 206
 * Content-Range, an exact Content-Length, or a small file. Only a hard ceiling remains so
 * a broken CDN cannot fill the phone; cancellation and header sanitization stay intact.
 */
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
        return transfer(request.removeHeader("Range").build(), target)
    }

    private suspend fun transfer(request: Request, target: File): Boolean =
        // Open before enqueuing so a canceled callback cannot recreate a deleted temp file.
        target.outputStream().use { output ->
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(false)
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

    private fun read(response: Response, output: OutputStream, canceled: () -> Boolean): Boolean {
        if (response.code !in 200..299) return false
        val body = response.body ?: return false
        val input = body.byteStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (!canceled()) {
            val count = try {
                input.read(buffer)
            } catch (_: IOException) {
                return false
            }
            if (count == -1) break
            total += count
            if (total > MAX_BYTES) return false
            output.write(buffer, 0, count)
        }
        return !canceled() && total > 0
    }

    private companion object {
        /** Safety ceiling only; unlike the old code this is not an audio-format check. */
        const val MAX_BYTES = 1024L * 1024 * 1024
    }
}
