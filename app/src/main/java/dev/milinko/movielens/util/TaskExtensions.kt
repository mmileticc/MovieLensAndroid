package dev.milinko.movielens.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspends until a Google Play Services [Task] completes and returns its result (or throws its error). */
suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

