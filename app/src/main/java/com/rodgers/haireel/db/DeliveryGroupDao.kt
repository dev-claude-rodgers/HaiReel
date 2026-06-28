package com.rodgers.haireel.db

import androidx.room.*

@Dao
interface DeliveryGroupDao {

    @Query("SELECT * FROM delivery_groups ORDER BY sort_order ASC")
    suspend fun getAll(): List<DeliveryGroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeliveryGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeliveryGroupEntity)

    @Query("DELETE FROM delivery_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM delivery_groups")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM delivery_groups")
    suspend fun count(): Int
}
