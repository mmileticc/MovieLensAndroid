package dev.milinko.movielens.util

import kotlinx.coroutines.CancellationException

/**
 * Wraps a suspend network call into a [Result] WITHOUT swallowing [CancellationException].
 *
 * `catch (e: Exception) { Result.failure(e) }` alone catches cancellation too - CancellationException
 * is a subtype of Exception in Kotlin - which breaks structured concurrency: when a coroutine is
 * cancelled (e.g. the debounced search job gets cancelled because the user typed another character,
 * or the ViewModel/screen is torn down mid-request), the cancellation gets reported as a real error
 * instead of silently unwinding. That's exactly what caused the "Greška" card to flash while typing
 * fast in Search - each keystroke cancels the in-flight request for the previous query, and that
 * cancellation was being shown to the user as a failed search.
 *
 * Every repository method that hits the network should go through this instead of writing its own
 * try/catch(Exception).
 */
suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
