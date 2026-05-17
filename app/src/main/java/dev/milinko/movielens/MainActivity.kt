package dev.milinko.movielens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.milinko.movielens.ui.chat.v2.ChatScreenV2

import dev.milinko.movielens.ui.theme.MovieLensTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MovieLensTheme {
                ChatScreenV2()
            }
        }
    }
}