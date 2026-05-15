package dev.milinko.geminiwrapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.milinko.geminiwrapper.ui.chat.ChatScreen
import dev.milinko.geminiwrapper.ui.theme.GeminiWrapperTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeminiWrapperTheme {
                ChatScreen()
            }
        }
    }
}