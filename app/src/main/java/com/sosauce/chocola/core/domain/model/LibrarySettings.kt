package com.sosauce.chocola.core.domain.model

data class TracksSettings(
    val sort: TrackSort,
    val ascending: Boolean
)

data class SearchSettings(
    val regex: Boolean,
    val matchCase: Boolean
)
