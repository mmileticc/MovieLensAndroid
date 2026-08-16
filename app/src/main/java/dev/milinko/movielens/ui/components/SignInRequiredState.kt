package dev.milinko.movielens.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Deljeni "potrebna je prijava" prikaz (17. avgust 2026, korisnička žalba) - koriste ga Library i
 * Profile ekrani kad korisnik nije prijavljen. Zamenjuje dva ranija, lošija ponašanja: (a) Profile
 * je prikazivao poluprazan/"pokvaren" oblik forme za izmenu profila (bez username-a, bez avatara,
 * ništa se ne može ni sačuvati bez tokena) kao da je normalan, samo prazan profil; (b) Library je
 * samo ispisivao golu rečenicu bez ikakve akcije. Namerno NE navigira automatski na Sign In čim se
 * tab otvori - korisnik i dalje slobodno pregleda Home/Search preko donje navigacije, ovaj prikaz
 * mu samo jasno kaže šta mu fali na OVOM tabu i nudi dugme da to reši kad poželi (isti obrazac kao
 * standardni "prijavi se da vidiš X" prikaz u većini aplikacija).
 */
@Composable
fun SignInRequiredState(
    title: String,
    message: String,
    buttonText: String,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = null,
            modifier = Modifier.height(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSignInClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(buttonText)
        }
    }
}
