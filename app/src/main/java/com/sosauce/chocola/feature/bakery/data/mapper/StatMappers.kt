package com.sosauce.chocola.feature.bakery.data.mapper

import androidx.core.net.toUri
import com.sosauce.chocola.core.data.bakery.entity.BakeryStreakEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryTrackEntity
import com.sosauce.chocola.core.data.bakery.result.AlbumCount
import com.sosauce.chocola.core.data.bakery.result.ArtistCount
import com.sosauce.chocola.core.data.bakery.result.DayTotal
import com.sosauce.chocola.core.data.bakery.result.PeriodTotal
import com.sosauce.chocola.core.data.bakery.result.TrackWithCount
import com.sosauce.chocola.feature.bakery.presentation.model.AlbumStatUi
import com.sosauce.chocola.feature.bakery.presentation.model.AllTimeStatsUi
import com.sosauce.chocola.feature.bakery.presentation.model.ArtistStatUi
import com.sosauce.chocola.feature.bakery.presentation.model.PeriodCardUi
import com.sosauce.chocola.feature.bakery.presentation.model.PeriodId
import com.sosauce.chocola.feature.bakery.presentation.model.StreakUi
import com.sosauce.chocola.feature.bakery.presentation.model.TopDayUi
import com.sosauce.chocola.feature.bakery.presentation.model.TrackStatUi

fun BakeryTrackEntity.toStatUi(listenCount: Int): TrackStatUi =
    TrackStatUi(
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri?.toUri(),
        listenCount = listenCount
    )

fun TrackWithCount.toStatUi(): TrackStatUi =
    TrackStatUi(
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri?.toUri(),
        listenCount = listenCount
    )

fun ArtistCount.toStatUi(): ArtistStatUi =
    ArtistStatUi(
        name = name,
        listenCount = listenCount
    )

fun AlbumCount.toStatUi(): AlbumStatUi =
    AlbumStatUi(
        name = name,
        artist = artist,
        artworkUri = artworkUri,
        listenCount = listenCount
    )

fun BakeryStreakEntity?.toStreakUi(): StreakUi =
    if (this == null) {
        StreakUi()
    } else {
        StreakUi(
            current = currentStreak,
            best = bestStreak
        )
    }

fun DayTotal.toTopDayUi(label: String): TopDayUi =
    TopDayUi(
        epochDay = epochDay,
        label = label,
        listenCount = totalListens,
        listenedMillis = totalListenedMillis
    )

fun PeriodTotal.toAllTimeStatsUi(
    topTrack: TrackWithCount?,
    topArtist: ArtistCount?
): AllTimeStatsUi =
    AllTimeStatsUi(
        totalListens = totalListens,
        totalListenedMillis = totalListenedMillis,
        topTrack = topTrack?.toStatUi(),
        topArtist = topArtist?.toStatUi()
    )

fun PeriodTotal.toPeriodCardUi(
    id: PeriodId,
    title: String,
    year: Int?,
    topArtworkUri: String?
): PeriodCardUi =
    PeriodCardUi(
        id = id,
        title = title,
        year = year,
        totalListens = totalListens,
        topArtworkUri = topArtworkUri?.toUri()
    )
