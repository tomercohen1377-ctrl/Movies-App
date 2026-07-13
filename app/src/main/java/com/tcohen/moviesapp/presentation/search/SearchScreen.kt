package com.tcohen.moviesapp.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.MovieGrid
import com.tcohen.moviesapp.presentation.common.OfflineBanner
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import com.tcohen.moviesapp.util.NetworkUnavailableException

/**
 * Free-text movie search screen.
 *
 * Layout (top-to-bottom):
 *  1. [OfflineBanner] when [SearchState.isOffline] is true.
 *  2. [SearchBar] — `OutlinedTextField` with a leading search icon and a trailing
 *     clear-icon shown only when there's text.
 *  3. [SearchHistoryRow] — shown only when query is empty and history is non-empty.
 *  4. Body — driven by [SearchState] + paging [LoadState]:
 *      - `loading` → centered spinner
 *      - `error`   → [ErrorView] with friendly "search needs internet" copy when offline
 *      - `empty + !hasSearched` → "Type to search for a movie" prompt
 *      - `empty + hasSearched`  → "No matches for "{query}"" inline illustration
 *      - `populated` → 2-column paging grid via [MovieGrid] (same grid component used by Home)
 */
@Composable
fun SearchScreen(
    onNavigateToDetail: (Int) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsState()
    val results = viewModel.results.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToDetail -> onNavigateToDetail(effect.movieId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        OfflineBanner(isOffline = state.isOffline)

        SearchBar(
            query = state.query,
            onQueryChange = { viewModel.processIntent(SearchIntent.UpdateQuery(it)) },
            onClear = { viewModel.processIntent(SearchIntent.ClearQuery) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // History row is only meaningful while the input is empty.
        if (state.query.isEmpty() && history.isNotEmpty()) {
            SearchHistoryRow(
                history = history,
                onSelect = { viewModel.processIntent(SearchIntent.SelectHistory(it)) },
                onClearAll = { viewModel.processIntent(SearchIntent.ClearHistory) }
            )
        }

        SearchBody(
            hasSearched = state.hasSearched,
            query = state.query,
            isOffline = state.isOffline,
            results = results,
            onIntent = viewModel::processIntent
        )
    }
}

// ── Search bar ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search for a movie") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The "Search" IME action is a no-op — typing-as-you-search debounces the
        // request, so users don't need to press enter.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
    )
}

// ── History chips ─────────────────────────────────────────────────────────────

@Composable
private fun SearchHistoryRow(
    history: List<String>,
    onSelect: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClearAll) {
                Text("Clear")
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val count = history.size
            items(count = count, key = { idx -> history[idx] }) { idx ->
                val entry = history[idx]
                AssistChip(
                    onClick = { onSelect(entry) },
                    label = { Text(entry) },
                    colors = AssistChipDefaults.assistChipColors()
                )
            }
        }
    }
}

// ── Body ──────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBody(
    hasSearched: Boolean,
    query: String,
    isOffline: Boolean,
    results: androidx.paging.compose.LazyPagingItems<Movie>,
    onIntent: (SearchIntent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // The "haven't searched yet" branch is checked first — Paging 3 emits a
        // transient LoadState.Loading for an empty Pager too, so gating on
        // hasSearched avoids an unnecessary spinner flash on screen open.
        when {
            !hasSearched -> SearchPrompt()

            results.loadState.refresh is LoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            results.loadState.refresh is LoadState.Error -> {
                // Distinguish "you typed → API is sick" from "you typed → you're offline".
                val isNoConnection = (results.loadState.refresh as LoadState.Error).error is NetworkUnavailableException
                val message = if (isNoConnection || isOffline) {
                    "Search requires an internet connection. Showing nothing."
                } else {
                    "Search failed. Please try again."
                }
                ErrorView(
                    message = message,
                    onRetry = { results.retry() }
                )
            }

            results.itemCount == 0 -> NoMatchesState(query = query)

            else -> {
                MovieGrid(movies = results) { movie ->
                    if (movie == null) {
                        // Lightweight placeholder mirrors Home's grid slot while pages load.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    } else {
                        com.tcohen.moviesapp.presentation.common.MovieCard(
                            movie = movie,
                            onClick = { onIntent(SearchIntent.OpenDetail(movie.id)) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

// ── Body helpers ──────────────────────────────────────────────────────────────

@Composable
private fun SearchPrompt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Type to search for a movie",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun NoMatchesState(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No matches for",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "\u201C$query\u201D",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SearchPromptPreview() {
    MoviesAppTheme { SearchPrompt() }
}

@Preview(showBackground = true)
@Composable
private fun NoMatchesStatePreview() {
    MoviesAppTheme { NoMatchesState(query = "Dune") }
}
