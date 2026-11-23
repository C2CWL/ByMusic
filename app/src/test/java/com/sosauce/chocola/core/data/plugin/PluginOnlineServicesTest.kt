package com.sosauce.chocola.core.data.plugin

import com.sosauce.chocola.core.data.plugin.PluginTestSupport.fictionalPlugin
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.OnlineResolveException
import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.QualityTier
import com.sosauce.chocola.core.domain.online.ResolveRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PluginOnlineServicesTest {

    private lateinit var dir: File
    private val logs = PluginTestSupport.LogSink()
    private lateinit var manager: PluginManager
    private lateinit var service: PluginMusicService

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("bymusic-online").toFile()
        manager = PluginManager(
            store = PluginStore(dir),
            host = PluginTestSupport.host(logs = logs),
            fetcher = PluginSourceFetcher { error("no network in tests") }
        )
        service = PluginMusicService(manager, OnlineMusicItemStore(File(dir, "online_items.json")))
    }

    @After
    fun tearDown() {
        runBlocking { manager.closeAll() }
        dir.deleteRecursively()
    }

    private suspend fun install(code: String) {
        manager.install(manager.inspect(code, PluginSourceKind.LOCAL_FILE))
    }

    @Test
    fun searchMapsItemsToOnlineTracks() = runBlocking {
        install(fictionalPlugin)
        val page = service.searchMusic("虚构音源", "周", 1)
        assertEquals(false, page.isEnd)
        assertEquals(2, page.tracks.size)
        val first = page.tracks[0]
        assertEquals(OnlineMediaId("虚构音源", "1").encode(), first.mediaId)
        assertEquals("晴&天", first.title)
        assertEquals("甲", first.artist)
        assertEquals("乙", first.album)
        assertEquals(200_000L, first.durationMillis)
        assertEquals("https://img.example.com/1.jpg", first.artUri)
        assertTrue(first.isOnline)
        assertEquals(185_000L, page.tracks[1].durationMillis)
    }

    @Test
    fun mediaSourceStartsAtTopAndStepsDownOneTierAtATime() = runBlocking {
        install(fictionalPlugin)
        service.searchMusic("虚构音源", "周", 1)
        val resolver = PluginOnlineMediaResolver(service)
        val media = resolver.resolve(
            ResolveRequest(OnlineMediaId("虚构音源", "1"), QualityRequest.autoFromTop())
        )
        assertEquals("https://cdn.example.com/1/standard.mp3", media.url)
        assertEquals(QualityTier.LOSSY_320, media.servedTier)
        assertEquals("FictionalUA/1.0", media.userAgent)
        assertEquals("https://www.example.com/", media.headers["Referer"])
        assertEquals(
            listOf(QualityTier.HIRES, QualityTier.LOSSLESS, QualityTier.LOSSY_320),
            media.attempts.map { it.tier }
        )
        assertTrue(media.attempts[0].error!!.contains("super"))
        assertNull(media.attempts.last().error)
    }

    @Test
    fun sameMusicFreeKeyIsAskedOnlyOnce() = runBlocking {
        install(
            """
            module.exports = {
              platform: '总失败',
              async getMediaSource(item, quality) {
                console.log('ask ' + quality);
                throw new Error('不行');
              }
            };
            """.trimIndent()
        )
        val resolver = PluginOnlineMediaResolver(service)
        try {
            resolver.resolve(ResolveRequest(OnlineMediaId("总失败", "x"), QualityRequest.autoFromTop()))
            fail("expected failure")
        } catch (e: OnlineResolveException) {
            assertTrue(e.message, e.message!!.contains("不行"))
        }
        assertEquals(listOf("ask super", "ask high", "ask standard", "ask low"), logs.lines.filter { it.startsWith("ask") })
    }

    @Test
    fun withoutGetMediaSourceTheItemUrlIsUsed() = runBlocking {
        install(
            """
            module.exports = {
              platform: '直链',
              async search() { return { isEnd: true, data: [{ id: 'a', title: 'T', url: 'https://cdn.example.com/a.mp3' }] }; }
            };
            """.trimIndent()
        )
        service.searchMusic("直链", "q", 1)
        val media = PluginOnlineMediaResolver(service).resolve(
            ResolveRequest(OnlineMediaId("直链", "a"), QualityRequest.autoFromTop())
        )
        assertEquals("https://cdn.example.com/a.mp3", media.url)
    }

    @Test
    fun withoutGetMediaSourceQualityUrlTakesPrecedenceOverItemUrl() = runBlocking {
        install(
            """
            module.exports = {
              platform: 'quality-direct',
              search() { return { data: [{ id: 'a', title: 'T', url: 'https://cdn.example.com/a.mp3',
                  qualities: { high: { url: 'https://cdn.example.com/a.flac' },
                               low: { url: 'http://cdn.example.com/insecure.mp3' } } }] }; }
            };
            """.trimIndent()
        )
        service.searchMusic("quality-direct", "q", 1)
        val id = OnlineMediaId("quality-direct", "a")
        assertEquals("https://cdn.example.com/a.flac", service.mediaSource(id, "high", 8_000)?.url)
        assertEquals("https://cdn.example.com/a.mp3", service.mediaSource(id, "low", 8_000)?.url)
    }

    @Test
    fun nullOrUndefinedMediaSourceFallsBackToTheRequestedQualityUrl() = runBlocking {
        install(
            """
            module.exports = {
              platform: 'quality-fallback',
              search() { return { data: [{ id: 'a', title: 'T', url: 'https://cdn.example.com/generic.mp3',
                  qualities: { high: { url: 'https://cdn.example.com/a.flac' },
                               low: { url: 'https://cdn.example.com/a.mp3' } } }] }; },
              getMediaSource(item, quality) {
                if (quality === 'super') return { url: 'http://cdn.example.com/a.flac' };
                if (quality === 'high') return null;
              }
            };
            """.trimIndent()
        )
        service.searchMusic("quality-fallback", "q", 1)
        val id = OnlineMediaId("quality-fallback", "a")
        // An omitted result falls back to that quality, then to the item's own url.
        assertEquals("https://cdn.example.com/a.mp3", service.mediaSource(id, "low", 8_000)?.url)
        assertEquals("https://cdn.example.com/generic.mp3", service.mediaSource(id, "standard", 8_000)?.url)
        val media = PluginOnlineMediaResolver(service).resolve(ResolveRequest(id, QualityRequest.autoFromTop()))
        assertEquals("https://cdn.example.com/a.flac", media.url)
        assertTrue(media.attempts.first().error.orEmpty().contains("HTTPS"))
    }

    @Test
    fun stringSourceKeepsEmbeddedCredentialsAsBasicAuthorization() = runBlocking {
        install(
            """
            module.exports = {
              platform: 'auth-source',
              search() { return { data: [{ id: 'a', title: 'T' }] }; },
              getMediaSource(item, quality) {
                return quality === 'super'
                    ? 'https://user:pass@cdn.example.com/a.mp3?sig=1'
                    : 'https://cdn.example.com/a.mp3?sig=1';
              }
            };
            """.trimIndent()
        )
        service.searchMusic("auth-source", "q", 1)
        val id = OnlineMediaId("auth-source", "a")
        val media = PluginOnlineMediaResolver(service).resolve(ResolveRequest(id, QualityRequest.autoFromTop()))
        assertEquals("https://cdn.example.com/a.mp3?sig=1", media.url)
        assertEquals("Basic " + java.util.Base64.getEncoder().encodeToString("user:pass".toByteArray()), media.headers["Authorization"])
        assertTrue(media.attempts.isEmpty())
    }

    @Test
    fun nonHttpUrlsAreRefused() = runBlocking {
        install(
            """
            module.exports = {
              platform: '坏链接',
              async getMediaSource() { return { url: 'file:///data/data/x' }; }
            };
            """.trimIndent()
        )
        try {
            PluginOnlineMediaResolver(service).resolve(
                ResolveRequest(OnlineMediaId("坏链接", "x"), QualityRequest.autoFromTop())
            )
            fail("file: must be refused")
        } catch (e: OnlineResolveException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }

    @Test
    fun disabledOrMissingPluginFailsFast() = runBlocking {
        install(fictionalPlugin)
        manager.setEnabled("虚构音源", false)
        val resolver = PluginOnlineMediaResolver(service)
        for (id in listOf("虚构音源", "不存在")) {
            try {
                resolver.resolve(ResolveRequest(OnlineMediaId(id, "1"), QualityRequest.autoFromTop()))
                fail("expected failure for $id")
            } catch (e: OnlineResolveException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }

    @Test
    fun lyricsComeFromGetLyric() = runBlocking {
        install(fictionalPlugin)
        service.searchMusic("虚构音源", "周", 1)
        val lyrics = service.lyrics(OnlineMediaId("虚构音源", "1"))!!
        assertEquals("[00:01.00]晴&天", lyrics.raw)
        assertEquals("[00:01.00]译文", lyrics.translation)
    }

    @Test
    fun searchIsHiddenForPluginsWithoutMusicSearch() = runBlocking {
        install("module.exports = { platform: '只搜专辑', supportedSearchType: ['album'], async search() { return {}; } };")
        val entry = manager.plugins.value.single()
        assertTrue(!entry.manifest.canSearchMusic)
        try {
            service.searchMusic("只搜专辑", "q", 1)
            fail("expected refusal")
        } catch (e: PluginException) {
            assertEquals(PluginErrorType.METHOD_MISSING, e.type)
        }
    }
}
