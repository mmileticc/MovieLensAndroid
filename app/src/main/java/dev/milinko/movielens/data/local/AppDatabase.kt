package dev.milinko.movielens.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline keš baza - namerno SAMO watchlist/istorija (podaci "koje ja posedujem", vidi
 * MOVIELENS_ROADMAP.md diskusiju o offline obimu). Pretraga/trending/bioskopski termini se
 * namerno NE kešuju ovde - nemaju smisla offline (live upit ka TMDB/skrapovanim terminima).
 */
@Database(
    entities = [WatchlistEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun historyDao(): HistoryDao
}
