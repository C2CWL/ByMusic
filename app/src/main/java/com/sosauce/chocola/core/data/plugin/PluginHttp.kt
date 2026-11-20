package com.sosauce.chocola.core.data.plugin

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A request coming from a plugin's axios call, as JSON produced by prelude.js. */
@Serializable
data class PluginHttpRequest(
    val method: String = "GET",
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyBase64: String? = null,
    val timeoutMs: Long = 0,
    val followRedirects: Boolean = true,
    /** true for axios responseType 'arraybuffer'/'blob': the body is returned as base64. */
    val binary: Boolean = false
)

@Serializable
data class PluginHttpResponse(
    val status: Int = 0,
    val statusText: String = "",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyBase64: String? = null,
    /** Final URL after redirects. */
    val url: String? = null,
    /** Set instead of a status when the request could not be made. */
    val error: String? = null,
    /** axios-style error code, e.g. ECONNABORTED for timeouts. */
    val code: String? = null
) {
    companion object {
        fun failure(message: String, code: String = "ERR_NETWORK") =
            PluginHttpResponse(error = message, code = code)
    }
}

/** What plugins may do over the network. Checked before any request is made. */
object PluginHttpPolicy {
    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val MAX_TIMEOUT_MS = 20_000L
    const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    const val MAX_REQUEST_BODY_BYTES = 4L * 1024 * 1024
    private val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    fun timeoutOf(request: PluginHttpRequest): Long =
        if (request.timeoutMs <= 0) DEFAULT_TIMEOUT_MS else request.timeoutMs.coerceIn(1, MAX_TIMEOUT_MS)

    /** Returns an error message, or null when the request is allowed. */
    fun check(request: PluginHttpRequest): String? {
        if (request.method.uppercase() !in METHODS) return "不支持的请求方法 ${request.method}"
        return if (PluginNetwork.parseUrl(request.url) == null) "只允许访问公网 HTTPS 链接" else null
    }

    fun isBlockedHostName(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        val literal = parseIpLiteral(h) ?: return false
        return isBlockedAddress(literal)
    }

    /** Loopback, link-local, private (RFC 1918 / ULA), carrier NAT, multicast, any-local. */
    fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true
        val b = address.address
        if (address is Inet4Address) {
            val first = b[0].toInt() and 0xFF
            val second = b[1].toInt() and 0xFF
            if (first == 0 || first >= 240 || (first == 198 && second in 18..19)) return true
            if (first == 100 && second in 64..127) return true // 100.64.0.0/10
            if (first == 169 && second == 254) return true
        }
        if (address is Inet6Address) {
            // Only global unicast; exclude transition/special-use ranges that can encode
            // other addresses or have routing semantics outside the public Internet.
            val first = b[0].toInt() and 0xFF
            val second = b[1].toInt() and 0xFF
            if (first !in 0x20..0x3F) return true
            if (first == 0x20 && (second == 2 || (second == 1 && b[2].toInt() in 0..1))) return true
        }
        return false
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        val looksV4 = host.isNotEmpty() && host.all { it.isDigit() || it == '.' } && host.count { it == '.' } == 3
        val looksV6 = host.contains(':') && host.all { it.isLetterOrDigit() || it == ':' || it == '.' }
        if (!looksV4 && !looksV6) return null
        return try {
            InetAddress.getByName(host) // no DNS lookup for literals
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeHeaders(headers: Map<String, String>): Headers {
        val builder = Headers.Builder()
        for ((name, value) in headers) {
            if (name.isBlank() || name.any { it == '\r' || it == '\n' || it == ':' } ||
                value.any { it == '\r' || it == '\n' }
            ) continue
            if (name.equals("Host", true) || name.equals("Content-Length", true) ||
                name.equals("Transfer-Encoding", true) || name.equals("Connection", true) ||
                name.startsWith("Proxy-", true) || name.equals("Upgrade", true)
            ) continue
            try {
                builder.addUnsafeNonAscii(name, value)
            } catch (_: IllegalArgumentException) {
                // invalid header name; skip
            }
        }
        return builder.build()
    }
}

/** Host side of the plugin's network access. */
fun interface PluginHttpClient {
    suspend fun execute(request: PluginHttpRequest): PluginHttpResponse
}

/** Resolves only public addresses, so plugins cannot reach the phone or the local network. */
object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (PluginHttpPolicy.isBlockedHostName(hostname)) {
            throw UnknownHostException("blocked host")
        }
        val all = Dns.SYSTEM.lookup(hostname)
        val allowed = all.filterNot(PluginHttpPolicy::isBlockedAddress)
        if (allowed.isEmpty()) throw UnknownHostException("blocked private address")
        return allowed
    }
}

