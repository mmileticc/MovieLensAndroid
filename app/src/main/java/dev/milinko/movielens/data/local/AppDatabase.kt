package dev.milinko.movielens.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline cache (Room). Watchlist and history have their own tables; other "owned" data (profile,
 * friends, requests, activity feed, taste profile, Movie Night suggestions, friend profiles) is
 * stored as JSON in [GenericCacheEntity]. Search results and cinema data are not cached because
 * they change often.
 *
 * Everything here can be refetched from the server, so schema changes use a destructive migration
 * (see DatabaseModule) instead of Migration classes.
 */
@Database(
    entities = [WatchlistEntity::class, HistoryEntity::class, GenericCacheEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun historyDao(): HistoryDao
    abstract fun genericCacheDao(): GenericCacheDao
}
