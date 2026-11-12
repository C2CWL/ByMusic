package com.sosauce.chocola.feature.bakery.presentation

import androidx.compose.runtime.Stable
import com.sosauce.chocola.feature.bakery.presentation.model.AllTimeStatsUi
import com.sosauce.chocola.feature.bakery.presentation.model.PeriodCardUi
import com.sosauce.chocola.feature.bakery.presentation.model.PeriodId
import com.sosauce.chocola.feature.bakery.presentation.model.StreakUi

@Stable
data class BakeryState(
    val isLoading: Boolean = true,
    val allTime: AllTimeStatsUi? = null,
    val streak: StreakUi = StreakUi(),
    val months: List<PeriodCardUi> = emptyList(),
    val years: List<PeriodCardUi> = emptyList()
)

sealed interface BakeryAction {
    data class OpenPeriod(val period: PeriodId) : BakeryAction
    data object NavigateBack : BakeryAction
}

sealed interface BakeryEvent {
    data class NavigateToStory(val period: PeriodId) : BakeryEvent
    data object NavigateBack : BakeryEvent
}
