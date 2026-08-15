package dev.milinko.movielens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Prvi put uveden u Fazi 6 redizajna - pre ovoga je svaki ekran koristio svoj ad-hoc
 * RoundedCornerShape(...) (8dp/12dp/14dp mešano, videti MOVIELENS_DESIGN_PLAN.md 1.3).
 *
 * small = chip, dugme, mali bedž
 * medium = kartica filma, showtime kartica (podrazumevani oblik za većinu kartica)
 * large = bottom sheet, dialog, donji rub hero slike
 */
val MovieLensShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp)
)

// Poster slike su UVEK 8dp (dosledno kroz sve kartice) - ne zavisi od Shapes() skale iznad jer
// Material3 Shapes nema poseban slot za "poster" - koristi se direktno gde treba.
val MlPosterCornerRadius = 8.dp
