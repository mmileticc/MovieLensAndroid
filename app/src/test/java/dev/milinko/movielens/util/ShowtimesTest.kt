package dev.milinko.movielens.util

import dev.milinko.movielens.data.remote.movie.Showtime
import dev.milinko.movielens.data.remote.movie.ShowtimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ShowtimesTest {

    private fun cinema(name: String, vararg times: String) = Showtime(
        cinemaName = name,
        cinemaLocation = name,
        bookingUrl = "https://example.com",
        times = times.map { ShowtimeSlot(startsAt = it) }
    )

    @Test
    fun `days in order and cinemas with screenings that day first`() {
        val a = cinema("A", "2026-10-16T19:00")
        val b = cinema("B", "2026-10-15T21:00", "2026-10-16T18:00")
        val c = Showtime(cinemaName = "C", cinemaLocation = "C", bookingUrl = "https://example.com")
        assertEquals(listOf("2026-10-15", "2026-10-16"), showtimeDays(listOf(a, b, c)))
        assertEquals(listOf("B", "A", "C"), cinemasForDay(listOf(a, b, c), "2026-10-15").map { it.cinemaName })
        assertEquals(listOf("21:00"), timesOnDay(b, "2026-10-15").map { it.time() })
        assertTrue(timesOnDay(c, "2026-10-15").isEmpty())
    }

    @Test
    fun `next day across the end of a month and a weekday label`() {
        assertEquals("2026-11-01", nextDay("2026-10-31"))
        assertEquals("Mon 19.10.", shortDayLabel("2026-10-19", Locale.ENGLISH))
    }

    @Test
    fun `hall numbers and safe links`() {
        assertTrue(isHallNumber("2"))
        assertFalse(isHallNumber("Sala 6"))
        assertFalse(isHallNumber(null))
        assertEquals("http://ulaznice.tuck.rs/x", safeWebLink("http://ulaznice.tuck.rs/x"))
        assertNull(safeWebLink("javascript:alert(1)"))
        assertNull(safeWebLink(null))
    }
}
