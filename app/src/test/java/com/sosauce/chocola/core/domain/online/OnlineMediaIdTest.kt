package com.sosauce.chocola.core.domain.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineMediaIdTest {

    @Test
    fun encodesToOnlinePluginIdForm() {
        assertEquals("online:demo:123", OnlineMediaId("demo", "123").encode())
    }

    @Test
    fun roundTripsSpecialCharacters() {
        val cases = listOf(
            OnlineMediaId("demo", "123"),
            OnlineMediaId("示例插件 · 甲", "abc"),
            OnlineMediaId("a:b%c", "x"),
            OnlineMediaId("p", "id:with:colons"),
            OnlineMediaId("p", "id with space/and?query=1&x=%41"),
            OnlineMediaId("emoji🎵", "曲目-1")
        )
        for (id in cases) {
            val encoded = id.encode()
            assertEquals(encoded, id, OnlineMediaId.parse(encoded))
            assertTrue(OnlineMediaId.isOnline(encoded))
        }
    }

    @Test
    fun pluginPartNeverContainsRawColon() {
        val encoded = OnlineMediaId("a:b", "c").encode()
        assertEquals("online:a%3Ab:c", encoded)
    }

    @Test
    fun localMediaStoreIdsAreNeverOnline() {
        val localIds = listOf("0", "1", "42", "1000000123", "-1", "", " ")
        for (id in localIds) {
            assertFalse(id, OnlineMediaId.isOnline(id))
            assertNull(id, OnlineMediaId.parse(id))
        }
        assertFalse(OnlineMediaId.isOnline(null))
    }

    @Test
    fun malformedOnlineIdsAreRejectedWithoutThrowing() {
        val bad = listOf(
            "online:",
            "online:demo",
            "online:demo:",
            "online::123",
            "online:%ZZ:1",
            "online:%4:1",
            "Online:demo:1",
            "content://media/external/audio/media/12",
            "file:///sdcard/Music/a.mp3",
            "/storage/emulated/0/Music/a.mp3"
        )
        for (value in bad) {
            assertNull(value, OnlineMediaId.parse(value))
            assertFalse(value, OnlineMediaId.isOnline(value))
        }
    }

    @Test
    fun playbackUriRoundTripsAndIsOpaque() {
        val id = OnlineMediaId("示例插件:甲", "song/1.m3u8?x=1")
        val uri = OnlinePlaybackUri.of(id)
        assertTrue(uri.startsWith("bymusic-online:"))
        // Opaque: no "//" authority and no '/' path, so ExoPlayer cannot guess HLS/DASH from it.
        assertFalse(uri.contains("/"))
        assertFalse(uri.contains("?"))
        assertEquals(id, OnlinePlaybackUri.parse(uri))
    }

    @Test
    fun localPlaybackUrisAreNotTreatedAsOnline() {
        val local = listOf(
            "content://media/external/audio/media/42",
            "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fa.flac",
            "file:///storage/emulated/0/Music/a.mp3",
            "/storage/emulated/0/Music/a.mp3",
            "https://example.invalid/a.mp3",
            "",
            "bymusic-online:",
            "bymusic-online:not-an-online-id",
            "bymusic-online:%ZZ"
        )
        for (uri in local) assertNull(uri, OnlinePlaybackUri.parse(uri))
        assertNull(OnlinePlaybackUri.parse(null))
    }

    @Test
    fun percentCodecRoundTrip() {
        val text = "a b:c%d/é中🎵~._-"
        val encoded = PercentCodec.encode(text)
        assertNotNull(encoded)
        assertTrue(encoded.all { it.code < 128 })
        assertEquals(text, PercentCodec.decode(encoded))
    }
}
