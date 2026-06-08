package dev.milinko.movielens.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

data class AuthFirebaseRequest(val idToken: String)
data class UserDto(val id: String, val firebaseUid: String, val username: String?, val email: String?, val avatarUrl: String?)
data class AuthExchangeResponse(val appToken: String?, val user: UserDto?)

interface AuthApi {
    @POST("/api/auth/firebase")
    suspend fun exchangeFirebaseToken(@Body body: AuthFirebaseRequest): AuthExchangeResponse
}

