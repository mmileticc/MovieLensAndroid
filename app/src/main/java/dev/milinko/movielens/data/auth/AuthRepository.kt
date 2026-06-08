package dev.milinko.movielens.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dev.milinko.movielens.util.awaitTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth) {

    val currentFirebaseUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    suspend fun signInWithGoogleIdToken(idToken: String) = withContext(Dispatchers.IO) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential).awaitTask()
        firebaseAuth.currentUser ?: throw IllegalStateException("No user after sign-in")
    }

    suspend fun getFirebaseIdToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext null
        val tokenResult = user.getIdToken(forceRefresh).awaitTask()
        tokenResult.token
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    fun isSignedIn(): Boolean = firebaseAuth.currentUser != null
    fun currentUserUid(): String? = firebaseAuth.currentUser?.uid
}
