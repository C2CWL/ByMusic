package com.sosauce.chocola.core.domain.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ResolvedMediaTest {

    @Test
    fun userAgentIsAddedWhenMissing() {
        val headers = ResolvedMedia(
            url = "https://example.invalid/a.flac",
            headers = mapOf("Referer" to "https://example.invalid/"),
            userAgent = "TestAgent/1.0"
        ).requestHeaders()
        assertEquals("https://example.invalid/", headers["Referer"])
        assertEquals("TestAgent/1.0", headers["User-Agent"])
    }

    @Test
    fun pluginUserAgentHeaderWinsOverUserAgentField() {
        val headers = ResolvedMedia(
            url = "https://example.invalid/a.flac",
            headers = mapOf("user-agent" to "FromHeader"),
            userAgent = "FromField"
        ).requestHeaders()
        assertEquals(mapOf("user-agent" to "FromHeader"), headers)
    }

    @Test
    fun headersWithLineBreaksAreDropped() {
        val headers = ResolvedMedia(
            url = "https://example.invalid/a.flac",
            headers = mapOf("X-Ok" to "1", "X-Bad" to "a\r\nInjected: 1", "Bad\nName" to "v", " " to "blank"),
            userAgent = "ua\nx"
        ).requestHeaders()
        assertEquals(mapOf("X-Ok" to "1"), headers)
    }

    @Test
    fun artworkAllowsPublicHttpAndAudioRequiresPublicHttps() {
        assertTrue(OnlineUrlPolicy.isAllowed("https://example.invalid/a.flac?sig=1"))
        assertTrue(OnlineUrlPolicy.isAllowed("http://example.invalid/a.jpg"))
        assertTrue(OnlineUrlPolicy.isAllowed("HTTPS://EXAMPLE.INVALID/a"))
        assertTrue(OnlineUrlPolicy.isAllowedMedia("https://example.invalid/a.flac"))
        assertFalse(OnlineUrlPolicy.isAllowedMedia("http://example.invalid/a.mp3"))
        val rejected = listOf(
            null, "", "   ",
            "https://127.0.0.1/a.mp3",
            "http://127.0.0.1/a.jpg",
            "https://localhost/a.mp3",
            "https://192.168.1.1/a.mp3",
            "http://192.168.1.1/cover.jpg",
            "https://10.0.0.2/a.mp3",
            "https://user:pass@example.invalid/a.mp3",
            "http://user:pass@example.invalid/a.jpg",
            "file:///sdcard/a.mp3", "content://media/external/audio/media/1",
            "javascript:alert(1)", "ftp://example.invalid/a.mp3", "https://", "/local/path.mp3",
            "data:audio/mp3;base64,AAAA", "bymusic-online:online%3Ap%3A1", "https://exa mple.invalid/a"
        )
        for (url in rejected) assertFalse(url.toString(), OnlineUrlPolicy.isAllowed(url))
        assertFalse(OnlineUrlPolicy.isAllowedMedia("https://127.0.0.1/a.mp3"))
        assertFalse(OnlineUrlPolicy.isAllowedMedia("https://user:pass@example.invalid/a.mp3"))
    }

    @Test
    fun cacheKeyIgnoresExpiringQueryButSeparatesTiersAndFiles() {
        val id = OnlineMediaId("demo", "1")
        val a = OnlineCacheKey.of(id, QualityTier.LOSSLESS, "https://cdn.example.invalid/a/1.flac?sig=111&t=1")
        val b = OnlineCacheKey.of(id, QualityTier.LOSSLESS, "https://cdn.example.invalid/a/1.flac?sig=222&t=2")
        val otherTier = OnlineCacheKey.of(id, QualityTier.LOSSY_320, "https://cdn.example.invalid/a/1.flac?sig=111")
        val otherFile = OnlineCacheKey.of(id, QualityTier.LOSSLESS, "https://cdn.example.invalid/a/1-320.mp3?sig=111")
        val unknownTier = OnlineCacheKey.of(id, null, "https://cdn.example.invalid/a/1.flac")
        assertEquals(a, b)
        assertNotEquals(a, otherTier)
        assertNotEquals(a, otherFile)
        assertTrue(a.startsWith("online:demo:1#LOSSLESS#"))
        assertTrue(unknownTier.startsWith("online:demo:1#unknown#"))
    }

    @Test
    fun placeholderResolverNeverReturnsASource() = runBlocking {
        try {
            UnavailableOnlineMediaResolver.resolve(
                ResolveRequest(OnlineMediaId("demo", "1"), QualityRequest.autoFromTop())
            )
            fail("placeholder must not resolve")
        } catch (e: OnlineResolveException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun fakeResolverCanImplementAutoFallbackFromTop() = runBlocking {
        // Shape of the later plugin-backed resolver: ask tiers from the top, take the first that works.
        val fake = object : OnlineMediaResolver {
            override suspend fun resolve(request: ResolveRequest): ResolvedMedia {
                val outcome = request.quality.tryInOrder { tier ->
                    if (tier >= QualityTier.LOSSLESS) null else "https://example.invalid/${request.id.trackId}-$tier.mp3"
                } ?: throw OnlineResolveException("all tiers failed")
                return ResolvedMedia(url = outcome.value, servedTier = outcome.tier, attempts = outcome.attempts)
            }
        }
        val resolved = fake.resolve(ResolveRequest(OnlineMediaId("demo", "7"), QualityRequest.autoFromTop()))
        assertEquals(QualityTier.LOSSY_320, resolved.servedTier)
        assertEquals(3, resolved.attempts.size)
        assertTrue(OnlineUrlPolicy.isAllowed(resolved.url))
    }

    @Test
    fun memoExpiresAndEvicts() {
        var now = 0L
        val memo = ResolvedMediaMemo(ttlMillis = 1000, maxEntries = 2, clock = { now })
        val v1 = ResolvedMedia(url = "https://example.invalid/1")
        val v2 = ResolvedMedia(url = "https://example.invalid/2")
        val v3 = ResolvedMedia(url = "https://example.invalid/3")
        memo.put("a", v1)
        now = 999
        assertSame(v1, memo.get("a"))
        now = 1000
        assertNull(memo.get("a"))

        now = 0
        memo.put("a", v1); memo.put("b", v2)
        memo.get("a") // touch a, so b is the eldest
        memo.put("c", v3)
        assertNull(memo.get("b"))
        assertSame(v1, memo.get("a"))
        assertSame(v3, memo.get("c"))
        memo.invalidate("c")
        assertNull(memo.get("c"))
    }

    @Test
    fun memoKeyDependsOnQualityChain() {
        val id = OnlineMediaId("demo", "1")
        assertNotEquals(
            ResolvedMediaMemo.keyOf(ResolveRequest(id, QualityRequest.autoFromTop())),
            ResolvedMediaMemo.keyOf(ResolveRequest(id, QualityRequest.preferred(QualityTier.LOSSY_128)))
        )
    }
}
