package dev.milinko.movielens.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.milinko.movielens.BuildConfig
import dev.milinko.movielens.data.remote.vision.VisionApi
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.AuthApi
import dev.milinko.movielens.data.remote.interceptor.AuthInterceptor
import dev.milinko.movielens.data.remote.interceptor.DynamicBaseUrlInterceptor
import dev.milinko.movielens.data.remote.interceptor.TokenAuthenticator
import dev.milinko.movielens.data.remote.movie.MovieApiService
import dev.milinko.movielens.data.remote.user.UserApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides OkHttp, Retrofit and the API interfaces: the main client (auth header, token refresh,
 * dynamic base URL, Accept-Language) and a separate bare client used only to refresh the JWT.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        // BODY logs whole requests and responses (including image uploads), which is noticeable
        // overhead, so it is used only in debug builds; release builds do not log at all.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            // The app language; the backend uses it to return error messages and texts (e.g. the
            // Movie Night reasons) in Serbian or English (see Localization.kt on the backend).
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept-Language", dev.milinko.movielens.util.AppLocale.currentTag())
                        .build()
                )
            }
            .addInterceptor(loggingInterceptor)
            // The backend JWT lasts 24h; when it expires this refreshes it via Firebase and retries
            // the request instead of failing with 401 (see TokenAuthenticator.kt).
            .authenticator(tokenAuthenticator)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, urlStorage: UrlStorage): Retrofit {
        return Retrofit.Builder()
            .baseUrl(urlStorage.getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create()) // For plain-text responses
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- Separate bare client used ONLY by TokenAuthenticator (JWT refresh) ---
    // It must not have AuthInterceptor (the exchange does not need our JWT) or TokenAuthenticator
    // (a 401 on the refresh call would loop forever). Injecting the main OkHttpClient/Retrofit here
    // would also create a Dagger cycle (OkHttpClient -> TokenAuthenticator -> AuthApi -> Retrofit
    // -> OkHttpClient).

    @Provides
    @Singleton
    @AuthRefresh
    fun provideAuthRefreshOkHttpClient(dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @AuthRefresh
    fun provideAuthRefreshRetrofit(@AuthRefresh okHttpClient: OkHttpClient, urlStorage: UrlStorage): Retrofit {
        return Retrofit.Builder()
            .baseUrl(urlStorage.getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @AuthRefresh
    fun provideAuthRefreshApi(@AuthRefresh retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }


    @Provides
    @Singleton
    fun provideVisionApi(retrofit: Retrofit): VisionApi {
        return retrofit.create(VisionApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMovieApiService(retrofit: Retrofit): MovieApiService {
        return retrofit.create(MovieApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApiService(retrofit: Retrofit): UserApiService {
        return retrofit.create(UserApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }
}
