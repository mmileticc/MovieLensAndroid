package dev.milinko.movielens.di

import javax.inject.Qualifier

/**
 * Označava OkHttpClient/Retrofit/AuthApi instancu namenjenu SAMO za osvežavanje backend JWT-a
 * (TokenAuthenticator). Mora biti odvojena od glavnog OkHttpClient-a (koji koristi AuthInterceptor
 * + TokenAuthenticator) da bi se izbegla beskonačna petlja i Dagger ciklus - videti komentar u
 * TokenAuthenticator.kt.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRefresh
