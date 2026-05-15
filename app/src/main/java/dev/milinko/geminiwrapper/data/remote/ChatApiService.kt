package dev.milinko.geminiwrapper.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ChatApiService {
    @POST("chat/text")
    suspend fun sendMessage(@Body message: String): String

    @Multipart
    @POST("chat/vision")
    suspend fun sendMessageWithImage(
        @Part("message") message: RequestBody,
        @Part image: MultipartBody.Part
    ): String
}
