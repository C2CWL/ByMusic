package com.sosauce.chocola.core.data.bakery

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sosauce.chocola.core.data.bakery.entity.BakeryListenEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryStreakEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryTrackEntity

@Database(
    entities = [
        BakeryTrackEntity::class,
        BakeryListenEntity::class,
        BakeryStreakEntity::class
    ],
    version = 1
)
abstract class BakeryDatabase : RoomDatabase() {
    abstract val dao: BakeryDao
}
