package dev.milinko.movielens.util

import java.text.Normalizer

/** Same rule as `USERNAME_REGEX` on the backend (`db/UsersTable.kt`). */
private val USERNAME_REGEX = Regex("^[A-Za-z0-9_.]{3,30}$")

/** Why a username is invalid (for the message under the field); null from [validateUsername] means valid. */
enum class UsernameProblem { TOO_SHORT, TOO_LONG, INVALID_CHARACTERS }

/**
 * On-device check before sending, so an invalid name is reported under the field right away. The
 * backend still runs the same check (plus uniqueness).
 */
fun validateUsername(username: String): UsernameProblem? = when {
    USERNAME_REGEX.matches(username) -> null
    username.length < 3 -> UsernameProblem.TOO_SHORT
    username.length > 30 -> UsernameProblem.TOO_LONG
    else -> UsernameProblem.INVALID_CHARACTERS
}

/**
 * Turns a raw name (usually the Google display name, e.g. "Miloš Miletić") into a valid default
 * username ("milos_miletic"): lowercase, spaces -> underscores, Serbian Latin letters without
 * diacritics (č/ć/š/ž via NFD decomposition, đ by hand because NFD does not decompose it).
 *
 * Same algorithm as `sanitizeUsername` on the backend (`db/UsersTable.kt`), duplicated because the
 * two Gradle projects share no module. Idempotent for names that are already valid, so it is safe to
 * call on a value loaded from the backend too.
 */
fun sanitizeUsername(raw: String): String {
    var s = raw.trim()
        .replace("đ", "dj")
        .replace("Đ", "Dj")
    s = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    s = s.lowercase()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^a-z0-9_.]"), "")
        .replace(Regex("_{2,}"), "_")
        .trim('_', '.')
    if (s.length > 30) {
        s = s.take(30).trimEnd('_', '.')
    }
    return s
}
