package dev.milinko.movielens.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/** Request body: Firebase ID token to exchange for a backend session. */
data class AuthFirebaseRequest(val idToken: String)
/** User returned by the sign-in exchange. */
data class UserDto(val id: String, val firebaseUid: String, val username: String?, val email: String?, val avatarUrl: String?)
/** Backend session JWT plus the user. */
data class AuthExchangeResponse(val appToken: String?, val user: UserDto?)

/** Exchanges a Firebase ID token for the backend's own session JWT. */
interface AuthApi {
    @POST("/api/auth/firebase")
    suspend fun exchangeFirebaseToken(@Body body: AuthFirebaseRequest): AuthExchangeResponse
}

