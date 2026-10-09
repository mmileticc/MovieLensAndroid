package dev.milinko.movielens.di

import javax.inject.Qualifier

/**
 * Marks the OkHttpClient/Retrofit/AuthApi instances used ONLY to refresh the backend JWT
 * (TokenAuthenticator). They must be separate from the main client to avoid an endless 401 loop
 * and a Dagger cycle (see TokenAuthenticator.kt).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRefresh
