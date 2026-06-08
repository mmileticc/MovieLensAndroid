package dev.milinko.movielens.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.milinko.movielens.ui.auth.SignInScreen
import dev.milinko.movielens.ui.detail.MovieDetailScreen
import dev.milinko.movielens.ui.home.HomeScreen
import dev.milinko.movielens.ui.library.LibraryScreen
import dev.milinko.movielens.ui.profile.ProfileScreen
import dev.milinko.movielens.ui.search.SearchScreen

sealed class Screen(val route: String) {
    object SignIn : Screen("sign_in?next={next}") {
        const val baseRoute = "sign_in"
        fun createRoute(next: String? = null): String = if (next.isNullOrBlank()) baseRoute else "$baseRoute?next=$next"
    }

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
fun MovieLensNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = Screen.SignIn.route,
            arguments = listOf(navArgument("next") {
                type = NavType.StringType
                defaultValue = "home"
                nullable = false
            })
        ) { backStackEntry ->
            val nextRoute = backStackEntry.arguments?.getString("next").orEmpty().ifBlank { Screen.Home.route }
            SignInScreen(
                onSignedIn = {
                    navController.navigate(nextRoute) {
                        popUpTo(Screen.SignIn.baseRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateToSearch = { navController.navigateBottomTab(Screen.Search.route) },
                onNavigateToLibrary = { navController.navigateBottomTab(Screen.Library.route) },
                onNavigateToProfile = { navController.navigateBottomTab(Screen.Profile.route) }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                hiltViewModel(),
                { tmdbId: Int -> navController.navigate(Screen.MovieDetail.createRoute(tmdbId)) },
                { navController.navigateBottomTab(Screen.Home.route) },
                { navController.navigateBottomTab(Screen.Library.route) },
                { navController.navigateBottomTab(Screen.Profile.route) },
                { navController.navigate(Screen.SignIn.createRoute(Screen.Search.route)) }
            )
        }

        composable(Screen.MovieDetail.route) { backStackEntry ->
            val tmdbId = backStackEntry.arguments?.getString("tmdbId")?.toIntOrNull() ?: return@composable
            MovieDetailScreen(
                tmdbId = tmdbId,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateToHome = { navController.navigateBottomTab(Screen.Home.route) },
                onNavigateToSearch = { navController.navigateBottomTab(Screen.Search.route) },
                onNavigateToProfile = { navController.navigateBottomTab(Screen.Profile.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                hiltViewModel(),
                { navController.navigateBottomTab(Screen.Home.route) },
                { navController.navigateBottomTab(Screen.Search.route) },
                { navController.navigateBottomTab(Screen.Library.route) },
                {
                    navController.navigate(Screen.SignIn.createRoute(Screen.Home.route)) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
