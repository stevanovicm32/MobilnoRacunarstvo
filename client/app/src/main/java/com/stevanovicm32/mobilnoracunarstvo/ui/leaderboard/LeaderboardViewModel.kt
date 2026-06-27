package com.stevanovicm32.mobilnoracunarstvo.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stevanovicm32.mobilnoracunarstvo.GameApp
import com.stevanovicm32.mobilnoracunarstvo.data.dto.LeaderboardEntryDto
import com.stevanovicm32.mobilnoracunarstvo.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val entries: List<LeaderboardEntryDto> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class LeaderboardViewModel(
    private val app: GameApp,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = app.leaderboardRepository.getLeaderboard()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            entries = result.data,
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun factory(app: GameApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LeaderboardViewModel(app) as T
                }
            }
    }
}
