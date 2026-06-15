package com.tcohen.moviesapp.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.tcohen.moviesapp.presentation.favorites.FavoritesEffect
import com.tcohen.moviesapp.presentation.favorites.FavoritesScreen
import com.tcohen.moviesapp.presentation.favorites.FavoritesViewModel
import com.tcohen.moviesapp.presentation.home.HomeEffect
import com.tcohen.moviesapp.presentation.home.HomeScreen
import com.tcohen.moviesapp.presentation.home.HomeViewModel
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailScreen
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailViewModel

/**
 * Root navigation graph.
 *
 * All Hilt ViewModel injection and lifecycle-aware state collection is concentrated here,
 * keeping each screen composable free of Android DI dependencies.
 *
 * [NavHostController] is intentionally kept as an internal implementation detail
 * so callers (e.g. MainActivity) do not need `navigation-compose` on their classpath.
 */
@Composable
fun AppNavGraph() {
    val navController: NavHostController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Screen.Home.route,
        Screen.Favorites.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val viewModel: HomeViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val movies = viewModel.moviesFlow.collectAsLazyPagingItems()

                // Collect one-shot navigation effects and route them through navController
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is HomeEffect.NavigateToDetail -> navController.navigate(
                                Screen.MovieDetail.createRoute(effect.movieId)
                            ) { launchSingleTop = true }
                        }
                    }
                }

                HomeScreen(
                    state = state,
                    movies = movies,
                    onIntent = viewModel::processIntent
                )
            }

            composable(Screen.Favorites.route) {
                val viewModel: FavoritesViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val favorites = viewModel.favoritesFlow.collectAsLazyPagingItems()

                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is FavoritesEffect.NavigateToDetail -> navController.navigate(
                                Screen.MovieDetail.createRoute(effect.movieId)
                            ) { launchSingleTop = true }
                        }
                    }
                }

                FavoritesScreen(
                    state = state,
                    favorites = favorites,
                    onIntent = viewModel::processIntent
                )
            }

            composable(
                route = Screen.MovieDetail.routeWithArgs,
                arguments = Screen.MovieDetail.arguments,
                // Slide in from the right when navigating to detail
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth })
                },
                // Slide out to the right when popping back
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
                },
                // Keep the host screens still (no exit animation when pushing detail)
                exitTransition = null,
                popEnterTransition = null
            ) {
                val viewModel: MovieDetailViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MovieDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    uiState = uiState,
                    onIntent = viewModel::processIntent
                )
            }
        }
    }
}
