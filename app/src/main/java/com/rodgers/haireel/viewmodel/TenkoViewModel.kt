package com.rodgers.haireel.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.db.TenkoDao
import com.rodgers.haireel.db.WorkRecordDao
import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.model.WorkRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class TenkoViewModel @Inject constructor(
    app: Application,
    private val dao: TenkoDao,
    private val workRecordDao: WorkRecordDao
) : AndroidViewModel(app) {
    private val monthFmt = DateTimeFormatter.ofPattern("yyyy-MM")

    private val _yearMonth = MutableStateFlow(LocalDate.now().format(monthFmt))
    val yearMonth: StateFlow<String> = _yearMonth

    private val _assignmentId = MutableStateFlow("")
    val assignmentId: StateFlow<String> = _assignmentId

    val monthRecords: StateFlow<List<TenkoRecord>> =
        combine(_yearMonth, _assignmentId) { ym, aid -> ym to aid }
            .flatMapLatest { (ym, aid) -> dao.getByMonthFlow(ym, aid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noWorkDates: StateFlow<Set<String>> =
        combine(_yearMonth, _assignmentId) { ym, aid -> ym to aid }
            .flatMapLatest { (ym, aid) ->
                workRecordDao.noWorkDatesForMonthFlow(ym, aid).map { it.toSet() }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setNoWork(date: String, isNoWork: Boolean) = viewModelScope.launch {
        val aid = _assignmentId.value
        val existing = workRecordDao.recordForDate(date, aid)
        val record = existing?.copy(noWork = isNoWork)
            ?: WorkRecord(date = date, assignmentId = aid, noWork = isNoWork)
        workRecordDao.upsert(record)
    }

    fun setAssignmentId(id: String) { _assignmentId.value = id }

    fun previousMonth() {
        val d = LocalDate.parse("${_yearMonth.value}-01").minusMonths(1)
        _yearMonth.value = d.format(monthFmt)
    }

    fun nextMonth() {
        val d = LocalDate.parse("${_yearMonth.value}-01").plusMonths(1)
        _yearMonth.value = d.format(monthFmt)
    }

    fun todayDate(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun jumpToToday() {
        _yearMonth.value = LocalDate.now().format(monthFmt)
    }

    fun saveBefore(
        date: String, existing: TenkoRecord?,
        method: String, time: String,
        health: Boolean, fatigue: Boolean,
        alcohol: Double, inspection: Boolean,
        instruction: String, checker: String,
        vehicleNumber: String = ""
    ) = viewModelScope.launch {
        val aid = _assignmentId.value
        val record = existing?.copy(
            beforeMethod = method, beforeTime = time,
            beforeHealth = health, beforeFatigue = fatigue,
            beforeAlcohol = alcohol, beforeInspection = inspection,
            beforeInstruction = instruction.ifBlank { null },
            beforeChecker = checker.ifBlank { null },
            vehicleNumber = vehicleNumber.ifBlank { null }
        ) ?: TenkoRecord(
            date = date, assignmentId = aid,
            beforeMethod = method, beforeTime = time,
            beforeHealth = health, beforeFatigue = fatigue,
            beforeAlcohol = alcohol, beforeInspection = inspection,
            beforeInstruction = instruction.ifBlank { null },
            beforeChecker = checker.ifBlank { null },
            vehicleNumber = vehicleNumber.ifBlank { null }
        )
        if (existing != null) dao.update(record) else dao.insert(record)
    }

    fun saveAfter(
        date: String, existing: TenkoRecord?,
        method: String, time: String,
        health: Boolean, fatigue: Boolean,
        alcohol: Double, accident: Boolean, vehicle: Boolean,
        instruction: String, checker: String, note: String = ""
    ) = viewModelScope.launch {
        val aid = _assignmentId.value
        val record = existing?.copy(
            afterMethod = method, afterTime = time,
            afterHealth = health, afterFatigue = fatigue,
            afterAlcohol = alcohol, afterAccident = accident,
            afterVehicle = vehicle,
            afterInstruction = instruction.ifBlank { null },
            afterChecker = checker.ifBlank { null },
            note = note.ifBlank { null }
        ) ?: TenkoRecord(
            date = date, assignmentId = aid,
            afterMethod = method, afterTime = time,
            afterHealth = health, afterFatigue = fatigue,
            afterAlcohol = alcohol, afterAccident = accident,
            afterVehicle = vehicle,
            afterInstruction = instruction.ifBlank { null },
            afterChecker = checker.ifBlank { null },
            note = note.ifBlank { null }
        )
        if (existing != null) dao.update(record) else dao.insert(record)
    }

    fun delete(record: TenkoRecord) = viewModelScope.launch { dao.delete(record) }

    fun restore(record: TenkoRecord) = viewModelScope.launch { dao.insert(record) }

    fun restoreAll(records: List<TenkoRecord>) = viewModelScope.launch {
        records.forEach { dao.insert(it) }
    }

    fun deleteMonth(yearMonth: String) = viewModelScope.launch { dao.deleteByMonth(yearMonth) }

    fun deleteMonthWithUndo(yearMonth: String, onDeleted: (List<TenkoRecord>) -> Unit) {
        viewModelScope.launch {
            val records = dao.getAllByMonth(yearMonth)
            dao.deleteByMonth(yearMonth)
            withContext(Dispatchers.Main) { onDeleted(records) }
        }
    }

    suspend fun recordsForMonth(yearMonth: String): List<TenkoRecord> =
        dao.getByMonth(yearMonth, _assignmentId.value)

    suspend fun allRecordsForMonth(yearMonth: String): List<TenkoRecord> =
        dao.getAllByMonth(yearMonth)
}
