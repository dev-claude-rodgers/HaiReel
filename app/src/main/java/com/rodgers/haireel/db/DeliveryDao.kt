package com.rodgers.haireel.db

import androidx.room.*

@Dao
interface DeliveryDao {

    @Query("SELECT * FROM deliveries WHERE group_id = :groupId ORDER BY sort_order ASC")
    suspend fun getByGroup(groupId: String): List<DeliveryEntity>

    @Query("SELECT * FROM deliveries ORDER BY group_id, sort_order ASC")
    suspend fun getAll(): List<DeliveryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeliveryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeliveryEntity)

    @Query("DELETE FROM deliveries WHERE group_id = :groupId")
    suspend fun deleteByGroup(groupId: String)

    @Query("DELETE FROM deliveries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM deliveries")
    suspend fun count(): Int
}
