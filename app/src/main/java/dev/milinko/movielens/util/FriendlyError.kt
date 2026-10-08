package dev.milinko.movielens.util

import androidx.annotation.StringRes
import dev.milinko.movielens.R
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
 * Dorada (2. septembar 2026, korisnički zahtev - "nijedna sirova poruka ne sme da postoji nigde,
 * sve mora da bude lepo prikazano") - sveobuhvatan audit je otkrio da je originalni `else ->
 * e.message ?: fallback` (ispod, i za HttpException nepoznate kodove i za sve ostale tipove)
 * predstavljao "rupu": bio je NAMERNO tu za slučajeve gde repository sloj SAM baca izuzetak sa već
 * smislenim, ručno napisanim tekstom (npr. `UserRepository.parseErrorMessage`, "Korisničko ime je
 * već zauzeto") - ALI ništa nije sprečavalo repository slojeve da kroz isti mehanizam (generički
 * `Exception(...)`) provuku i sirov/tehnički tekst (pronađeno: `MovieRepository`/`UserRepository`
 * su na desetinama mesta bacali generički `Exception("X failed: <kod>")` - taj tekst bi ovde
 * prošao NEPROMENJEN pravo do korisnika). Popravljeno na dva fronta: (1) svi ti pozivi u
 * `MovieRepository.kt`/`UserRepository.kt` sad bacaju pravi `HttpException(response)` umesto
 * generičkog `Exception`, pa gornja HTTP-kod grana ispod stvarno hvata te slučajeve; (2) OVA
 * funkcija sad propušta `e.message` SAMO za tipove za koje je pouzdano poznato da ih app sam baca
 * sa namerno napisanim tekstom (`IllegalStateException`, `TransientServiceException`) - svaki
 * DRUGI, neočekivan tip izuzetka (JSON parsiranje, NPE, biblioteke koje ne kontrolišemo...) sad
 * pada na bezbedan `fallback` tekst koji poziva prosleđuje, nikad na sirovu poruku izuzetka.
 */
fun friendlyMessage(e: Throwable, fallback: String): String = when (e) {
    // Tekstovi iz resursa preko AppLocale - jezik prati izbor u Podešavanjima (srpski/engleski).
    is IOException -> AppLocale.string(R.string.error_network)
    is HttpException -> when (e.code()) {
        401, 403 -> AppLocale.string(R.string.error_forbidden)
        404 -> AppLocale.string(R.string.error_not_found)
        429 -> AppLocale.string(R.string.error_too_many_requests)
        in 500..599 -> AppLocale.string(R.string.error_server)
        // Namerno NE `e.message` - za manje uobičajene kodove (400/409/422...) Retrofit-ov
        // HttpException.message() vraća kratak ali i dalje tehnički tekst (npr. "HTTP 409
        // Conflict") - fallback koji poziva prosleđuje je uvek smisleniji.
        else -> fallback
    }
    // Ovi tipovi se koriste ISKLJUČIVO za poruke koje app sam namerno piše (videti
    // `UserRepository.parseErrorMessage`, `VisionRepository`, `TransientServiceException.kt`) -
    // bezbedno je proslediti `e.message` dalje, to NIJE sirova/tehnička greška.
    is IllegalStateException -> e.message ?: fallback
    is TransientServiceException -> e.message ?: fallback
    else -> fallback
}

/** Isto kao gore, sa rezervnom porukom iz resursa (lokalizovanom) umesto fiksnog teksta. */
fun friendlyMessage(e: Throwable, @StringRes fallbackRes: Int): String =
    friendlyMessage(e, AppLocale.string(fallbackRes))
