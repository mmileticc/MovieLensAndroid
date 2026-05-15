package dev.milinko.geminiwrapper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.milinko.geminiwrapper.data.remote.ChatApiService
import dev.milinko.geminiwrapper.data.pref.UrlStorage
import dev.milinko.geminiwrapper.data.remote.interceptor.DynamicBaseUrlInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

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
}
