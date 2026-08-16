package dev.milinko.movielens.util

import retrofit2.HttpException
import java.io.IOException

/**
 * Faza A (16. avgust 2026, korisnička žalba: "video sam ispis greške tipa timeout ka ruti, to ne
 * treba nikad da se vidi") - do sad je skoro svaki ViewModel radio `e.message ?: "srpska poruka"`,
 * što znači da se srpska poruka koristila SAMO kad izuzetak nema tekst - a mrežni izuzeci (timeout,
 * DNS greška, prekinuta konekcija...) skoro uvek imaju tehnički tekst na engleskom
 * (`SocketTimeoutException`, `Unable to resolve host "..."`, itd.), pa se taj sirovi tekst
 * provlačio direktno do korisnika. Sign In ekran (`AuthViewModel`) je bio posebno izložen jer
 * `AuthApi.exchangeFirebaseToken` NIJE umotan u `Response<T>` (Retrofit sam baca `HttpException`/
 * `IOException` direktno, ne vraća "neuspešan" `Response` objekat kao ostali pozivi).
 *
 * Ova funkcija zamenjuje taj obrazac svuda: poznati "tehnički" izuzeci (nema konekcije, timeout,
 * HTTP 401/403/404/5xx) se prevode u razumljivu srpsku poruku BEZ obzira na `e.message`. Za sve
 * ostalo (npr. izuzeci koje repository sloj sam baca sa već smislenim tekstom - videti
 * `UserRepository.parseErrorMessage`, poruke kao "Korisničko ime je već zauzeto") koristi se
 * postojeći `e.message`, jer je to namerno napisan, actionable tekst - ne sirova greška.
 */
fun friendlyMessage(e: Throwable, fallback: String): String = when (e) {
    is IOException -> "Nema internet konekcije ili server ne odgovara. Proveri vezu i pokušaj ponovo."
    is HttpException -> when (e.code()) {
        401, 403 -> "Nemaš dozvolu za ovu akciju - probaj da se ponovo prijaviš."
        404 -> "Traženi podatak nije pronađen."
        in 500..599 -> "Server trenutno ima problema. Pokušaj ponovo za koji trenutak."
        else -> e.message ?: fallback
    }
    else -> e.message ?: fallback
}
