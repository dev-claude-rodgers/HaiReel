package com.rodgers.routist.db

import androidx.room.*

@Dao
interface DeliveryGroupDao {

    @Query("SELECT * FROM delivery_groups ORDER BY sort_order ASC")
    suspend fun getAll(): List<DeliveryGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeliveryGroupEntity>)

    @Query("DELETE FROM delivery_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM delivery_groups")
    suspend fun count(): Int
}
