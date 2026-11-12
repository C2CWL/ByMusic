package com.sosauce.chocola.core.data.bakery.result

data class TrackWithCount(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
    val durationMillis: Long,
    val listenCount: Int
)
