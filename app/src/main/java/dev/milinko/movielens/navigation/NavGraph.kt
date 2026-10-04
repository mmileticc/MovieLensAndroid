package dev.milinko.movielens.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.milinko.movielens.ui.actor.ActorDetailScreen
import dev.milinko.movielens.ui.auth.SignInScreen
import dev.milinko.movielens.ui.browse.NowInCinemasScreen
import dev.milinko.movielens.ui.browse.SuggestedMoviesScreen
import dev.milinko.movielens.ui.detail.MovieDetailScreen
import dev.milinko.movielens.ui.friendprofile.FriendProfileScreen
import dev.milinko.movielens.ui.home.HomeScreen
import dev.milinko.movielens.ui.library.LibraryScreen
import dev.milinko.movielens.ui.movienight.MovieNightScreen
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

    // E2.3 (2. septembar 2026) - stranica glumca, otvara se sa Movie Detail-a.
    object ActorDetail : Screen("actor/{actorId}") {
        fun createRoute(actorId: Int) = "actor/$actorId"
    }

    // "Vidi sve" ekrani za Home trake "Predloženo za tebe" i "U bioskopima sada".
    object Suggested : Screen("browse/suggested")
    object NowInCinemas : Screen("browse/now_in_cinemas")

    object Library : Screen("library")
    object Profile : Screen("profile")
    object FriendProfile : Screen("friend_profile/{friendId}") {
        fun createRoute(friendId: String) = "friend_profile/$friendId"
    }
    object MovieNight : Screen("movie_night/{friendId}") {
        fun createRoute(friendId: String) = "movie_night/$friendId"
    }
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
                },
                // Zatvaranje bez prijave - ako ima šta u back stack-u (npr. stiglo se ovde iz
                // Search/Movie Detail preko onRequireSignIn), ponašaj se kao obično "nazad". Ako
                // nema (npr. posle odjave iz Profile-a, gde se ceo stack briše - videti onSignOut
                // ispod), idi na Home umesto da se aplikacija ugasi.
                onDismiss = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            launchSingleTop = true
                        }
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
                onNavigateToProfile = { navController.navigateBottomTab(Screen.Profile.route) },
                onNavigateToSuggested = { navController.navigate(Screen.Suggested.route) },
                onNavigateToNowInCinemas = { navController.navigate(Screen.NowInCinemas.route) }
            )
        }

        composable(Screen.Suggested.route) {
            SuggestedMoviesScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                }
            )
        }

        composable(Screen.NowInCinemas.route) {
            NowInCinemasScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                }
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
                onNavigateBack = { navController.navigateUp() },
                onRequireSignIn = {
                    navController.navigate(Screen.SignIn.createRoute(Screen.MovieDetail.createRoute(tmdbId)))
                },
                onNavigateToFriendProfile = { friendId: String ->
                    navController.navigate(Screen.FriendProfile.createRoute(friendId))
                },
                onNavigateToActor = { actorId: Int ->
                    navController.navigate(Screen.ActorDetail.createRoute(actorId))
                }
            )
        }

        composable(Screen.ActorDetail.route) { backStackEntry ->
            val actorId = backStackEntry.arguments?.getString("actorId")?.toIntOrNull() ?: return@composable
            ActorDetailScreen(
                personId = actorId,
                onNavigateBack = { navController.navigateUp() },
                onNavigateToMovie = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateToHome = { navController.navigateBottomTab(Screen.Home.route) },
                onNavigateToSearch = { navController.navigateBottomTab(Screen.Search.route) },
                onNavigateToProfile = { navController.navigateBottomTab(Screen.Profile.route) },
                onNavigateToSignIn = { navController.navigate(Screen.SignIn.createRoute(Screen.Library.route)) }
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
                },
                {
                    navController.navigate(Screen.SignIn.createRoute(Screen.Profile.route))
                },
                onNavigateToFriendProfile = { friendId: String ->
                    navController.navigate(Screen.FriendProfile.createRoute(friendId))
                },
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                }
            )
        }

        composable(Screen.FriendProfile.route) {
            FriendProfileScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                },
                onNavigateToMovieNight = { friendId: String ->
                    navController.navigate(Screen.MovieNight.createRoute(friendId))
                }
            )
        }

        composable(Screen.MovieNight.route) {
            MovieNightScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToDetail = { tmdbId: Int ->
                    navController.navigate(Screen.MovieDetail.createRoute(tmdbId))
                }
            )
        }
    }
}
