package dev.milinko.movielens.util

/**
 * Dorada (2. septembar 2026, korisnički zahtev - "poruka 'prepoznavanje slike je trenutno
 * preopterećeno' me para oči, deluje kao greška koju je korisnik skrivio, hoću da bude blaže
 * prikazana"). Do sad su SVE greške (prava mrežna/serverska greška ILI privremeno preopterećenje
 * spoljnog servisa poput Gemini-ja) završavale kao isti `IllegalStateException`, pa ih UI (crveni
 * "Greška" karton) nije mogao vizuelno razlikovati - korisnik nije ništa pokvario, servis je samo
 * privremeno zauzet, ali je poruka izgledala isto ozbiljno kao prava greška.
 *
 * Ovaj tip je marker za baš tu kategoriju - "nije tvoja greška, samo sačekaj i pokušaj ponovo" -
 * analogno backend `GeminiOverloadedException` (GeminiService.kt). Repository slojevi (npr.
 * VisionRepository) ga bacaju kad prepoznaju HTTP 503 sa backend-a; ViewModel slojevi ga hvataju
 * POSEBNO (pre generičkog `catch (e: Exception)`) i postavljaju `isSoftError = true` u uiState,
 * da UI (SearchScreen) može da prikaže smireniji, manje alarmantan karton umesto crvene "Greške".
 */
class TransientServiceException(message: String) : Exception(message)
