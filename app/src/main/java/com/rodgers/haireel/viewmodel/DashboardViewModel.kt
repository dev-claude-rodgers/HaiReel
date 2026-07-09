package com.rodgers.haireel.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.db.DeliveryGroupDao
import com.rodgers.haireel.db.DeliveryGroupEntity
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

    // -1 = 全取引先、それ以外は ReportPattern.id
    private val _patternId         = MutableStateFlow(-1)
    val patternId: StateFlow<Int>  = _patternId

    private val _patterns                         = MutableStateFlow<List<ReportPattern>>(emptyList())
    val patterns: StateFlow<List<ReportPattern>>  = _patterns

    private val _groups                                      = MutableStateFlow<List<DeliveryGroupEntity>>(emptyList())
    private val groups: StateFlow<List<DeliveryGroupEntity>> = _groups

    init {
        viewModelScope.launch { refresh() }
    }

    fun setPatternId(id: Int) { _patternId.value = id }

    suspend fun refresh() {
        _groups.value   = groupDao.getAll()
        _patterns.value = PatternStorage.getAll(getApplication())
    }

    val monthlySummaries: StateFlow<List<MonthlySummary>> =
        combine(_year, _patternId, _groups, _patterns) { year, pid, groups, patterns ->
            // 特定パターン選択時のみグループIDセットを作成
            // グループが未紐付けの場合は null（全件表示）にフォールバック
            val groupIds: Set<String>? = if (pid != -1) {
                groups.filter { it.patternId == pid }.map { it.id }.toSet()
                    .takeIf { it.isNotEmpty() }
            } else null
            val cd = when {
                pid != -1 -> patterns.find { it.id == pid }?.closingDay
                patterns.isNotEmpty() -> {
                    // 全取引先表示時はアクティブパターンの締め日を使用（日報と一致させる）
                    val activeId = com.rodgers.haireel.util.PatternStorage.getActiveId(getApplication())
                    patterns.find { it.id == activeId }?.closingDay ?: patterns[0].closingDay
                }
                else -> null
            } ?: AppSettings.getClosingDay(getApplication())
            Triple(Pair(year, cd), pid, groupIds)
        }
        .flatMapLatest { (yearCd, _, groupIds) ->
            val (year, cd) = yearCd
            val periods = (1..12).map { month ->
                val ym = "%04d-%02d".format(year, month)
                ReportViewModel.computePeriod(ym, cd)
            }
            val queryStart = periods.first().first
            val queryEnd   = periods.last().second
            dao.recordsForPeriodFlow(queryStart, queryEnd)
                .map { records ->
                    val filtered = if (groupIds == null) records
                                   else records.filter { it.assignmentId in groupIds || it.assignmentId.isBlank() }
                    // 同一日付の重複排除: blankとグループ両方ある場合はblankを除外
                    val deduped = filtered.groupBy { it.date }
                        .flatMap { (_, recs) ->
                            if (groupIds == null) {
                                // 全案件: 複数グループは全て残す、blankはグループがある日付から除外
                                val nonBlank = recs.filter { it.assignmentId.isNotBlank() }
                                if (nonBlank.isNotEmpty()) nonBlank else recs.take(1)
                            } else {
                                // 特定案件群: グループレコード優先、なければblank（1件）
                                listOf(recs.firstOrNull { it.assignmentId in groupIds } ?: recs.first())
                            }
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
