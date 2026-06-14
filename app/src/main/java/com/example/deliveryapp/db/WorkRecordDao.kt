package com.rodgers.routist.db

import androidx.room.*
import com.rodgers.routist.model.WorkRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkRecordDao {

    @Query("SELECT * FROM work_records ORDER BY date DESC")
    fun allRecords(): Flow<List<WorkRecord>>

    // assignmentId = "" のとき全件、それ以外は案件フィルタ
    @Query("SELECT * FROM work_records WHERE date LIKE :yearMonth || '%' AND (:assignmentId = '' OR assignmentId = :assignmentId) ORDER BY date ASC")
    fun recordsForMonthFlow(yearMonth: String, assignmentId: String = ""): Flow<List<WorkRecord>>

    @Query("SELECT * FROM work_records WHERE date >= :startDate AND date <= :endDate AND (:assignmentId = '' OR assignmentId = :assignmentId) ORDER BY date ASC")
    fun recordsForPeriodFlow(startDate: String, endDate: String, assignmentId: String = ""): Flow<List<WorkRecord>>

    @Query("SELECT * FROM work_records WHERE date >= :startDate AND date <= :endDate AND (:assignmentId = '' OR assignmentId = :assignmentId) ORDER BY date ASC")
    suspend fun recordsForPeriod(startDate: String, endDate: String, assignmentId: String = ""): List<WorkRecord>

    @Query("SELECT * FROM work_records WHERE date LIKE :yearMonth || '%' ORDER BY date ASC")
    suspend fun recordsForMonth(yearMonth: String): List<WorkRecord>

    @Query("SELECT * FROM work_records WHERE date = :date AND (:assignmentId = '' OR assignmentId = :assignmentId) LIMIT 1")
    suspend fun recordForDate(date: String, assignmentId: String = ""): WorkRecord?

    @Query("SELECT * FROM work_records ORDER BY date DESC")
    suspend fun getAll(): List<WorkRecord>

    @Query("DELETE FROM work_records")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(record: WorkRecord)

    @Delete
    suspend fun delete(record: WorkRecord)
}
