package com.rodgers.routist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.routist.db.WorkRecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val dao: WorkRecordDao
) : ViewModel() {

    data class MonthlySummary(
        val month: Int,
        val income: Int,
        val fuelCost: Int,
        val profit: Int,
        val workDays: Int,
        val deliveryCount: Int
    )

    data class WeekSummary(
        val workDays: Int,
        val income: Int,
        val deliveryCount: Int,
        val distanceKm: Float
    )

    val weekSummary: StateFlow<WeekSummary> = run {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        dao.recordsForPeriodFlow(monday.format(fmt), today.format(fmt))
            .map { records ->
                WeekSummary(
                    workDays = records.size,
                    income = records.sumOf { it.income },
                    deliveryCount = records.sumOf { it.deliveryCount },
                    distanceKm = records.sumOf { it.distanceKm.toDouble() }.toFloat()
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekSummary(0, 0, 0, 0f))

    private val _year = MutableStateFlow(LocalDate.now().year)
    val year: StateFlow<Int> = _year

    val monthlySummaries: StateFlow<List<MonthlySummary>> = _year
        .flatMapLatest { year -> dao.recordsForYearFlow("%04d".format(year)) }
        .map { records ->
            (1..12).map { month ->
                val monthStr = "%02d".format(month)
                val monthRecords = records.filter {
                    it.date.length >= 7 && it.date.substring(5, 7) == monthStr
                }
                MonthlySummary(
                    month = month,
                    income = monthRecords.sumOf { it.income },
                    fuelCost = monthRecords.sumOf { it.fuelCost },
                    profit = monthRecords.sumOf { it.income - it.fuelCost },
                    workDays = monthRecords.distinctBy { it.date }.size,
                    deliveryCount = monthRecords.sumOf { it.deliveryCount }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun previousYear() { _year.update { it - 1 } }

    fun nextYear() {
        if (_year.value < LocalDate.now().year) _year.update { it + 1 }
    }

    fun isCurrentYear(): Boolean = _year.value >= LocalDate.now().year
}
