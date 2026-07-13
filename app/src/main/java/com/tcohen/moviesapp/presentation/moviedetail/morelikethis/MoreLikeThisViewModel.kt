package com.tcohen.moviesapp.presentation.moviedetail.morelikethis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.util.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for the "More like this" subsection.
 *
 * **Not** scoped to the navigation route — instantiated as `viewModel()` inside
 * the `MoreLikeThisSection` composable, so its lifecycle is tied to the section
 * being composed. This matches the `PlotExplainerSection` pattern and avoids
 * polluting the parent `MovieDetailViewModel` with yet another VM dependency.
 *
 * The view-model fires its own API call (via [MovieRepository.getSimilarMovies],
 * which wraps it in the project-wide [com.tcohen.moviesapp.data.remote.api.SafeApiCaller]).
 * No `NetworkMonitor.isCurrentlyOnline()` is read here — offline handling is owned
 * by `SafeApiCaller` (consistent with the recent refactor).
 */
@HiltViewModel
class MoreLikeThisViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _state = MutableStateFlow<MoreLikeThisState>(MoreLikeThisState.Idle)
    val state: StateFlow<MoreLikeThisState> = _state.asStateFlow()

    private val _effects = Channel<MoreLikeThisEffect>(Channel.BUFFERED)
    val effects: Flow<MoreLikeThisEffect> = _effects.receiveAsFlow()

    fun processIntent(intent: MoreLikeThisIntent) {
        when (intent) {
            is MoreLikeThisIntent.Load -> load(intent.movieId)
            is MoreLikeThisIntent.OpenDetail -> viewModelScope.launch {
                _effects.send(MoreLikeThisEffect.NavigateToDetail(intent.movieId))
            }
        }
    }

    private fun load(movieId: Int) {
        _state.value = MoreLikeThisState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getSimilarMovies(movieId)) {
                is NetworkResult.Success -> {
                    val movies = result.data
                    if (movies.isEmpty()) MoreLikeThisState.Empty
                    else MoreLikeThisState.Success(movies)
                }
                is NetworkResult.Error -> MoreLikeThisState.Error(result.message)
            }
        }
    }
}
