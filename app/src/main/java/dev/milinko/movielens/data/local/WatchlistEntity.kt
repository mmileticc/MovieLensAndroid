package dev.milinko.movielens.data.local

import androidx.room.Entity

/**
 * Lokalni (Room) keš watchlist-e - offline-first: UI čita odavde (Flow, instant), mreža samo
 * osvežava ovu tabelu u pozadini (videti UserRepositoryImpl). Composite PK (userId, tmdbId) da bi
 * više naloga na istom uređaju imalo odvojene liste (npr. posle sign-out/sign-in kao drugi nalog).
 */
@Entity(tableName = "watchlist_items", primaryKeys = ["userId", "tmdbId"])
data class WatchlistEntity(
    val userId: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val addedAt: Long,
    val priority: Int = 0,
    // Filteri Biblioteke (UX dorada oktobar 2026). Žanrovi kao JSON (Gson) - mala lista, ne
    // vredi posebna tabela/TypeConverter samo za prikaz i filtriranje.
    val genresJson: String? = null,
    val releaseYear: Int? = null,
    val voteAverage: Double? = null
)
