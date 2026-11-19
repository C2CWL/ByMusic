package com.sosauce.chocola.core.data.mapper

import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.sosauce.chocola.core.data.backup.SettingType
import com.sosauce.chocola.core.data.backup.SettingsWithType
import com.sosauce.chocola.core.data.datastore.PreferencesKeys.HIDDEN_FOLDERS
import com.sosauce.chocola.core.data.datastore.PreferencesKeys.LAST_MUSIC_STATE
import com.sosauce.chocola.core.data.datastore.PreferencesKeys.SAF_TRACKS
import com.sosauce.chocola.core.data.datastore.PreferencesKeys.WHITELISTED_FOLDERS
import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.model.Folder
import com.sosauce.chocola.core.domain.online.MediaItemSnapshot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

@androidx.annotation.OptIn(UnstableApi::class)
fun CuteTrack.toMediaItem(): MediaItem {

    val metadata = MediaMetadata.Builder()
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artUri?.toUri())
        .apply {
            // Online tracks have no local file for the UI to read the duration from.
            if (isOnline && durationMillis > 0) setDurationMs(durationMillis)
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

/** Plain copy of the metadata needed to show a track that is not in the local library. */
@androidx.annotation.OptIn(UnstableApi::class)
fun MediaItem.toSnapshot(): MediaItemSnapshot = MediaItemSnapshot(
    mediaId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    album = mediaMetadata.albumTitle?.toString().orEmpty(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
    durationMillis = mediaMetadata.durationMs ?: 0L
)

fun Preferences.toJsonMap(): Map<String, SettingsWithType> {

    val keysToIgnore = setOf(
        SAF_TRACKS,
        HIDDEN_FOLDERS,
        WHITELISTED_FOLDERS,
        LAST_MUSIC_STATE
    )

    return asMap()
        .filterKeys { it !in keysToIgnore }
        .mapNotNull { (key, value) ->
            val entry = when (value) {
                is Boolean -> SettingsWithType(SettingType.BOOLEAN, JsonPrimitive(value))
                is Int -> SettingsWithType(SettingType.INT, JsonPrimitive(value))
                is Long -> SettingsWithType(SettingType.LONG, JsonPrimitive(value))
                is Float -> SettingsWithType(SettingType.FLOAT, JsonPrimitive(value))
                is Double -> SettingsWithType(SettingType.DOUBLE, JsonPrimitive(value))
                is String -> SettingsWithType(SettingType.STRING, JsonPrimitive(value))
                is Set<*> -> SettingsWithType(
                    SettingType.SET_STRING,
                    JsonArray(value.map { JsonPrimitive(it as String) })
                )

                else -> return@mapNotNull null
            }
            key.name to entry
        }
        .toMap()
}

fun Set<String>.toFolders(): List<Folder> {
    return map {
        Folder(
            name = it.substringAfterLast('/'),
            path = it
        )
    }
}