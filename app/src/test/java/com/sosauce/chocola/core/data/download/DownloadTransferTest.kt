package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.data.plugin.PluginMediaSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadTransferTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun stripsPluginHeadersOnCrossOriginRedirect() = runBlocking {
        val requests = mutableListOf<Request>()
        val transfer = transfer { request ->
            requests += request
            when (request.url.host) {
                "source.example" -> response(request, 302, location = "https://cdn.example/song")
                else -> response(request, 200, body = "audio bytes")
            }
        }
        val file = files.newFile()
        assertTrue(transfer.fetch(source(), file))
        assertEquals("audio bytes", file.readText())
        assertEquals(2, requests.size)
        assertEquals("test", requests.first().header("Authorization"))
        assertNull(requests.last().header("Authorization"))
        assertNull(requests.last().header("User-Agent"))
    }

    @Test fun rejectsLocalRedirectBeforeConnecting() = runBlocking {
        val hosts = mutableListOf<String>()
        val transfer = transfer { request ->
            hosts += request.url.host
            response(request, 302, location = "https://localhost/song")
        }
        assertFalse(transfer.fetch(source(), files.newFile()))
        assertEquals(listOf("source.example"), hosts)
    }

    @Test fun rejectsPartialResponse() = runBlocking {
        val requests = mutableListOf<Request>()
        val transfer = transfer { request ->
            requests += request
            response(request, 206, body = "partial")
        }
        assertFalse(transfer.fetch(source(), files.newFile()))
        assertNull(requests.single().header("Range"))
    }

    private fun source() = PluginMediaSource(
        "https://source.example/song", mapOf("Authorization" to "test", "Range" to "bytes=0-"), "plugin", null
    )

    private fun transfer(reply: (Request) -> Response) = DownloadTransfer(
        OkHttpClient.Builder().addInterceptor { reply(it.request()) }.build()
    )

    private fun response(request: Request, code: Int, body: String = "", location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .apply { if (location != null) header("Location", location) }
            .body(body.toResponseBody()).build()
}