/** Plugin axios and source fetches share the public HTTPS transport. */
class OkHttpPluginHttpClient(base: OkHttpClient) : PluginHttpClient {
    private val client = base.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(PluginHttpPolicy.MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun execute(request: PluginHttpRequest): PluginHttpResponse {
        PluginHttpPolicy.check(request)?.let { return PluginHttpResponse.failure(it, "ERR_BLOCKED") }
        val method = request.method.uppercase()
        val headers = PluginHttpPolicy.sanitizeHeaders(request.headers)
        val bodyBytes: ByteArray? = when {
            request.bodyBase64 != null -> try {
                Base64.getDecoder().decode(request.bodyBase64)
            } catch (_: IllegalArgumentException) {
                return PluginHttpResponse.failure("请求内容格式不对", "ERR_BAD_REQUEST")
            }
            request.body != null -> request.body.toByteArray(Charsets.UTF_8)
            else -> null
        }
        if (bodyBytes != null && bodyBytes.size > PluginHttpPolicy.MAX_REQUEST_BODY_BYTES) {
            return PluginHttpResponse.failure("请求内容太大", "ERR_BAD_REQUEST")
        }
        val requestBody = when {
            method == "GET" || method == "HEAD" -> null
            bodyBytes != null -> bodyBytes.toRequestBody(headers["Content-Type"]?.toMediaTypeOrNull())
            method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val okRequest = try {
            Request.Builder()
                .url(checkNotNull(PluginNetwork.parseUrl(request.url)))
                .headers(headers)
                .method(method, requestBody)
                .tag(PluginRedirects::class.java, PluginRedirects(request.followRedirects))
                .build()
        } catch (e: IllegalArgumentException) {
            return PluginHttpResponse.failure("请求无效：${e.message}", "ERR_BAD_REQUEST")
        }

        val call = client.newCall(okRequest)
        call.timeout().timeout(PluginHttpPolicy.timeoutOf(request), TimeUnit.MILLISECONDS)
        return try {
            call.awaitBody(request.binary)
        } catch (e: InterruptedIOException) {
            // SocketTimeoutException and OkHttp's call timeout both land here.
            PluginHttpResponse.failure("请求超时", "ECONNABORTED")
        } catch (e: IOException) {
            PluginHttpResponse.failure(e.message ?: e::class.java.simpleName)
        }
    }

    private fun read(response: Response, binary: Boolean): PluginHttpResponse {
        val body = response.body
        val bytes = body?.byteStream()?.use { input ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > PluginHttpPolicy.MAX_RESPONSE_BYTES) {
                    return PluginHttpResponse.failure("响应太大（超过 8MB）", "ERR_BAD_RESPONSE")
                }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: ByteArray(0)
        val headerMap = LinkedHashMap<String, String>()
        for (name in response.headers.names()) {
            headerMap[name.lowercase()] = response.headers.values(name).joinToString(", ")
        }
        val charset = body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        return PluginHttpResponse(
            status = response.code,
            statusText = response.message,
            headers = headerMap,
            body = if (binary) null else String(bytes, charset),
            bodyBase64 = if (binary) Base64.getEncoder().encodeToString(bytes) else null,
            url = response.request.url.toString()
        )
    }

    private suspend fun Call.awaitBody(binary: Boolean): PluginHttpResponse =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    try {
                        cont.resume(response.use { read(it, binary) })
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
}
