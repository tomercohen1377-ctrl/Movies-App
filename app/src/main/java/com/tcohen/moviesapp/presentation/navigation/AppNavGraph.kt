package com.tcohen.moviesapp.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Root navigation graph.
 *
 * Screens are stubbed with placeholder composables for Phase 1.
 * Full implementations will be added in Phase 4.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = {
            BottomNavBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                PlaceholderScreen(label = "Home — coming in Phase 4")
            }
            composable(Screen.Favorites.route) {
                PlaceholderScreen(label = "Favorites — coming in Phase 4")
            }
            composable(
                route = Screen.MovieDetail.routeWithArgs,
                arguments = Screen.MovieDetail.arguments
            ) {
                PlaceholderScreen(label = "Movie Detail — coming in Phase 4")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label)
    }
}
