package com.photopuzzle.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photopuzzle.app.data.models.StatsOverview
import com.photopuzzle.app.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: StatsRepository
) : ViewModel() {

    val stats: StateFlow<StatsOverview?> = repository.getStatsOverview()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun clearStats() {
        viewModelScope.launch { repository.clearAll() }
    }
}
