package com.sosauce.chocola.feature.bakery.presentation.story

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import com.sosauce.chocola.feature.bakery.presentation.model.PeriodId
import com.sosauce.chocola.feature.bakery.presentation.model.StoryPageUi

@Stable
data class BakeryStoryState(
    val isLoading: Boolean = true,
    val period: PeriodId? = null,
    val pages: List<StoryPageUi> = emptyList(),
    val currentPage: Int = 0,
    val pageProgress: Float = 0f,
    val isPaused: Boolean = false,
    val isSaving: Boolean = false
)

sealed interface BakeryStoryAction {
    data object NextPage : BakeryStoryAction
    data object PreviousPage : BakeryStoryAction
    data object Pause : BakeryStoryAction
    data object Resume : BakeryStoryAction
    data object Close : BakeryStoryAction
    data class SaveSummary(val bitmap: Bitmap) : BakeryStoryAction
}

sealed interface BakeryStoryEvent {
    data object Close : BakeryStoryEvent
    data object SummarySaved : BakeryStoryEvent
    data object SaveFailed : BakeryStoryEvent
}
