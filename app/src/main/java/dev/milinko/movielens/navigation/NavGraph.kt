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
import dev.milinko.movielens.ui.notifications.NotificationsScreen
import dev.milinko.movielens.ui.profile.FriendsScreen
import dev.milinko.movielens.ui.profile.ProfileScreen
import dev.milinko.movielens.ui.search.SearchScreen

/** All navigation destinations; screens with arguments expose `createRoute(...)` to build the route. */
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

    // Person page, opened from Movie Detail.
    object ActorDetail : Screen("actor/{actorId}") {
        fun createRoute(actorId: Int) = "actor/$actorId"
    }

    // "See all" screens for the Home rows "Suggested for you" and "In cinemas now".
    object Suggested : Screen("browse/suggested")
    object NowInCinemas : Screen("browse/now_in_cinemas")

    object Library : Screen("library")
    object Profile : Screen("profile")
    object Friends : Screen("friends")
    object Notifications : Screen("notifications")
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

/**
 * The app's navigation graph. Most of the app works signed out; account features navigate to
 * SignIn with a `next` route to return to after signing in.
 */
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
                    // Closing without signing in: behave like a normal "back" if there is a back
                    // stack (e.g. SignIn was opened from Search/Movie Detail via onRequireSignIn);
                    // otherwise (e.g. after sign-out, which clears the whole stack - see onSignOut
                    // below) go to Home instead of closing the app.
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
                onNavigateToFriends = { navController.navigateBottomTab(Screen.Friends.route) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
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
                onNavigateToDetail = { tmdbId: Int -> navController.navigate(Screen.MovieDetail.createRoute(tmdbId)) },
                onNavigateBack = { navController.navigateBottomTab(Screen.Home.route) },
                onNavigateToLibrary = { navController.navigateBottomTab(Screen.Library.route) },
                onNavigateToProfile = { navController.navigateBottomTab(Screen.Profile.route) },
                onRequireSignIn = { navController.navigate(Screen.SignIn.createRoute(Screen.Search.route)) },
                onNavigateToFriends = { navController.navigateBottomTab(Screen.Friends.route) }
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
                onNavigateToFriends = { navController.navigateBottomTab(Screen.Friends.route) },
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
                onNavigateToFriends = { navController.navigateBottomTab(Screen.Friends.route) }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToRoute = { route -> navController.navigate(route) },
                onNavigateToSignIn = { navController.navigate(Screen.SignIn.createRoute(Screen.Notifications.route)) }
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen(
                onNavigateToHome = { navController.navigateBottomTab(Screen.Home.route) },
                onNavigateToSearch = { navController.navigateBottomTab(Screen.Search.route) },
                onNavigateToLibrary = { navController.navigateBottomTab(Screen.Library.route) },
                onNavigateToProfile = { navController.navigateBottomTab(Screen.Profile.route) },
                onNavigateToSignIn = { navController.navigate(Screen.SignIn.createRoute(Screen.Friends.route)) },
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
