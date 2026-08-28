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

    // 完了済みのみ削除。進行中ルートに残る同一配達先（未完了）は誤って消さないよう保護する
    @Query("DELETE FROM deliveries WHERE TRIM(COALESCE(name,'')) = :name AND TRIM(address) = :address AND is_completed = 1")
    suspend fun deleteByNameAndAddress(name: String, address: String)

    @Query("DELETE FROM deliveries")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM deliveries")
    suspend fun count(): Int
}
