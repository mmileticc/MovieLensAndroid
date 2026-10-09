package dev.milinko.movielens.util

/**
 * A temporary problem with an external service (e.g. Gemini overloaded, HTTP 503): not the user's
 * fault, just "wait and try again". Repositories (e.g. VisionRepository) throw it on a 503 from the
 * backend; ViewModels catch it separately (before the generic `catch (e: Exception)`) and set
 * `isSoftError = true`, so the UI (SearchScreen) shows a calmer card instead of a red error.
 * Counterpart of the backend's `GeminiOverloadedException` (GeminiService.kt).
 */
class TransientServiceException(message: String) : Exception(message)
