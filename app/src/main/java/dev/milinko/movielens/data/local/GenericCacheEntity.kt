package dev.milinko.movielens.data.local

import androidx.room.Entity

/**
 * Generic JSON cache for screens whose data is always read and written as a whole (own profile,
 * friends, pending requests, activity feed, taste profile, Movie Night suggestions, friend
 * profiles). One table with a JSON payload per key instead of a table per shape.
 *
 * `cacheKey` encodes both the data type and its owner (e.g. "friends:<userId>",
 * "movienight:<userId>:<friendId>"), so different accounts on one device never share entries.
 */
@Entity(tableName = "generic_cache", primaryKeys = ["cacheKey"])
data class GenericCacheEntity(
    val cacheKey: String,
    val jsonPayload: String,
    val updatedAt: Long = System.currentTimeMillis()
)
