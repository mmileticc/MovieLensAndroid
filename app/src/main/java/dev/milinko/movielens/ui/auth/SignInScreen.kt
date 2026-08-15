package dev.milinko.movielens.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import dev.milinko.movielens.R

// ⚠️  Replace this with your actual Web Client ID from Firebase Console →
//     Project Settings → Your apps → Web client (auto created by Google Service) → Client ID
private const val WEB_CLIENT_ID = "688395138951-64q747lml48uq3idgstjgj5tpa0rkgr2.apps.googleusercontent.com"

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    // Zatvara ekran bez prijave - ranije jedini izlaz je bio sistemsko dugme "nazad", što je posle
    // odjave (back stack potpuno očišćen u NavGraph.kt) gasilo celu aplikaciju umesto da vrati na
    // Home. Sad se i sistemsko "nazad" (BackHandler ispod) i vidljivo X dugme oslanjaju na isti
    // callback - NavGraph.kt odlučuje da li ima šta da se pop-uje ili treba direktno na Home.
    onDismiss: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Navigate when signed in
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.SignedIn) {
            onSignedIn()
        }
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
                }
            } catch (e: ApiException) {
                Log.e("SignIn", "Google sign-in failed: ${e.statusCode}", e)
            }
        }
    }

    // Faza 4 redizajna: gradijent je ranije bio inline ljubičasto-crn (#070710/#0F0F1A/#0B0B12),
    // van teme i van naše palete. Sad ide iz centralizovanih tokena (background -> surfaceElevated1)
    // - i dalje suptilan vertikalni gradijent (koncept se zadržava, videti MOVIELENS_DESIGN_PLAN.md
    // 2.1), samo bez tuđe boje. Pozadinski poster-collage je i dalje otvoreno pitanje (plan 6),
    // nije dodat u ovoj fazi.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Vidljivo dugme za zatvaranje - ne oslanjamo se samo na sistemsko "nazad" jer korisnici
        // ne moraju znati da će ono raditi ovde (posle odjave back stack je bio prazan i pre ove
        // izmene bi "nazad" ugasio celu aplikaciju).
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo / Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.27f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.displayMedium
                )
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
                    // Google "G" logo - Google-ova sopstvena brend boja (#4285F4), namerno NE ide
                    // iz naše teme (obavezan deo Google Sign-In vizuelnog identiteta).
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

