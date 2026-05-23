package dev.milinko.movielens.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.milinko.movielens.data.remote.ChatApiService
import dev.milinko.movielens.data.remote.vision.VisionApi
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.interceptor.DynamicBaseUrlInterceptor
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
        dynamicBaseUrlInterceptor: DynamicBaseUrlInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(loggingInterceptor)
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

    @Provides
    @Singleton
    fun provideChatApiService(retrofit: Retrofit): ChatApiService {
        // Logika za Gemini API bi trebala biti ovde ili u interceptoru, 
        // ali za potrebe ovog zadatka pretpostavljamo da retrofit već radi.
        return retrofit.create(ChatApiService::class.java)
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
}
