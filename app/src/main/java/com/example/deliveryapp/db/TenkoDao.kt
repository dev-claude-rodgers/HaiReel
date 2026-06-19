package com.rodgers.routist.db

import androidx.room.*
import com.rodgers.routist.model.TenkoRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TenkoDao {
    @Query("""
        SELECT * FROM tenko_records
        WHERE date LIKE :monthPrefix || '%'
          AND (:assignmentId = '' OR assignmentId = :assignmentId)
        ORDER BY date ASC
    """)
    fun getByMonthFlow(monthPrefix: String, assignmentId: String = ""): Flow<List<TenkoRecord>>

    @Query("""
        SELECT * FROM tenko_records
        WHERE date = :date
          AND (:assignmentId = '' OR assignmentId = :assignmentId)
        LIMIT 1
    """)
    suspend fun getByDate(date: String, assignmentId: String = ""): TenkoRecord?

    @Query("""
        SELECT * FROM tenko_records
        WHERE date LIKE :monthPrefix || '%'
          AND (:assignmentId = '' OR assignmentId = :assignmentId)
        ORDER BY date ASC
    """)
    suspend fun getByMonth(monthPrefix: String, assignmentId: String = ""): List<TenkoRecord>

    // 全案件（サマリー・エクスポート用）
    @Query("SELECT * FROM tenko_records WHERE date LIKE :monthPrefix || '%' ORDER BY date ASC")
    suspend fun getAllByMonth(monthPrefix: String): List<TenkoRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TenkoRecord)

    @Update
    suspend fun update(record: TenkoRecord)

    @Delete
    suspend fun delete(record: TenkoRecord)

    @Query("DELETE FROM tenko_records WHERE date LIKE :monthPrefix || '%'")
    suspend fun deleteByMonth(monthPrefix: String)
}
