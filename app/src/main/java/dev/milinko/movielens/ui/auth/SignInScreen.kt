package dev.milinko.movielens.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.MovieneeNameOrigin
import dev.milinko.movielens.ui.components.MovieneeWordmark
import dev.milinko.movielens.ui.components.SettingsDialog
import dev.milinko.movielens.util.DevSettings

// Web client ID from the Firebase console (Project settings -> Your apps -> Web client), needed to
// request a Google ID token for Firebase.
private const val WEB_CLIENT_ID = "688395138951-64q747lml48uq3idgstjgj5tpa0rkgr2.apps.googleusercontent.com"

/**
 * Google sign-in screen with a poster collage background. [onDismiss] closes it without signing in
 * (the X button and system back); NavGraph decides whether to pop or go to Home.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    // Closes the screen without signing in. Both system "back" (BackHandler below) and the visible X
    // use it; NavGraph.kt decides whether there is something to pop or to go to Home (after sign-out
    // the back stack is empty and "back" would otherwise close the app).
    onDismiss: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backgroundPosters by viewModel.backgroundPosters.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Navigate when signed in
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.SignedIn) {
            onSignedIn()
        }
    }

    // Decorative poster background, loaded only here (see AuthViewModel).
    LaunchedEffect(Unit) {
        viewModel.loadBackgroundPostersIfNeeded()
    }

    BackHandler(onBack = onDismiss)

    // Google Sign-In launcher
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    viewModel.finishGoogleSignIn(idToken)
                } else {
                    Log.e("SignIn", "Google idToken is null")
                    viewModel.reportGoogleSignInFailure(context.getString(R.string.sign_in_google_failed))
                }
            } catch (e: ApiException) {
                Log.e("SignIn", "Google sign-in failed: ${e.statusCode}", e)
                // 12501 = the user closed the Google window themselves - not an error, nothing is shown.
                if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    viewModel.reportGoogleSignInFailure(context.getString(R.string.sign_in_google_failed))
                }
            }
        }
    }

    // Background: a subtle vertical gradient from theme tokens, with a collage of currently popular
    // posters (`AuthViewModel.backgroundPosters`) underneath. If the posters cannot be loaded the
    // screen is just the gradient - purely decorative, no error state.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (backgroundPosters.isNotEmpty()) {
            PosterCollageBackground(
                posterPaths = backgroundPosters,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.40f)
            )
        }

        // Radial vignette: opaque in the center (logo/title/button - readability first), transparent
        // at the edges where the collage should show. Without a collage the opaque background of the
        // outer Box is underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f)
                        )
                    )
                )
        )

        // A visible close button - users do not necessarily know that system "back" works here.
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        // Backend URL button (debug builds only, DevSettings.SHOW_BACKEND_URL_SETTINGS): Sign In is
        // the only screen always visible when signed out, so the URL can be changed here too. Uses
        // the same UrlStorage as the Profile settings, via AuthViewModel.
        var showBackendDialog by remember { mutableStateOf(false) }
        if (DevSettings.SHOW_BACKEND_URL_SETTINGS) {
            IconButton(
                onClick = { showBackendDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.sign_in_backend_content_description),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }

        if (showBackendDialog) {
            val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
            val savedRoutes by viewModel.savedRoutes.collectAsStateWithLifecycle()
            SettingsDialog(
                currentUrl = baseUrl,
                savedRoutes = savedRoutes,
                onDismiss = { showBackendDialog = false },
                onSave = {
                    viewModel.updateBaseUrl(it)
                    showBackendDialog = false
                },
                onAddRoute = { viewModel.addSavedRoute(it) },
                onDeleteRoute = { viewModel.removeSavedRoute(it) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo: the Movienee mark (drawable-nodpi/ic_movienee_mark.png) in a circle.
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.27f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_movienee_mark),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            }

            // Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MovieneeWordmark(style = MaterialTheme.typography.displayMedium)
                // Small note explaining the name (movie + matinee), as under the wordmark in the
                // original logo - separate from the tagline below.
                MovieneeNameOrigin()
                Text(
                    text = stringResource(R.string.sign_in_tagline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error banner
            if (uiState is AuthUiState.Error) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = (uiState as AuthUiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Google Sign-In button
            val isLoading = uiState is AuthUiState.Loading

            Button(
                onClick = {
                    if (!isLoading) {
                        // Sign out first so account picker always shows
                        googleSignInClient.signOut().addOnCompleteListener {
                            launcher.launch(googleSignInClient.signInIntent)
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.sign_in_loading),
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    // Google "G" logo in Google's own brand color (#4285F4), deliberately not from our
                    // theme (a required part of the Google Sign-In branding).
                    Text(
                        text = "G",
                        color = Color(0xFF4285F4),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.sign_in_google_button),
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Text(
                text = stringResource(R.string.sign_in_terms),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Static grid of poster cards (3 columns) filling the whole screen, repeating the posters if there
 * are fewer than cells. Not lazy on purpose: it is background decoration, and the list is always
 * small (one TMDB page, ~20 movies).
 */
@Composable
private fun PosterCollageBackground(
    posterPaths: List<String>,
    modifier: Modifier = Modifier
) {
    val columns = 3
    val rows = 6
    Column(modifier = modifier) {
        repeat(rows) { rowIndex ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                repeat(columns) { columnIndex ->
                    val posterPath = posterPaths[(rowIndex * columns + columnIndex) % posterPaths.size]
                    AsyncImage(
                        model = tmdbPosterUrl(posterPath, "w342"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

