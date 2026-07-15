package com.rodgers.haireel.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.db.WorkRecordDao
import com.rodgers.haireel.model.ReportPattern
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.PatternStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    app: Application,
    private val dao: WorkRecordDao
) : AndroidViewModel(app) {

    data class MonthlySummary(
        val month: Int,
        val income: Int,
        val fuelCost: Int,
        val profit: Int,
        val workDays: Int,
        val deliveryCount: Int
    )

    private val _year        = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year

    // -1 = 全取引先、それ以外は ReportPattern.id
    private val _patternId         = MutableStateFlow(-1)
    val patternId: StateFlow<Int>  = _patternId

    private val _patterns                         = MutableStateFlow<List<ReportPattern>>(emptyList())
    val patterns: StateFlow<List<ReportPattern>>  = _patterns

    init {
        viewModelScope.launch { refresh() }
    }

    fun setPatternId(id: Int) { _patternId.value = id }

    suspend fun refresh() {
        _patterns.value = PatternStorage.getAll(getApplication())
    }

    val monthlySummaries: StateFlow<List<MonthlySummary>> =
        combine(_year, _patternId, _patterns) { year, pid, patterns ->
            // 選択パターンの締め日を使用。未選択（-1）はアクティブパターン or デフォルト
            val cd = when {
                pid != -1 -> patterns.find { it.id == pid }?.closingDay
                patterns.isNotEmpty() -> {
                    val activeId = PatternStorage.getActiveId(getApplication())
                    patterns.find { it.id == activeId }?.closingDay ?: patterns[0].closingDay
                }
                else -> null
            } ?: AppSettings.getClosingDay(getApplication())
            Pair(year, cd)
        }
        .flatMapLatest { (year, cd) ->
            val periods = (1..12).map { month ->
                val ym = "%04d-%02d".format(year, month)
                ReportViewModel.computePeriod(ym, cd)
            }
            val queryStart = periods.first().first
            val queryEnd   = periods.last().second
            dao.recordsForPeriodFlow(queryStart, queryEnd)
                .map { records ->
                    // 同一日付は1件のみ: assignmentId有りを優先、複数ある場合はid最大（最新）を選択
                    val deduped = records.groupBy { it.date }
                        .map { (_, recs) ->
                            val nonBlank = recs.filter { it.assignmentId.isNotBlank() }
                            (if (nonBlank.isNotEmpty()) nonBlank else recs).maxByOrNull { it.id }!!
                        }
                        .sortedBy { it.date }
                    periods.mapIndexed { idx, (start, end) ->
                        val monthRecords = deduped.filter { it.date in start..end }
                        MonthlySummary(
                            month         = idx + 1,
                            income        = monthRecords.sumOf { it.income },
                            fuelCost      = monthRecords.sumOf { it.fuelCost },
                            profit        = monthRecords.sumOf { it.income - it.fuelCost },
                            workDays      = monthRecords.filter { !it.noWork }.distinctBy { it.date }.size,
                            deliveryCount = monthRecords.sumOf { it.deliveryCount }
                        )
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun previousYear() { _year.update { it - 1 } }

    fun nextYear() {
        if (_year.value < LocalDate.now().year) _year.update { it + 1 }
    }

    fun isCurrentYear(): Boolean = _year.value >= LocalDate.now().year
}
