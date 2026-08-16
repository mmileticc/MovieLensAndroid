package dev.milinko.movielens.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GenericCacheDao {

    @Query("SELECT * FROM generic_cache WHERE cacheKey = :key")
    fun observe(key: String): Flow<GenericCacheEntity?>

    @Query("SELECT * FROM generic_cache WHERE cacheKey = :key")
    suspend fun get(key: String): GenericCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: GenericCacheEntity)

    @Query("DELETE FROM generic_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)
}
