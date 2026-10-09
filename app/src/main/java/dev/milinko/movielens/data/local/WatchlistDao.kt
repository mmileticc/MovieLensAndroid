package dev.milinko.movielens.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Cached watchlist, per user. */
@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist_items WHERE userId = :userId ORDER BY addedAt DESC")
    fun observeWatchlist(userId: String): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WatchlistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist_items WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM watchlist_items WHERE userId = :userId AND tmdbId = :tmdbId")
    suspend fun delete(userId: String, tmdbId: Int)

    /**
     * Full refresh after a successful network load: delete the user's rows and insert the fresh
     * list. Simple and fast enough for real list sizes, and the cache always matches the server.
     */
    @Transaction
    suspend fun replaceAll(userId: String, items: List<WatchlistEntity>) {
        clearForUser(userId)
        insertAll(items)
    }
}
