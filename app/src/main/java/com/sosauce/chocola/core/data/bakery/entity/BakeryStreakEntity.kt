package com.sosauce.chocola.core.data.bakery.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bakery_streak")
data class BakeryStreakEntity(
    @PrimaryKey
    val id: Int = 0,
    val currentStreak: Int,
    val bestStreak: Int,
    val lastActiveEpochDay: Long
)
