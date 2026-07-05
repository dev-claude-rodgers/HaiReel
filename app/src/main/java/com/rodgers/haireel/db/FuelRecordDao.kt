package com.rodgers.haireel.db

import androidx.room.*
import com.rodgers.haireel.model.FuelRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelRecordDao {

    @Query("SELECT * FROM fuel_records ORDER BY date ASC, id ASC")
    fun getAllFlow(): Flow<List<FuelRecord>>

    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY date ASC, id ASC")
    fun getByVehicleFlow(vehicleId: Long): Flow<List<FuelRecord>>

    @Query("SELECT * FROM fuel_records ORDER BY date ASC, id ASC")
    suspend fun getAll(): List<FuelRecord>

    @Query("DELETE FROM fuel_records")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(record: FuelRecord)

    @Delete
    suspend fun delete(record: FuelRecord)
}
