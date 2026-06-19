package com.rodgers.routist.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeocodingCacheDao {

    @Query("SELECT * FROM geocoding_cache WHERE address = :address LIMIT 1")
    suspend fun get(address: String): GeocodingCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: GeocodingCacheEntity)

    @Query("DELETE FROM geocoding_cache WHERE cachedAt < :threshold")
    suspend fun evictExpired(threshold: Long)
}
