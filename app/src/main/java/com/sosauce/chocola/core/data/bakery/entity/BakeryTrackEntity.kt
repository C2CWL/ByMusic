package com.sosauce.chocola.core.data.bakery.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bakery_track")
data class BakeryTrackEntity(
    @PrimaryKey
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
    val durationMillis: Long
)
