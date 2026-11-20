package com.sosauce.chocola.core.data.plugin

import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.net.URISyntaxException

/** Shared by every transport that consumes a plugin-controlled URL. */
object PluginNetwork {
    fun parseUrl(raw: String?): HttpUrl? {
        if (raw == null || raw.length > 8192) return null
        val uri = try {
            URI(raw)
        } catch (_: URISyntaxException) {
            return null
        }
        // OkHttp normalizes some malformed URLs, so check the original authority first.
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host == null || uri.rawUserInfo != null) return null
        return raw.toHttpUrlOrNull()?.takeIf(::isAllowed)
    }

    private fun isAllowed(url: HttpUrl): Boolean = url.isHttps && url.toString().length <= 8192 &&
        url.username.isEmpty() && url.password.isEmpty() && !PluginHttpPolicy.isBlockedHostName(url.host)

    fun client(base: OkHttpClient = OkHttpClient()): OkHttpClient = base.newBuilder()
        .dns(PublicOnlyDns)
        .proxy(Proxy.NO_PROXY)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .apply { interceptors().add(0, Interceptor(::intercept)) }
        .build()

    private val redirectCodes = setOf(301, 302, 303, 307, 308)

    private fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val redirects = original.tag(PluginRedirects::class.java)
        var request = original.newBuilder()
            .headers(PluginHttpPolicy.sanitizeHeaders(original.headers.toMultimap().mapValues { it.value.last() }))
            .build()
        repeat(6) { hop ->
            if (!isAllowed(request.url)) throw IOException("只允许访问公网 HTTPS 链接")
            val response = chain.proceed(request)
            val location = response.header("Location")
            if (redirects?.follow == false || response.code !in redirectCodes || location == null) {
                return response
            }
            response.close()
            if (hop == 5) throw IOException("重定向次数过多")
            val target = request.url.resolve(location) ?: throw IOException("重定向地址无效")
            val sameOrigin = target.host == request.url.host && target.port == request.url.port &&
                target.scheme == request.url.scheme
            val toGet = (response.code == 303 && request.method != "HEAD") ||
                (response.code in 301..302 && request.method == "POST")
            if (!sameOrigin && !toGet && request.body != null) throw IOException("拒绝跨域转发请求正文")
            request = request.newBuilder().url(target).apply {
                if (!sameOrigin) {
                    // Keep only a safe transport header, needed for playback seeks across CDNs.
                    val range = request.header("Range")
                    headers(okhttp3.Headers.Builder().build())
                    range?.let { header("Range", it) }
                }
                if (toGet) {
                    get()
                    removeHeader("Content-Type")
                    removeHeader("Content-Encoding")
                }
            }.build()
        }
        error("unreachable")
    }
}

internal data class PluginRedirects(val follow: Boolean)
