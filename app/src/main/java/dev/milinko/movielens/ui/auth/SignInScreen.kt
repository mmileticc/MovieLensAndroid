package dev.milinko.movielens.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

// ⚠️  Replace this with your actual Web Client ID from Firebase Console →
//     Project Settings → Your apps → Web client (auto created by Google Service) → Client ID
private const val WEB_CLIENT_ID = "688395138951-64q747lml48uq3idgstjgj5tpa0rkgr2.apps.googleusercontent.com"

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF070710), Color(0xFF0F0F1A), Color(0xFF0B0B12))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
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
                    .background(Color(0x22E50914), CircleShape)
                    .border(1.dp, Color(0x44E50914), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(48.dp)
                )
            }

            // Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MovieLens",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Otkrij, skeniraj i deli filmove\nsa prijateljima",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error banner
            if (uiState is AuthUiState.Error) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2A1111)
                ) {
                    Text(
                        text = (uiState as AuthUiState.Error).message,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFE50914)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Prijavljivanje…",
                        color = Color.DarkGray,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    // Google "G" logo via text (works without image dependency)
                    Text(
                        text = "G",
                        color = Color(0xFF4285F4),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Nastavi sa Google nalogom",
                        color = Color.DarkGray,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }

            Text(
                text = "Prijavljivanjem prihvataš uslove korišćenja i politiku privatnosti.",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

