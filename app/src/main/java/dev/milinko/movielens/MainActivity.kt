package dev.milinko.movielens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.milinko.movielens.navigation.MovieLensNavHost
import dev.milinko.movielens.ui.theme.MovieLensTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieLensTheme {
                val navController = rememberNavController()
                MovieLensNavHost(navController)
            }
        }
    }
}
