package com.rodgers.haireel.db

import androidx.room.*
import com.rodgers.haireel.model.WorkRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkRecordDao {

    companion object {
        // assignmentId = "" のとき全件、それ以外は指定案件 + 旧データ(assignmentId="")を含む
        private const val ASSIGNMENT_FILTER = "(:assignmentId = '' OR assignmentId = :assignmentId OR assignmentId = '')"
    }

    @Query("SELECT * FROM work_records WHERE date LIKE :yearMonth || '%' AND $ASSIGNMENT_FILTER ORDER BY date ASC")
    fun recordsForMonthFlow(yearMonth: String, assignmentId: String = ""): Flow<List<WorkRecord>>

    @Query("SELECT * FROM work_records WHERE date >= :startDate AND date <= :endDate AND $ASSIGNMENT_FILTER ORDER BY date ASC")
    fun recordsForPeriodFlow(startDate: String, endDate: String, assignmentId: String = ""): Flow<List<WorkRecord>>

    @Query("SELECT * FROM work_records WHERE date >= :startDate AND date <= :endDate AND $ASSIGNMENT_FILTER ORDER BY date ASC")
    suspend fun recordsForPeriod(startDate: String, endDate: String, assignmentId: String = ""): List<WorkRecord>

    // assignmentIdが空: 同日に複数ルードの記録があれば非空ルードを優先し、id降順（最新保存分）で1件選ぶ。
    // assignmentIdが指定: その案件を優先し、なければ旧データ(assignmentId="")を返す。
    // ReportViewModel.records の重複排除ロジックと選択基準を一致させ、表示中のカードと編集対象がズレないようにする。
    @Query("""
        SELECT * FROM work_records WHERE date = :date AND $ASSIGNMENT_FILTER
        ORDER BY
            CASE
                WHEN :assignmentId != '' AND assignmentId = :assignmentId THEN 0
                WHEN :assignmentId = ''  AND assignmentId != ''           THEN 0
                ELSE 1
            END,
            id DESC
        LIMIT 1
    """)
    suspend fun recordForDate(date: String, assignmentId: String = ""): WorkRecord?

    @Query("SELECT * FROM work_records WHERE date LIKE :year || '%' ORDER BY date ASC")
    fun recordsForYearFlow(year: String): Flow<List<WorkRecord>>

    @Query("SELECT DISTINCT date FROM work_records WHERE date LIKE :yearMonth || '%' AND $ASSIGNMENT_FILTER AND noWork = 1 ORDER BY date ASC")
    fun noWorkDatesForMonthFlow(yearMonth: String, assignmentId: String = ""): Flow<List<String>>

    @Query("SELECT * FROM work_records ORDER BY date DESC")
    suspend fun getAll(): List<WorkRecord>

    @Query("SELECT COUNT(*) FROM work_records WHERE assignmentId = :assignmentId")
    suspend fun countByAssignment(assignmentId: String): Int

    @Query("DELETE FROM work_records")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(record: WorkRecord)

    @Delete
    suspend fun delete(record: WorkRecord)
}
