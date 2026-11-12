package com.sosauce.chocola.core.data.auto

import androidx.media3.common.MediaItem
import com.sosauce.chocola.core.data.library.AbstractTracksScanner
import com.sosauce.chocola.core.data.mapper.toMediaItem

class AndroidAutoHelper(
    private val abstractTracksScanner: AbstractTracksScanner
) {

    fun getChildrenMediaItems(
        limit: Int,
        offset: Int
    ): List<MediaItem> {
        val allTracks = abstractTracksScanner.latestTracks().value

        return if (limit > 0 && offset >= 0) {
            allTracks
                .drop(offset)
                .take(limit)
                .map { it.toMediaItem() }
        } else {
            allTracks.map { it.toMediaItem() }
        }
    }

}