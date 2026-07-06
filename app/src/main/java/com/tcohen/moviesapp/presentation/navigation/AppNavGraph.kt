package com.tcohen.moviesapp.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tcohen.moviesapp.presentation.common.OfflineBanner
import com.tcohen.moviesapp.presentation.favorites.FavoritesScreen
import com.tcohen.moviesapp.presentation.home.HomeScreen
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailScreen
import com.tcohen.moviesapp.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    networkMonitor: NetworkMonitor = hiltViewModel<NetworkMonitorHolder>().monitor
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showChrome = currentRoute in setOf(
        Screen.Home.route,
        Screen.Favorites.route
    )

    Scaffold(
        topBar = {
            if (showChrome) {
                OfflineBanner(networkMonitor = networkMonitor)
            }
        },
        bottomBar = {
            if (showChrome) {
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
                HomeScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.MovieDetail.routeWithArgs,
                arguments = Screen.MovieDetail.arguments,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth })
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
                },
                exitTransition = null,
                popEnterTransition = null
            ) {
                MovieDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@HiltViewModel
class NetworkMonitorHolder @Inject constructor(
    val monitor: NetworkMonitor,
) : ViewModel()
