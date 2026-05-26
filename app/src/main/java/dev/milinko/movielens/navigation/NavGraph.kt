package dev.milinko.movielens.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.milinko.movielens.ui.home.HomeScreen
import dev.milinko.movielens.ui.search.SearchScreen
import dev.milinko.movielens.ui.detail.MovieDetailScreen
import dev.milinko.movielens.ui.library.LibraryScreen
import dev.milinko.movielens.ui.profile.ProfileScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object MovieDetail : Screen("detail/{tmdbId}") {
        fun createRoute(tmdbId: Int) = "detail/$tmdbId"
    }
    object Library : Screen("library")
    object Profile : Screen("profile")
}

        private fun NavHostController.navigateBottomTab(route: String) {
            navigate(route) {
                popUpTo(graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

@Composable
fun MovieLensNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateToSearch = {
                    navController.navigateBottomTab(Screen.Search.route)
                },
                onNavigateToLibrary = {
                    navController.navigateBottomTab(Screen.Library.route)
                },
                onNavigateToProfile = {
                    navController.navigateBottomTab(Screen.Profile.route)
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateBack = {
                    navController.navigateBottomTab(Screen.Home.route)
                },
                onNavigateToLibrary = {
                    navController.navigateBottomTab(Screen.Library.route)
                },
                onNavigateToProfile = {
                    navController.navigateBottomTab(Screen.Profile.route)
                }
            )
        }

        composable(Screen.MovieDetail.route) { backStackEntry ->
            val tmdbId = backStackEntry.arguments?.getString("tmdbId")?.toIntOrNull() ?: return@composable
            MovieDetailScreen(
                tmdbId = tmdbId,
                onNavigateBack = {
                    navController.navigateUp()
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateToHome = {
                    navController.navigateBottomTab(Screen.Home.route)
                },
                onNavigateToSearch = {
                    navController.navigateBottomTab(Screen.Search.route)
                },
                onNavigateToProfile = {
                    navController.navigateBottomTab(Screen.Profile.route)
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateToHome = {
                    navController.navigateBottomTab(Screen.Home.route)
                },
                onNavigateToSearch = {
                    navController.navigateBottomTab(Screen.Search.route)
                },
                onNavigateToLibrary = {
                    navController.navigateBottomTab(Screen.Library.route)
                }
            )
        }
    }
}


