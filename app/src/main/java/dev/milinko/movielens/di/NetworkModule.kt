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
        // BODY loguje ceo request/response (uključujući vision/image pozive) na svaki poziv -
        // primetan overhead (Logcat I/O + serijalizacija) čak i na debug buildu. Ostaje BODY samo
        // u debugu radi lakšeg razvoja, u release-u se logging potpuno gasi.
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            // Backend JWT traje 24h - kad istekne, ovo automatski osveži token (preko Firebase-a) i
            // ponovi zahtev umesto da zaštićeni pozivi tiho puknu 401 dok se korisnik ručno ne
            // odjavi/prijavi. Videti TokenAuthenticator.kt.
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
            .addConverterFactory(ScalarsConverterFactory.create()) // Za običan tekst
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- Odvojen, "goli" klijent SAMO za TokenAuthenticator (osvežavanje JWT-a) ---
    // Mora biti bez AuthInterceptor-a (nepotreban - exchangeFirebaseToken ne treba naš JWT) i bez
    // TokenAuthenticator-a (kritično - inače bi 401 na refresh pozivu izazvao beskonačnu petlju
    // koja opet zove sebe). Ako bismo ovde ubrizgali glavni OkHttpClient/Retrofit, dobili bismo
    // Dagger ciklus (OkHttpClient -> TokenAuthenticator -> AuthApi -> Retrofit -> OkHttpClient).

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
