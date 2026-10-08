package dev.milinko.movielens.util

import dev.milinko.movielens.BuildConfig

/**
 * Developerski UI, po build tipu (app/build.gradle.kts):
 * - debug: vidi se izbor backend rute (Render/lokalno/ngrok) - za razvoj na sopstvenom uređaju;
 * - beta i release: sakriveno, aplikacija ostaje na podrazumevanoj (Render) ruti iz UrlStorage.kt.
 *
 * APK za testere se pravi kao `beta` build (debug potpis, bez dev UI-ja), pa ništa ne treba
 * ručno prebacivati pre builda.
 */
object DevSettings {
    val SHOW_BACKEND_URL_SETTINGS: Boolean = BuildConfig.SHOW_DEV_SETTINGS

    // Render drži ENABLE_LOCAL_EMBEDDING isključeno (lokalni MiniLM model ne staje u free plan), pa
    // bi engine=local tamo uvek vraćao praznu listu - prekidač se zato podrazumevano ne prikazuje.
    val SHOW_LOCAL_EMBEDDING_ENGINE_OPTION: Boolean = BuildConfig.SHOW_LOCAL_EMBEDDING_OPTION
}
