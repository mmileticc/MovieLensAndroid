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
    val backgroundPosters by viewModel.backgroundPosters.collectAsState()
    val context = LocalContext.current

    // Navigate when signed in
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.SignedIn) {
            onSignedIn()
        }
    }

    // UI dorada (17. avgust 2026) - vidi napomenu u AuthViewModel.kt: poster kolaž pozadina,
    // učitava se samo ovde (ne u init{}) da se izbegne nepotreban poziv kad se AuthViewModel
    // reuse-uje u ProfileScreen top baru za isSignedIn proveru.
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
                // 12501 = korisnik je sam zatvorio Google prozor - to nije greška, ne prikazujemo ništa.
                if (e.statusCode != GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    viewModel.reportGoogleSignInFailure(context.getString(R.string.sign_in_google_failed))
                }
            }
        }
    }

    // Faza 4 redizajna: gradijent je ranije bio inline ljubičasto-crn (#070710/#0F0F1A/#0B0B12),
    // van teme i van naše palete. Sad ide iz centralizovanih tokena (background -> surfaceElevated1)
    // - i dalje suptilan vertikalni gradijent (koncept se zadržava, videti MOVIELENS_DESIGN_PLAN.md
    // 2.1), samo bez tuđe boje.
    //
    // UI dorada (17. avgust 2026, korisnička žalba "sign in deluje prazno") - zatvoreno "otvoreno
    // pitanje" iz plana 6: dodat poster kolaž ispod gradijenta. Grid se puni pravim trenutno
    // popularnim filmovima (`AuthViewModel.backgroundPosters`, ista javna /trending ruta kao Home) -
    // ako poziv ne uspe (offline/hladan start backenda) lista ostaje prazna i ekran izgleda
    // IDENTIČNO kao pre ove dorade (čist gradijent), bez ikakvog error stanja - ovo je čisto
    // dekorativno.
    //
    // Ispravka (17. avgust 2026, korisnik prijavio "ne vidim promenu") - prva verzija je imala
    // grešku u računu providnosti: gornji/donji kraj vertikalnog gradijenta su bili POTPUNO
    // neprovidni (`colorScheme.background` bez alpha), a čak i sredina je bila providna samo 12%
    // (`surface.copy(alpha=0.88f)`) - preko kolaža koji je i sam bio na svega 18% providnosti. Neto
    // efekat: kolaž praktično nevidljiv. Zamenjeno radijalnim vinjet-om (providno na ivicama, gde i
    // onako nema teksta osim blede terms poruke, neprovidno u centru gde sede logo/naslov/dugme) i
    // podignuta providnost postera na 40% - sad stvarno vidljivo, a centar ekrana (gde je čitljivost
    // najbitnija) ostaje čist.
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

        // Radijalni vinjet: neproviran u centru (gde sede logo/naslov/dugme - čitljivost prioritet),
        // providan na ivicama (gde se kolaž treba VIDETI). Kad kolaža nema, ovo je i dalje solidno
        // oslonjeno na opaque `.background(colorScheme.background)` ispod (spoljašnji Box).
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

        // Dorada (17. avgust 2026, korisnička žalba "ne mogu ništa da uradim kad sam izlogovan jer
        // se backend ruta vidi samo kad si ulogovan") - urediva backend ruta je ranije bila SAMO na
        // Profile → Podešavanja tabu, koji je sad zaključan iza prijave (dorada #2). Sign In je
        // jedini ekran koji izlogovan korisnik uvek vidi, pa dugme ide ovde, simetrično sa X gore
        // levo. Reciklira isti UrlStorage preko AuthViewModel-a - ista podešavanja kao Profile.
        //
        // Dorada (1. septembar 2026, priprema APK-a za spoljne testere) - dugme je sad vidljivo
        // samo kad je DevSettings.SHOW_BACKEND_URL_SETTINGS = true (videti obrazloženje tamo zašto
        // NIJE vezano za BuildConfig.DEBUG). Testeri i dalje dobijaju `debug`-potpisan APK (radi bez
        // keystore-a), ali sa ovim flag-om na `false` ne vide/ne mogu da menjaju backend rutu -
        // ostaju na difoltnoj (Render) ruti iz UrlStorage.kt.
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
            val baseUrl by viewModel.baseUrl.collectAsState()
            val savedRoutes by viewModel.savedRoutes.collectAsState()
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
            // Logo (rebrending 25. avgust 2026) - stvaran Movienee znak (crvena filmska traka/dva
            // profila) umesto generičke Icons.Default.Movie ikonice, videti
            // drawable-nodpi/ic_movienee_mark.png. Krug pozadina/ivica nedirnuti (isti tretman kao
            // pre).
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
                // Sitno objašnjenje odakle ime dolazi (movie + matinee), isto kao mala napomena
                // ispod wordmark-a u originalnom logu - odvojeno od funkcionalnog taglina ispod.
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

/**
 * Statična mreža poster kartica (3 kolone) koja popunjava ceo ekran, ciklično ponavljajući
 * dostupne postere ako ih ima manje od broja ćelija - namerno NE lazy/skrolujuća (ovo je
 * dekoracija u pozadini, ne interaktivna lista), veličina liste je uvek mala (jedna TMDB stranica,
 * ~20 filmova) pa je obična ugnježdena Column/Row kompozicija sasvim dovoljna.
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

