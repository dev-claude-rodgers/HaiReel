package com.rodgers.haireel.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.db.DeliveryGroupDao
import com.rodgers.haireel.db.WorkRecordDao
import com.rodgers.haireel.db.toGroup
import com.rodgers.haireel.model.DeliveryGroup
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
    private val dao: WorkRecordDao,
    private val groupDao: DeliveryGroupDao
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

    // "" = 全ルート、それ以外は DeliveryGroup.id
    private val _groupId         = MutableStateFlow("")
    val groupId: StateFlow<String> = _groupId

    private val _patterns                        = MutableStateFlow<List<ReportPattern>>(emptyList())
    val patterns: StateFlow<List<ReportPattern>> = _patterns

    private val _groups                      = MutableStateFlow<List<DeliveryGroup>>(emptyList())
    val groups: StateFlow<List<DeliveryGroup>> = _groups

    init {
        viewModelScope.launch { refresh() }
    }

    fun setGroupId(id: String) { _groupId.value = id }

    suspend fun refresh() {
        _patterns.value = PatternStorage.getAll(getApplication())
        _groups.value   = groupDao.getAll().map { it.toGroup() }
    }

    val monthlySummaries: StateFlow<List<MonthlySummary>> =
        combine(_year, _groupId, _patterns, _groups) { year, gid, patterns, groups ->
            val cd: Int
            val targetGroupIds: Set<String>?

            if (gid.isEmpty()) {
                // 全ルートは暦月を使用（ルート切り替えで締め日が変わる問題を防ぐ）
                cd = 31
                targetGroupIds = null
            } else {
                val group = groups.find { it.id == gid }
                val pattern = if (group?.patternId != null && group.patternId != -1)
                    patterns.find { it.id == group.patternId } else null
                cd = pattern?.closingDay ?: AppSettings.getClosingDay(getApplication())
                targetGroupIds = setOf(gid)
            }

            Triple(year, cd, targetGroupIds)
        }
        .flatMapLatest { triple ->
            val (year, cd, targetGroupIds) = triple
            val periods = (1..12).map { month ->
                val ym = "%04d-%02d".format(year, month)
                ReportViewModel.computePeriod(ym, cd)
            }
            val queryStart = periods.first().first
            val queryEnd   = periods.last().second
            dao.recordsForPeriodFlow(queryStart, queryEnd)
                .map { records ->
                    val scoped = when {
                        targetGroupIds == null      -> records
                        targetGroupIds.isNotEmpty() -> records.filter {
                            it.assignmentId in targetGroupIds || it.assignmentId.isEmpty()
                        }
                        else                        -> emptyList()
                    }

                    // 日付ごとに「同一ルート内の重複」だけを1件に潰す。異なるルートの記録は
                    // 別々に残して合算する（潰しすぎると全ルート表示時に金額が過少になる）
                    val deduped = scoped.groupBy { it.date }
                        .flatMap { (_, recs) ->
                            val nonBlank = recs.filter { it.assignmentId.isNotBlank() }
                            val candidates = if (nonBlank.isNotEmpty()) nonBlank else recs
                            candidates.groupBy { it.assignmentId }
                                .map { (_, sameRoute) -> sameRoute.maxByOrNull { it.id }!! }
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
