package dev.milinko.movielens.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Cached watched movies, per user. */
@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_items WHERE userId = :userId ORDER BY watchedAt DESC")
    fun observeHistory(userId: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryEntity)

    @Query("DELETE FROM history_items WHERE userId = :userId")
    suspend fun clearForUser(userId: String)

    @Query("DELETE FROM history_items WHERE userId = :userId AND tmdbId = :tmdbId")
    suspend fun delete(userId: String, tmdbId: Int)

    @Transaction
    suspend fun replaceAll(userId: String, items: List<HistoryEntity>) {
        clearForUser(userId)
        insertAll(items)
    }
}
