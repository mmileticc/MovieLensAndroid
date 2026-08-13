package dev.milinko.movielens.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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
     * Puno osvežavanje keša sa mreže (posle uspešnog GET /watchlist) - obriši sve za korisnika pa
     * upiši sveže. Jednostavnije i dovoljno brzo za realne veličine liste (nema potrebe za
     * diff/merge logikom), garantuje da lokalni keš tačno odražava server posle svakog refresh-a.
     */
    @Transaction
    suspend fun replaceAll(userId: String, items: List<WatchlistEntity>) {
        clearForUser(userId)
        insertAll(items)
    }
}
