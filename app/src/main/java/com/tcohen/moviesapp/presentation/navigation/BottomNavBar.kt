package com.tcohen.moviesapp.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

@Composable
fun BottomNavBar(navController: NavController) {
    val tabs = listOf(
        BottomNavItem(
            screen = Screen.Home,
            label = "Home",
            icon = Icons.Filled.Home
        ),
        BottomNavItem(
            screen = Screen.Favorites,
            label = "Favorites",
            icon = Icons.Filled.Favorite
        )
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        tabs.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {

                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomNavBarPreview() {
    MoviesAppTheme {
        BottomNavBar(navController = rememberNavController())
    }
}
