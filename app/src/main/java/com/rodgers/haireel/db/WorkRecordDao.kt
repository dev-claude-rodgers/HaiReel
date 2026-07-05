package com.rodgers.haireel.db

import androidx.room.*
import com.rodgers.haireel.model.WorkRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkRecordDao {

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

    @Query("SELECT * FROM work_records WHERE date LIKE :year || '%' ORDER BY date ASC")
    fun recordsForYearFlow(year: String): Flow<List<WorkRecord>>

    @Query("SELECT date FROM work_records WHERE date LIKE :yearMonth || '%' AND (:assignmentId = '' OR assignmentId = :assignmentId) AND noWork = 1 ORDER BY date ASC")
    fun noWorkDatesForMonthFlow(yearMonth: String, assignmentId: String = ""): Flow<List<String>>

    @Query("SELECT * FROM work_records ORDER BY date DESC")
    suspend fun getAll(): List<WorkRecord>

    @Query("DELETE FROM work_records")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(record: WorkRecord)

    @Delete
    suspend fun delete(record: WorkRecord)
}
