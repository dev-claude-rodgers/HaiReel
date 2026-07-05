package com.rodgers.haireel.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.db.FuelRecordDao
import com.rodgers.haireel.db.VehicleDao
import com.rodgers.haireel.model.FuelRecord
import com.rodgers.haireel.model.Vehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FuelViewModel @Inject constructor(
    private val dao: FuelRecordDao,
    private val vehicleDao: VehicleDao
) : ViewModel() {

    data class FuelEntry(
        val record: FuelRecord,
        val distanceKm: Int?,       // 前回給油からの走行距離（最初のレコードはnull）
        val fuelEconomy: Float?     // 区間燃費 km/L（最初のレコードはnull）
    )

    val records: StateFlow<List<FuelRecord>> = dao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vehicles: StateFlow<List<Vehicle>> = vehicleDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun entriesFrom(records: List<FuelRecord>): List<FuelEntry> =
        records.mapIndexed { i, r ->
            val prev = records.getOrNull(i - 1)
            val dist = if (prev != null && r.odometer > 0 && prev.odometer > 0)
                (r.odometer - prev.odometer).takeIf { it > 0 } else null
            val eco = if (dist != null && r.liters > 0f)
                dist.toFloat() / r.liters else null
            FuelEntry(r, dist, eco)
        }

    fun upsert(record: FuelRecord) = viewModelScope.launch { dao.upsert(record) }

    fun delete(record: FuelRecord) = viewModelScope.launch { dao.delete(record) }

    fun upsertVehicle(vehicle: Vehicle) = viewModelScope.launch { vehicleDao.upsert(vehicle) }

    fun deleteVehicle(vehicle: Vehicle) = viewModelScope.launch { vehicleDao.delete(vehicle) }
}
