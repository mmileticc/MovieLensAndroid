package dev.milinko.movielens.data.pref

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Persists the backend session token and user info received after exchanging
 * a Firebase ID token with the Ktor backend (POST /api/auth/firebase).
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APP_TOKEN = "app_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_FIREBASE_UID = "firebase_uid"
    }

    fun saveSession(appToken: String, userId: String, firebaseUid: String, username: String?, email: String?, avatarUrl: String?) {
        prefs.edit {
            putString(KEY_APP_TOKEN, appToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_FIREBASE_UID, firebaseUid)
                .putString(KEY_USERNAME, username)
                .putString(KEY_EMAIL, email)
                .putString(KEY_AVATAR_URL, avatarUrl)
        }
    }

    fun getAppToken(): String? = prefs.getString(KEY_APP_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getFirebaseUid(): String? = prefs.getString(KEY_FIREBASE_UID, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)
    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    fun isSessionValid(): Boolean = getAppToken() != null

    fun clearSession() {
        prefs.edit { clear() }
    }
}

