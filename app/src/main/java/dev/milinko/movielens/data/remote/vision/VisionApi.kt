package dev.milinko.movielens.data.remote.vision

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Image recognition: uploads a photo (poster or film still) to `POST /chat/vision`. */
interface VisionApi {
    @Multipart
    @POST("chat/vision")
    suspend fun detect(
        @Part image: MultipartBody.Part
    ): Response<VisionSearchResponse>
}

