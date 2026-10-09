package dev.milinko.movielens.util

import androidx.annotation.StringRes
import dev.milinko.movielens.R
import retrofit2.HttpException
import java.io.IOException

/**
 * Turns any exception into a message that is safe to show to the user; raw technical text
 * (timeouts, "Unable to resolve host", "HTTP 409 Conflict", JSON errors...) never reaches the UI.
 *
 * - Network errors and known HTTP status codes get a localized message.
 * - `e.message` is passed through only for exception types the app throws itself with a
 *   deliberately written text (IllegalStateException, TransientServiceException), e.g.
 *   `UserRepository.parseErrorMessage` ("Username is already taken").
 * - Everything else falls back to the caller's [fallback] text.
 *
 * Repositories therefore throw a real `HttpException(response)` on failed responses, never a plain
 * Exception with a message.
 */
fun friendlyMessage(e: Throwable, fallback: String): String = when (e) {
    // Texts come from resources via AppLocale, so the language follows the choice in Settings.
    is IOException -> AppLocale.string(R.string.error_network)
    is HttpException -> when (e.code()) {
        401, 403 -> AppLocale.string(R.string.error_forbidden)
        404 -> AppLocale.string(R.string.error_not_found)
        429 -> AppLocale.string(R.string.error_too_many_requests)
        in 500..599 -> AppLocale.string(R.string.error_server)
        // Not `e.message`: for less common codes (400/409/422...) Retrofit's message is still
        // technical (e.g. "HTTP 409 Conflict"); the caller's fallback is always more meaningful.
        else -> fallback
    }
    // These types carry only messages the app writes itself (see UserRepository.parseErrorMessage,
    // VisionRepository, TransientServiceException.kt), so passing e.message on is safe.
    is IllegalStateException -> e.message ?: fallback
    is TransientServiceException -> e.message ?: fallback
    else -> fallback
}

/** Same as above, with a localized fallback message from resources instead of a fixed text. */
fun friendlyMessage(e: Throwable, @StringRes fallbackRes: Int): String =
    friendlyMessage(e, AppLocale.string(fallbackRes))
