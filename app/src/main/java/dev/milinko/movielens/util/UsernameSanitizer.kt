package dev.milinko.movielens.util

import java.text.Normalizer

/**
 * Ista sanitizacija kao `sanitizeUsername` na backendu (`db/UsersTable.kt`) - namerno dupliran
 * algoritam (odvojeni Gradle projekti, nema deljenog Kotlin modula) da bi default korisničko ime
 * VEĆ bilo validno po `USERNAME_REGEX`-u (`^[A-Za-z0-9_.]{3,30}$`) pre nego što stigne do backenda.
 *
 * Rešava korisnički prijavljen bag: kad korisnik nikad nije sam postavio username, polje se
 * popunjavalo sirovim Google imenom ("Miloš Miletić" - razmak i č/š/ć nisu dozvoljeni karakteri),
 * pa je SVAKA izmena profila (čak i kad korisnik menja samo sliku ili opis, ne dodiruje username
 * polje) padala na regex proveru na backendu. Ovde se taj default pretvara u "milos_miletic" -
 * malim slovima, razmak -> donja crta, srpska latinična slova bez dijakritike (č/ć/š/ž se skidaju
 * preko NFD dekompozicije, đ ručno jer se NFD ne dekomponuje na d + kvačicu).
 *
 * Idempotentno je za već validne username-e (npr. korisnik je ranije sam postavio "milinko_m") -
 * transformacije ne menjaju ništa što već zadovoljava regex, pa je bezbedno pozvati i na
 * vrednost učitanu sa backenda, ne samo na sirovo Google ime.
 */
fun sanitizeUsername(raw: String): String {
    var s = raw.trim()
        .replace("đ", "dj")
        .replace("Đ", "Dj")
    s = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    s = s.lowercase()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^a-z0-9_.]"), "")
        .replace(Regex("_{2,}"), "_")
        .trim('_', '.')
    if (s.length > 30) {
        s = s.take(30).trimEnd('_', '.')
    }
    return s
}
