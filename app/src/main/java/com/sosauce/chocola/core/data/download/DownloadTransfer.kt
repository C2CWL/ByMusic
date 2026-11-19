package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.data.plugin.PluginHttpPolicy
import com.sosauce.chocola.core.data.plugin.PluginMediaSource
import com.sosauce.chocola.core.data.plugin.PublicOnlyDns
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DownloadTransfer(base: OkHttpClient) {
    private val client = base.newBuilder()
        .dns(PublicOnlyDns)
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Each redirect is checked before connecting. Plugin headers stay on their original origin. */
    suspend fun fetch(source: PluginMediaSource, target: File): Boolean {
        if (source.url.length > MAX_URL_LENGTH) return false
        val origin = source.url.toHttpUrlOrNull() ?: return false
        val pluginHeaders = PluginHttpPolicy.sanitizeHeaders(source.headers)
        var url = origin
        repeat(MAX_REDIRECTS + 1) {
            if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.toString().length > MAX_URL_LENGTH || PluginHttpPolicy.isBlockedHostName(url.host)
            ) return false

            val request = Request.Builder().url(url)
            if (url.host == origin.host && url.port == origin.port) {
                request.headers(pluginHeaders)
                if (pluginHeaders["User-Agent"] == null) {
                    source.userAgent?.let {
                        PluginHttpPolicy.sanitizeHeaders(mapOf("User-Agent" to it))["User-Agent"]
                            ?.let { value -> request.header("User-Agent", value) }
                    }
                }
            }
            request.removeHeader("Range")
            val location = when (val result = transfer(request.build(), target)) {
                ResponseResult.Complete -> return true
                ResponseResult.Rejected -> return false
                is ResponseResult.Redirect -> result.location
            }
            url = url.resolve(location) ?: return false
        }
        return false
    }

    private suspend fun transfer(request: Request, target: File): ResponseResult =
        // Open before enqueuing so a canceled callback cannot recreate a deleted temp file.
        target.outputStream().use { output ->
            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
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
        if (response.code in REDIRECT_CODES) {
            val location = response.header("Location") ?: return ResponseResult.Rejected
            return ResponseResult.Redirect(location)
        }
        if (response.code != 200) return ResponseResult.Rejected
        val body = response.body ?: return ResponseResult.Rejected
        val size = body.contentLength()
        if (size == 0L || size > MAX_BYTES) return ResponseResult.Rejected
        var total = 0L
        val input = body.byteStream()
        val buffer = ByteArray(64 * 1024)
        while (!canceled()) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            if (total > MAX_BYTES) return ResponseResult.Rejected
            output.write(buffer, 0, count)
        }
        return if (!canceled() && total > 0 && (size < 0 || total == size)) ResponseResult.Complete
        else ResponseResult.Rejected
    }

    private sealed interface ResponseResult {
        data object Complete : ResponseResult
        data object Rejected : ResponseResult
        data class Redirect(val location: String) : ResponseResult
    }

    private companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
        const val MAX_URL_LENGTH = 8192
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
