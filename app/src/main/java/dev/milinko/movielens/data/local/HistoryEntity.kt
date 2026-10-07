package dev.milinko.movielens.data.local

import androidx.room.Entity

/** Lokalni (Room) keš istorije (odgledano/ocene) - isti obrazac kao WatchlistEntity. */
@Entity(tableName = "history_items", primaryKeys = ["userId", "tmdbId"])
data class HistoryEntity(
    val userId: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val userRating: Double,
    // Kratak tekstualni komentar uz ocenu (opcion) - videti HistoryItem.review.
    val review: String? = null,
    val watchedAt: Long,
    val duration: Int = 0,
    // Filteri Biblioteke - videti WatchlistEntity.
    val genresJson: String? = null,
    val releaseYear: Int? = null,
    val voteAverage: Double? = null
)
