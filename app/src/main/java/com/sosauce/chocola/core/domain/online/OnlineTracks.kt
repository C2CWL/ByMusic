package com.sosauce.chocola.core.domain.online

import com.sosauce.chocola.core.domain.model.CuteTrack

/**
 * Plain copy of the fields of a Media3 MediaItem that we need to show a track that is
 * not in the local library. Kept free of android/media3 types so it is JVM-testable.
 */
data class MediaItemSnapshot(
    val mediaId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val durationMillis: Long = 0
)

object OnlineTracks {

    fun create(
        id: OnlineMediaId,
        title: String,
        artist: String = "",
        album: String = "",
        artworkUrl: String? = null,
        durationMillis: Long = 0
    ): CuteTrack = CuteTrack(
        mediaId = id.encode(),
        uriString = OnlinePlaybackUri.of(id),
        artUri = artworkUrl?.takeIf(OnlineUrlPolicy::isAllowed),
        title = title,
        artist = artist,
        album = album,
        durationMillis = durationMillis.coerceAtLeast(0)
    )

    fun fromSnapshot(snapshot: MediaItemSnapshot): CuteTrack? {
        val id = OnlineMediaId.parse(snapshot.mediaId) ?: return null
        return create(
            id = id,
            title = snapshot.title,
            artist = snapshot.artist,
            album = snapshot.album,
            artworkUrl = snapshot.artworkUri,
            durationMillis = snapshot.durationMillis
        )
    }
}

/**
 * How the player UI finds the CuteTrack for a mediaId.
 *
 * 1. Local library first, exactly as before (same instance is returned).
 * 2. Only for `online:` ids that are not in the library, fall back to the MediaItem's
 *    own metadata. Local ids that are missing from the library still return null, so
 *    local behaviour is unchanged. [snapshot] is only evaluated in the fallback case.
 */
object PlayableTrackLookup {
    fun find(
        mediaId: String,
        library: List<CuteTrack>,
        snapshot: () -> MediaItemSnapshot?
    ): CuteTrack? {
        library.firstOrNull { it.mediaId == mediaId }?.let { return it }
        if (!OnlineMediaId.isOnline(mediaId)) return null
        val snap = snapshot() ?: return null
        if (snap.mediaId != mediaId) return null
        return OnlineTracks.fromSnapshot(snap)
    }
}
