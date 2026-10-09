package dev.milinko.movielens.util

import dev.milinko.movielens.BuildConfig

/**
 * Developer UI, per build type (app/build.gradle.kts):
 * - debug: the backend URL picker (Render/local/ngrok) is visible, for development on your own device;
 * - beta and release: hidden, the app stays on the default (Render) URL from UrlStorage.kt.
 *
 * The tester APK is built as `beta` (debug signing, no dev UI), so nothing has to be switched by
 * hand before a build.
 */
object DevSettings {
    val SHOW_BACKEND_URL_SETTINGS: Boolean = BuildConfig.SHOW_DEV_SETTINGS

    // Render keeps ENABLE_LOCAL_EMBEDDING off (the local MiniLM model does not fit the free plan), so
    // engine=local would always return an empty list there; the switch is hidden by default.
    val SHOW_LOCAL_EMBEDDING_ENGINE_OPTION: Boolean = BuildConfig.SHOW_LOCAL_EMBEDDING_OPTION
}
