package com.sosauce.chocola.core.domain.online

import com.sosauce.chocola.core.domain.model.CuteTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayableTrackLookupTest {

    private val local1 = CuteTrack(
        mediaId = "101",
        uriString = "content://media/external/audio/media/101",
        title = "Local One",
        artist = "Artist",
        path = "/storage/emulated/0/Music/one.mp3",
        durationMillis = 200_000
    )
    private val local2 = CuteTrack(
        mediaId = "102",
        uriString = "content://media/external/audio/media/102",
        title = "Local Two",
        path = "/storage/emulated/0/Music/two.flac"
    )
    private val library = listOf(local1, local2)

    @Test
    fun localTrackIsReturnedUnchangedAndSnapshotIsNotEvaluated() {
        var snapshotCalls = 0
        val found = PlayableTrackLookup.find("102", library) {
            snapshotCalls++
            MediaItemSnapshot(mediaId = "102", title = "should not be used")
        }
        assertSame(local2, found)
        assertEquals(0, snapshotCalls)
    }

    @Test
    fun localIdMissingFromLibraryStillReturnsNullLikeBefore() {
        var snapshotCalls = 0
        val found = PlayableTrackLookup.find("999", library) {
            snapshotCalls++
            MediaItemSnapshot(mediaId = "999", title = "Deleted song")
        }
        assertNull(found)
        assertEquals(0, snapshotCalls)
    }

    @Test
    fun localTracksAreNotOnline() {
        assertFalse(local1.isOnline)
        assertFalse(CuteTrack().isOnline)
        assertEquals("content://media/external/audio/media/101", local1.uriText)
    }

    @Test
    fun onlineTrackFallsBackToMediaItemMetadata() {
        val id = OnlineMediaId("示例插件", "abc:1")
        val snapshot = MediaItemSnapshot(
            mediaId = id.encode(),
            title = "示例曲",
            artist = "示例歌手",
            album = "示例专辑",
            artworkUri = "https://example.invalid/cover.jpg",
            durationMillis = 232_000
        )
        val track = PlayableTrackLookup.find(id.encode(), library) { snapshot }!!
        assertTrue(track.isOnline)
        assertEquals(id.encode(), track.mediaId)
        assertEquals("示例曲", track.title)
        assertEquals("示例歌手", track.artist)
        assertEquals("示例专辑", track.album)
        assertEquals("https://example.invalid/cover.jpg", track.artUri)
        assertEquals(232_000L, track.durationMillis)
        assertEquals(OnlinePlaybackUri.of(id), track.uriText)
        assertEquals(id, OnlinePlaybackUri.parse(track.uriText))
        // Fields that local-only code keys on stay at "no local file" defaults.
        assertEquals("", track.path)
        assertEquals(-1L, track.albumId)
        assertEquals(-1L, track.artistId)
        assertFalse(track.isSaf)
        assertNull(track.mediaId.toLongOrNull())
    }

    @Test
    fun onlineTrackInLibraryWinsOverSnapshot() {
        val id = OnlineMediaId("demo", "1")
        val stored = OnlineTracks.create(id, title = "Stored")
        val found = PlayableTrackLookup.find(id.encode(), library + stored) {
            MediaItemSnapshot(mediaId = id.encode(), title = "Snapshot")
        }
        assertSame(stored, found)
    }

    @Test
    fun mismatchedOrMissingSnapshotGivesNull() {
        val id = OnlineMediaId("demo", "1").encode()
        assertNull(PlayableTrackLookup.find(id, library) { null })
        assertNull(PlayableTrackLookup.find(id, library) { MediaItemSnapshot(mediaId = "online:demo:2") })
    }

    @Test
    fun unsafeArtworkUrlsAreDropped() {
        val id = OnlineMediaId("demo", "1")
        assertNull(OnlineTracks.create(id, "t", artworkUrl = "file:///sdcard/secret.jpg").artUri)
        assertNull(OnlineTracks.create(id, "t", artworkUrl = "content://media/external/images/1").artUri)
        assertEquals(0L, OnlineTracks.create(id, "t", durationMillis = -5).durationMillis)
    }

    @Test
    fun cuteTrackSerializationIsUnchangedForLocalTracks() {
        // Saved "last music state" must still decode: no new stored fields were added.
        val json = kotlinx.serialization.json.Json.encodeToString(CuteTrack.serializer(), local1)
        assertFalse(json.contains("isOnline"))
        assertFalse(json.contains("uriText"))
        assertEquals(local1, kotlinx.serialization.json.Json.decodeFromString(CuteTrack.serializer(), json))
    }
}
