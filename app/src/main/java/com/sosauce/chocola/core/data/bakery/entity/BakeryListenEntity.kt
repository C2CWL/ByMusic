package com.sosauce.chocola.core.data.bakery.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bakery_listen",
    indices = [
        Index(value = ["epochDay"]),
        Index(value = ["mediaId"])
    ]
)
data class BakeryListenEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: String,
    val listenedAtMillis: Long,
    val epochDay: Long
)
