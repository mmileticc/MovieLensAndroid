package dev.milinko.geminiwrapper.data.remote.interceptor

import dev.milinko.geminiwrapper.data.pref.UrlStorage
import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicBaseUrlInterceptor @Inject constructor(
    private val urlStorage: UrlStorage
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val newUrl = urlStorage.getBaseUrl().toHttpUrlOrNull()
        
        if (newUrl != null) {
            val newRequestBuilder = request.newBuilder()
                .url(
                    request.url.newBuilder()
                        .scheme(newUrl.scheme)
                        .host(newUrl.host)
                        .port(newUrl.port)
                        .build()
                )
            request = newRequestBuilder.build()
        }
        
        return chain.proceed(request)
    }
}
