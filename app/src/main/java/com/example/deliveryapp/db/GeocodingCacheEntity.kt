package com.rodgers.routist.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geocoding_cache")
data class GeocodingCacheEntity(
    @PrimaryKey val address: String,
    val lat: Double,
    val lng: Double,
    val cachedAt: Long = System.currentTimeMillis()
)
