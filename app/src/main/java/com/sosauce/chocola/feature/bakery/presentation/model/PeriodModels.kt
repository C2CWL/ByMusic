package com.sosauce.chocola.feature.bakery.presentation.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class PeriodId(
    val type: PeriodType,
    val key: String
)

@Serializable
enum class PeriodType {
    MONTH,
    YEAR,
    ALL_TIME
}

@Immutable
data class PeriodCardUi(
    val id: PeriodId,
    val title: String,
    val year: Int?,
    val totalListens: Int,
    val topArtworkUri: Uri?
)
