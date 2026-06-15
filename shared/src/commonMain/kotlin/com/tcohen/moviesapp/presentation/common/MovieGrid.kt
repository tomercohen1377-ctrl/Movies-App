package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.LazyPagingItems
import com.tcohen.moviesapp.domain.model.Movie

/**
 * Shared 2-column movie grid used by both `HomeScreen` and `FavoritesScreen`.
 *
 * Handles:
 * - Grid layout (2 fixed columns, 12 dp padding, 8 dp gaps)
 * - Append footer — spinner while the next page is loading, [NetworkErrorFooter] on error
 *
 * Callers supply [itemContent] to control how each item is rendered. The lambda receives the
 * [Movie] (or `null` for not-yet-loaded placeholder slots) and should handle both cases.
 */
@Composable
fun MovieGrid(
    movies: LazyPagingItems<Movie>,
    modifier: Modifier = Modifier,
    itemContent: @Composable LazyGridItemScope.(movie: Movie?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(count = movies.itemCount) { index ->
            itemContent(movies[index])
        }

        // Footer: spinner while the next page loads, error if the append fails
        item(span = { GridItemSpan(maxLineSpan) }) {
            when {
                movies.loadState.append is LoadStateLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                movies.loadState.append is LoadStateError -> {
                    NetworkErrorFooter(onRetry = { movies.retry() })
                }
                else -> {}
            }
        }
    }
}
