package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcohen.moviesapp.presentation.common.ErrorView

@Composable
fun MovieDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSimilar: (Int) -> Unit = {},
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Tracks whether the YouTube player has fired its onReady callback.
    // Until it does (and we have a trailerKey), we keep the loading overlay visible.
    var playerReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {

            is MovieDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is MovieDetailUiState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { viewModel.processIntent(MovieDetailIntent.Reload) }
                )
            }

            is MovieDetailUiState.Success -> {
                // Render content immediately so the YouTube player can warm up in the background.
                // A loading overlay is shown on top until the player signals it's ready.
                val isPlayerPending = state.trailerKey != null && !playerReady

                MovieDetailContent(
                    uiState = state,
                    onPlayerReady = { playerReady = true },
                    modifier = Modifier.fillMaxSize(),
                    onNavigateToSimilar = onNavigateToSimilar
                )

                if (isPlayerPending) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // Favorite FAB — bottom-right, only in Success state
                val fabColor by animateColorAsState(
                    targetValue = if (state.isFavorite) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.surface,
                    animationSpec = tween(durationMillis = FAB_COLOR_ANIMATION_MS),
                    label = "fav_color"
                )
                FloatingActionButton(
                    onClick = { viewModel.processIntent(MovieDetailIntent.ToggleFavorite) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    containerColor = fabColor
                ) {
                    Icon(
                        imageVector = if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (state.isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (state.isFavorite) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Back button — overlaid on top-left, always visible regardless of state.
        // Calls onNavigateBack() directly — no intent/effect needed for simple navigation.
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = BACK_BUTTON_SCRIM_ALPHA), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

private const val BACK_BUTTON_SCRIM_ALPHA = 0.45f
private const val FAB_COLOR_ANIMATION_MS = 300
