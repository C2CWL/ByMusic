package com.sosauce.chocola.core.data.bakery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sosauce.chocola.core.data.bakery.entity.BakeryListenEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryStreakEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryTrackEntity
import com.sosauce.chocola.core.data.bakery.result.AlbumCount
import com.sosauce.chocola.core.data.bakery.result.ArtistCount
import com.sosauce.chocola.core.data.bakery.result.DayTotal
import com.sosauce.chocola.core.data.bakery.result.PeriodTotal
import com.sosauce.chocola.core.data.bakery.result.TrackWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface BakeryDao {

    @Upsert
    suspend fun upsertTrack(track: BakeryTrackEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<BakeryTrackEntity>)

    @Insert
    suspend fun insertListen(listen: BakeryListenEntity): Long

    @Insert
    suspend fun insertListens(listens: List<BakeryListenEntity>): List<Long>

    @Transaction
    suspend fun recordListen(track: BakeryTrackEntity, listen: BakeryListenEntity) {
        upsertTrack(track)
        insertListen(listen)
    }

    @Upsert
    suspend fun upsertStreak(streak: BakeryStreakEntity)

    @Query("SELECT * FROM bakery_streak WHERE id = 0")
    suspend fun getStreak(): BakeryStreakEntity?

    @Query("SELECT * FROM bakery_streak WHERE id = 0")
    fun observeStreak(): Flow<BakeryStreakEntity?>

    @Query("SELECT * FROM bakery_track WHERE mediaId = :mediaId")
    suspend fun getTrack(mediaId: String): BakeryTrackEntity?

    @Query(
        """
        SELECT COUNT(l.id) AS totalListens,
               COALESCE(SUM(t.durationMillis), 0) AS totalListenedMillis
        FROM bakery_listen l
        INNER JOIN bakery_track t ON t.mediaId = l.mediaId
        """
    )
    fun observeAllTimeTotal(): Flow<PeriodTotal>

    @Query(
        """
        SELECT COUNT(l.id) AS totalListens,
               COALESCE(SUM(t.durationMillis), 0) AS totalListenedMillis
        FROM bakery_listen l
        INNER JOIN bakery_track t ON t.mediaId = l.mediaId
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
        """
    )
    fun observePeriodTotal(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<PeriodTotal>

    @Query(
        """
        SELECT t.mediaId AS mediaId,
               t.title AS title,
               t.artist AS artist,
               t.album AS album,
               t.artworkUri AS artworkUri,
               t.durationMillis AS durationMillis,
               COUNT(l.id) AS listenCount
        FROM bakery_track t
        INNER JOIN bakery_listen l ON l.mediaId = t.mediaId
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY t.mediaId
        ORDER BY listenCount DESC, t.title COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeTopTracks(
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int
    ): Flow<List<TrackWithCount>>

    @Query(
        """
        SELECT t.artist AS name,
               COUNT(l.id) AS listenCount
        FROM bakery_track t
        INNER JOIN bakery_listen l ON l.mediaId = t.mediaId
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY t.artist
        ORDER BY listenCount DESC, name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeTopArtists(
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int
    ): Flow<List<ArtistCount>>

    @Query(
        """
        SELECT t.album AS name,
               t.artist AS artist,
               MAX(t.artworkUri) AS artworkUri,
               COUNT(l.id) AS listenCount
        FROM bakery_track t
        INNER JOIN bakery_listen l ON l.mediaId = t.mediaId
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY t.album, t.artist
        ORDER BY listenCount DESC, name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    fun observeTopAlbums(
        startEpochDay: Long,
        endEpochDay: Long,
        limit: Int
    ): Flow<List<AlbumCount>>

    @Query(
        """
        SELECT l.epochDay AS epochDay,
               COUNT(l.id) AS totalListens,
               COALESCE(SUM(t.durationMillis), 0) AS totalListenedMillis
        FROM bakery_listen l
        INNER JOIN bakery_track t ON t.mediaId = l.mediaId
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
        GROUP BY l.epochDay
        ORDER BY totalListens DESC, l.epochDay ASC
        LIMIT 1
        """
    )
    fun observeTopDay(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<DayTotal?>

    @Query(
        """
        SELECT COUNT(DISTINCT l.mediaId) AS distinctTrackCount
        FROM bakery_listen l
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
        """
    )
    fun observeDistinctTrackCount(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<Int>

    @Query(
        """
        SELECT strftime('%Y-%m', l.epochDay * 86400, 'unixepoch') AS monthKey
        FROM bakery_listen l
        GROUP BY monthKey
        ORDER BY monthKey DESC
        """
    )
    fun observeMonthKeys(): Flow<List<String>>

    @Query(
        """
        SELECT strftime('%Y', l.epochDay * 86400, 'unixepoch') AS yearKey
        FROM bakery_listen l
        GROUP BY yearKey
        ORDER BY yearKey DESC
        """
    )
    fun observeYearKeys(): Flow<List<String>>

    @Query(
        """
        SELECT t.artworkUri AS artworkUri
        FROM bakery_listen l
        INNER JOIN bakery_track t ON t.mediaId = l.mediaId
        WHERE l.epochDay BETWEEN :startEpochDay AND :endEpochDay
          AND t.artworkUri IS NOT NULL
        GROUP BY t.artworkUri
        ORDER BY COUNT(l.id) DESC
        LIMIT 1
        """
    )
    fun observePeriodArtwork(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<String?>
}
