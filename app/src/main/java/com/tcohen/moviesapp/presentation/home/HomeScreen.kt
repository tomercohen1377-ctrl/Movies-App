package com.tcohen.moviesapp.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tcohen.moviesapp.presentation.common.CategoryFilterRow
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.MovieCard
import com.tcohen.moviesapp.presentation.common.NetworkErrorFooter
import com.tcohen.moviesapp.presentation.common.ShimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val movies = viewModel.moviesFlow.collectAsLazyPagingItems()

    // One-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToDetail -> onNavigateToDetail(effect.movieId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(title = { Text("Movies") })

        // Offline banner — shown only when device has no connectivity
        AnimatedVisibility(visible = state.isOffline) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "You're offline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Category filter chip row
        CategoryFilterRow(
            selectedCategory = state.selectedCategory,
            onCategorySelected = { viewModel.processIntent(HomeIntent.SelectCategory(it)) },
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Movie grid — driven by Paging 3
        when (val refreshState = movies.loadState.refresh) {
            is LoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Error -> {
                ErrorView(
                    message = refreshState.error.message ?: "Failed to load movies",
                    onRetry = { movies.retry() }
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = movies.itemCount,
                        key = movies.itemKey { it.id }
                    ) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            MovieCard(
                                movie = movie,
                                onClick = {
                                    viewModel.processIntent(HomeIntent.OpenDetail(movie.id))
                                }
                            )
                        } else {
                            // Placeholder while the page loads
                            ShimmerEffect(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }

                    // Footer: spinner while loading next page, or error if offline boundary hit
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        when (val appendState = movies.loadState.append) {
                            is LoadState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            is LoadState.Error -> {
                                NetworkErrorFooter(onRetry = { movies.retry() })
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
