package dev.milinko.movielens.util

/**
 * Dorada (1. septembar 2026, priprema APK-a za spoljne testere) - ručni prekidač za developer-only
 * UI, NAMERNO odvojen od `BuildConfig.DEBUG`. Razlog: `debug` build tip se koristi za slanje APK-a
 * testerima jer ga Android Studio automatski potpisuje generičkim debug ključem (instalira se bez
 * podešavanja keystore-a) - da smo UI vezali za `BuildConfig.DEBUG`, i dalje bi bio vidljiv u tom
 * istom APK-u koji ide testerima. Umesto toga, ovaj flag se ručno prebacuje pre svakog builda:
 *
 * - `true`  dok se razvija/testira LOKALNO (Android Studio, sopstveni uređaj) - vidi se dugme za
 *           promenu backend rute (korisno da brzo prebacuješ Render/lokalno/ngrok itd.)
 * - `false` pre nego što se pravi APK za slanje spoljnim testerima - dugme je sakriveno, testeri
 *           ostaju na difoltnoj (Render) ruti iz UrlStorage.kt bez mogućnosti da je promene.
 */
object DevSettings {
    const val SHOW_BACKEND_URL_SETTINGS = true

    // Dorada (1. septembar 2026, isti razlog kao SHOW_BACKEND_URL_SETTINGS iznad) - Render (hostovan
    // backend, difoltna ruta za testere) namerno drži ENABLE_LOCAL_EMBEDDING isključeno (videti
    // Application.kt na backend-u - lokalni MiniLM model bi rizikovao OutOfMemoryError na free
    // planu). To znači da `engine=local` na Render-u UVEK vraća praznu listu - nema smisla da
    // testeri uopšte vide Gemini/Lokalno prekidač na Search ekranu kad druga opcija nikad ne radi.
    // `false` dok se testira na Render-u (testeri, ovaj default build); postaviti na `true` samo
    // kad se radi lokalno protiv backend-a koji STVARNO ima ENABLE_LOCAL_EMBEDDING=true.
    const val SHOW_LOCAL_EMBEDDING_ENGINE_OPTION = false
}
