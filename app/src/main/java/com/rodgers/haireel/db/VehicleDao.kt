package com.rodgers.haireel.db

import androidx.room.*
import com.rodgers.haireel.model.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    fun getAllFlow(): Flow<List<Vehicle>>

    @Upsert
    suspend fun upsert(vehicle: Vehicle): Long

    @Delete
    suspend fun delete(vehicle: Vehicle)
}
