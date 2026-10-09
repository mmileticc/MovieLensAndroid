package dev.milinko.movielens.data.local

import androidx.room.Entity

/** Cached watched/rated movie (same pattern as [WatchlistEntity]). */
@Entity(tableName = "history_items", primaryKeys = ["userId", "tmdbId"])
data class HistoryEntity(
    val userId: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val userRating: Double,
    // Optional note attached to the rating (see HistoryItem.review).
    val review: String? = null,
    val watchedAt: Long,
    val duration: Int = 0,
    // For the Library filters (see WatchlistEntity).
    val genresJson: String? = null,
    val releaseYear: Int? = null,
    val voteAverage: Double? = null
)
