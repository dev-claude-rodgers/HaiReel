package com.rodgers.haireel.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.db.WorkRecordDao
import com.rodgers.haireel.model.WorkRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel @Inject constructor(
    app: Application,
    private val dao: WorkRecordDao
) : AndroidViewModel(app) {
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    private val _yearMonth = MutableStateFlow(LocalDate.now().format(monthFmt))
    val yearMonth: StateFlow<String> = _yearMonth

    private val _closingDay = MutableStateFlow(31)
    val closingDay: StateFlow<Int> = _closingDay

    // 現在選択中の案件ID（""=全案件表示）
    private val _assignmentId = MutableStateFlow("")
    val assignmentId: StateFlow<String> = _assignmentId

    // _closingDay も含めて period ベースでリアルタイム取得
    // 旧データ(assignmentId="")と新データが混在する場合は同日を1件に絞る（新データ優先）
    val records: StateFlow<List<WorkRecord>> =
        combine(_yearMonth, _closingDay, _assignmentId) { ym, cd, aid -> Triple(ym, cd, aid) }
        .flatMapLatest { (ym, cd, aid) ->
            val (start, end) = computePeriod(ym, cd)
            if (com.rodgers.haireel.BuildConfig.DEBUG) android.util.Log.d("ReportVM", "records query: aid='$aid', period=$start~$end")
            dao.recordsForPeriodFlow(start, end, aid).map { list ->
                if (com.rodgers.haireel.BuildConfig.DEBUG) android.util.Log.d("ReportVM", "records result: ${list.size}件, income=${list.sumOf { it.income }}, assignmentIds=${list.map { it.assignmentId }.distinct()}")
                list.groupBy { it.date }
                    .flatMap { (_, recs) ->
                        if (aid.isBlank()) {
                            // 全案件: 同日にblankとグループ両方ある場合はblankを除外、複数グループは全て残す
                            val nonBlank = recs.filter { it.assignmentId.isNotBlank() }
                            if (nonBlank.isNotEmpty()) nonBlank else recs.take(1)
                        } else {
                            // 特定案件: グループレコード優先、なければblankを使用（1件）
                            listOf(recs.firstOrNull { it.assignmentId == aid } ?: recs.first())
                        }
                    }
                    .sortedBy { it.date }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setClosingDay(day: Int) { _closingDay.value = day }

    fun setAssignmentId(id: String) { _assignmentId.value = id }

    fun previousMonth() {
        val d = LocalDate.parse("${_yearMonth.value}-01").minusMonths(1)
        _yearMonth.value = d.format(monthFmt)
    }

    fun nextMonth() {
        val d = LocalDate.parse("${_yearMonth.value}-01").plusMonths(1)
        _yearMonth.value = d.format(monthFmt)
    }

    fun jumpToToday() {
        _yearMonth.value = LocalDate.now().format(monthFmt)
    }

    fun save(record: WorkRecord) = viewModelScope.launch {
        try { dao.upsert(record) }
        catch (e: Exception) { android.util.Log.e("ReportViewModel", "save 失敗", e) }
    }

    suspend fun saveAndWait(record: WorkRecord) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { dao.upsert(record) }

    fun delete(record: WorkRecord) = viewModelScope.launch {
        try { dao.delete(record) }
        catch (e: Exception) { android.util.Log.e("ReportViewModel", "delete 失敗", e) }
    }

    fun setNoWork(date: String, isNoWork: Boolean) = viewModelScope.launch {
        try {
            val aid = _assignmentId.value
            val existing = dao.recordForDate(date, aid)
            val record = existing?.copy(noWork = isNoWork)
                ?: WorkRecord(date = date, assignmentId = aid, noWork = isNoWork)
            dao.upsert(record)
        } catch (e: Exception) { android.util.Log.e("ReportViewModel", "setNoWork 失敗", e) }
    }

    suspend fun recordForDate(date: String): WorkRecord? = dao.recordForDate(date, _assignmentId.value)

    suspend fun recordsForPeriod(startDate: String, endDate: String): List<WorkRecord> =
        dao.recordsForPeriod(startDate, endDate, _assignmentId.value)

    suspend fun recordsForPeriodWithAssignment(startDate: String, endDate: String, assignmentId: String): List<WorkRecord> =
        dao.recordsForPeriod(startDate, endDate, assignmentId)

    // 全案件の記録を取得（サマリー用）
    suspend fun allRecordsForMonth(yearMonth: String): List<WorkRecord> =
        dao.recordsForMonth(yearMonth)

    companion object {
        // 締め日から集計期間の開始日・終了日を返す (ISO文字列のPair)
        fun computePeriod(yearMonth: String, closingDay: Int): Pair<String, String> {
            val parsed = java.time.YearMonth.parse(yearMonth)
            val ym = LocalDate.of(parsed.year, parsed.monthValue, 1)
            val lastDayOfMonth = ym.lengthOfMonth()
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE

            return if (closingDay >= 31) {
                // 31日締め＝月末締め: 当月1日〜月末
                ym.format(fmt) to ym.withDayOfMonth(lastDayOfMonth).format(fmt)
            } else {
                // N日締め: 前月(N+1)日〜当月N日（28/29/30は実際の日付として扱う）
                val prevMonth = ym.minusMonths(1)
                val prevLastDay = prevMonth.lengthOfMonth()
                val start = prevMonth.withDayOfMonth(minOf(closingDay + 1, prevLastDay))
                val end = ym.withDayOfMonth(minOf(closingDay, lastDayOfMonth))
                start.format(fmt) to end.format(fmt)
            }
        }
    }
}
