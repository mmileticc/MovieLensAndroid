package dev.milinko.movielens.util

import kotlinx.coroutines.CancellationException

/**
 * Wraps a suspend network call into a [Result] WITHOUT swallowing [CancellationException].
 *
 * A plain `catch (e: Exception) { Result.failure(e) }` also catches cancellation (CancellationException
 * is an Exception), which breaks structured concurrency: when a coroutine is cancelled (e.g. the
 * debounced search job because the user typed another character, or the screen is closed
 * mid-request), the cancellation would be reported as a real error. In Search this made the error
 * card flash while typing fast.
 *
 * Every repository method that hits the network should go through this instead of its own
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
