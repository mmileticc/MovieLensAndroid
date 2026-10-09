package dev.milinko.movielens.data.local

import androidx.room.Entity

/**
 * Cached watchlist entry. Offline-first: the UI reads from here (Flow, instant) and the network only
 * refreshes the table in the background (see UserRepositoryImpl). The composite key (userId, tmdbId)
 * keeps the lists of different accounts on one device apart.
 */
@Entity(tableName = "watchlist_items", primaryKeys = ["userId", "tmdbId"])
data class WatchlistEntity(
    val userId: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val addedAt: Long,
    val priority: Int = 0,
    // For the Library filters. Genres as JSON (Gson): a small list that does not justify a
    // separate table or TypeConverter.
    val genresJson: String? = null,
    val releaseYear: Int? = null,
    val voteAverage: Double? = null
)
