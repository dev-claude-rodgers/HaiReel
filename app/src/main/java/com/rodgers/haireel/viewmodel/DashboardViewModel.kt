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

    // "" = 全取引先、それ以外は ReportPattern.id の文字列（帳票設定＝取引先単位で集計する）
    private val _patternSel         = MutableStateFlow("")
    val patternId: StateFlow<String> = _patternSel

    private val _patterns                        = MutableStateFlow<List<ReportPattern>>(emptyList())
    val patterns: StateFlow<List<ReportPattern>> = _patterns

    init {
        viewModelScope.launch { refresh() }
    }

    fun setPatternId(id: String) { _patternSel.value = id }

    suspend fun refresh() {
        _patterns.value = PatternStorage.getAll(getApplication())
    }

    val monthlySummaries: StateFlow<List<MonthlySummary>> =
        combine(_year, _patternSel, _patterns) { year, sel, patterns ->
            val cd: Int
            val targetPatternId: String?

            if (sel.isEmpty()) {
                // 全取引先は暦月を使用（取引先切り替えで締め日が変わる問題を防ぐ）
                cd = 31
                targetPatternId = null
            } else {
                cd = patterns.find { it.id.toString() == sel }?.closingDay
                    ?: AppSettings.getClosingDay(getApplication())
                targetPatternId = sel
            }

            Triple(year, cd, targetPatternId)
        }
        .flatMapLatest { triple ->
            val (year, cd, targetPatternId) = triple
            val periods = (1..12).map { month ->
                val ym = "%04d-%02d".format(year, month)
                ReportViewModel.computePeriod(ym, cd)
            }
            val queryStart = periods.first().first
            val queryEnd   = periods.last().second
            dao.recordsForPeriodFlow(queryStart, queryEnd)
                .map { records ->
                    val scoped = if (targetPatternId == null) records
                        else records.filter { it.assignmentId == targetPatternId || it.assignmentId.isEmpty() }

                    // 日付ごとに「同一取引先内の重複」だけを1件に潰す。異なる取引先の記録は
                    // 別々に残して合算する（潰しすぎると全取引先表示時に金額が過少になる）
                    val deduped = scoped.groupBy { it.date }
                        .flatMap { (_, recs) ->
                            val nonBlank = recs.filter { it.assignmentId.isNotBlank() }
                            val candidates = if (nonBlank.isNotEmpty()) nonBlank else recs
                            candidates.groupBy { it.assignmentId }
                                .map { (_, sameAssignment) -> sameAssignment.maxByOrNull { it.id }!! }
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
