package com.sosauce.chocola.feature.bakery.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosauce.chocola.feature.bakery.data.BakeryRepository
import com.sosauce.chocola.feature.bakery.presentation.model.StreakUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class BakeryViewModel(
    private val repository: BakeryRepository
) : ViewModel() {

    val state = combine(
        repository.observeAllTimeStats(),
        repository.observeStreak(),
        repository.observeMonths(),
        repository.observeYears(),
        repository.observeHasListens()
    ) { allTime, streak, months, years, hasListens ->
        BakeryState(
            isLoading = false,
            allTime = allTime.takeIf { hasListens },
            streak = if (hasListens) streak else StreakUi(),
            months = months,
            years = years
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BakeryState(isLoading = true)
    )

    private val _events = Channel<BakeryEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: BakeryAction) {
        when (action) {
            is BakeryAction.OpenPeriod -> {
                _events.trySend(BakeryEvent.NavigateToStory(action.period))
            }

            BakeryAction.NavigateBack -> {
                _events.trySend(BakeryEvent.NavigateBack)
            }
        }
    }
}
