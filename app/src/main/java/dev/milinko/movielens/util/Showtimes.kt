package dev.milinko.movielens.util

import dev.milinko.movielens.data.remote.movie.Showtime
import dev.milinko.movielens.data.remote.movie.ShowtimeSlot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Helpers for the showtimes on the movie detail. Days are ISO strings ("2026-10-15") in Belgrade
// time, where the cinemas publish their times; SimpleDateFormat instead of java.time because
// minSdk is 24 (java.time without desugaring needs API 26).

private val CINEMA_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Europe/Belgrade")

private fun isoDayFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = CINEMA_TIME_ZONE }

/** Today as a calendar day in Belgrade. */
fun cinemaToday(now: Date = Date()): String = isoDayFormat().format(now)

/** The day after [day] ("2026-10-31" -> "2026-11-01"). */
fun nextDay(day: String): String {
    val format = isoDayFormat()
    val calendar = Calendar.getInstance(CINEMA_TIME_ZONE).apply {
        time = format.parse(day) ?: return day
        add(Calendar.DAY_OF_MONTH, 1)
    }
    return format.format(calendar.time)
}

/** The days with at least one screening, in order. */
fun showtimeDays(cinemas: List<Showtime>): List<String> =
    cinemas.flatMap { cinema -> cinema.times.orEmpty().map { it.startsAt.take(10) } }.distinct().sorted()

/** The screenings of one cinema on [day]. */
fun timesOnDay(cinema: Showtime, day: String?): List<ShowtimeSlot> =
    if (day == null) emptyList() else cinema.times.orEmpty().filter { it.startsAt.startsWith(day) }

/** Cinemas with screenings on [day] first, otherwise in the server's order (distance). */
fun cinemasForDay(cinemas: List<Showtime>, day: String?): List<Showtime> {
    if (day == null) return cinemas
    val (withTimes, without) = cinemas.partition { timesOnDay(it, day).isNotEmpty() }
    return withTimes + without
}

/** "19:00" from "2026-10-15T19:00". */
fun ShowtimeSlot.time(): String = startsAt.drop(11).take(5)

/** True for a bare hall number (Arena, Roda), which the app shows as "Hall 2". */
fun isHallNumber(hall: String?): Boolean = !hall.isNullOrBlank() && hall.trim().all { it.isDigit() }

/** Only real web links (the data comes from scraped sites), otherwise null. */
fun safeWebLink(url: String?): String? =
    url?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

/** Short weekday and date for a day chip, e.g. "pon 19.10." / "Mon 19.10.". */
fun shortDayLabel(day: String, locale: Locale): String {
    val date = isoDayFormat().parse(day) ?: return day
    return SimpleDateFormat("EEE d.M.", locale).apply { timeZone = CINEMA_TIME_ZONE }.format(date)
}
