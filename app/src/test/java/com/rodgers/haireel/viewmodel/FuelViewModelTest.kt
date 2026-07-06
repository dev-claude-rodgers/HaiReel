package com.rodgers.haireel.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rodgers.haireel.db.FuelRecordDao
import com.rodgers.haireel.db.VehicleDao
import com.rodgers.haireel.model.FuelRecord
import com.rodgers.haireel.model.Vehicle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FuelViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dao: FuelRecordDao
    private lateinit var vehicleDao: VehicleDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dao        = mockk(relaxed = true)
        vehicleDao = mockk(relaxed = true)
        every { dao.getAllFlow() }        returns flowOf(emptyList())
        every { vehicleDao.getAllFlow() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun makeVm() = FuelViewModel(dao, vehicleDao)

    private fun makeRecord(id: Long, odometer: Int, liters: Float) = FuelRecord(
        id = id,
        date = "2026-07-0$id",
        liters = liters,
        pricePerLiter = 170,
        totalCost = (liters * 170).toInt(),
        odometer = odometer
    )

    private fun makeVehicle(id: Long = 1L, name: String = "軽自動車") =
        Vehicle(id = id, name = name)

    // ── 空リスト ────────────────────────────────────────────────

    @Test
    fun `空リストはentriesが空`() {
        val vm = makeVm()
        assertTrue(vm.entriesFrom(emptyList()).isEmpty())
    }

    // ── 1件目は距離・燃費がnull ─────────────────────────────────

    @Test
    fun `最初のレコードはdistanceKmがnull`() {
        val vm = makeVm()
        val entries = vm.entriesFrom(listOf(makeRecord(1, 10000, 40f)))
        assertNull(entries[0].distanceKm)
    }

    @Test
    fun `最初のレコードはfuelEconomyがnull`() {
        val vm = makeVm()
        val entries = vm.entriesFrom(listOf(makeRecord(1, 10000, 40f)))
        assertNull(entries[0].fuelEconomy)
    }

    // ── 2件以上での距離・燃費計算 ───────────────────────────────

    @Test
    fun `2件目は前回からの走行距離を計算する`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 40f),
            makeRecord(2, 10400, 40f)
        )
        val entries = vm.entriesFrom(records)
        assertEquals(400, entries[1].distanceKm)
    }

    @Test
    fun `2件目の燃費はdistanceKm割るliters`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 40f),
            makeRecord(2, 10400, 40f)    // 400km ÷ 40L = 10.0km/L
        )
        val entries = vm.entriesFrom(records)
        assertEquals(10.0f, entries[1].fuelEconomy!!, 0.01f)
    }

    @Test
    fun `3件連続で各区間の距離を個別に計算する`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 30f),
            makeRecord(2, 10300, 30f),   // +300km
            makeRecord(3, 10600, 20f)    // +300km
        )
        val entries = vm.entriesFrom(records)
        assertNull(entries[0].distanceKm)
        assertEquals(300, entries[1].distanceKm)
        assertEquals(300, entries[2].distanceKm)
    }

    // ── エッジケース ─────────────────────────────────────────────

    @Test
    fun `オドメーター0の前レコードは距離計算をスキップしnullを返す`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 0, 40f),    // odometer未設定
            makeRecord(2, 10400, 40f)
        )
        val entries = vm.entriesFrom(records)
        assertNull(entries[1].distanceKm)
        assertNull(entries[1].fuelEconomy)
    }

    @Test
    fun `現在オドメーター0は距離計算をスキップしnullを返す`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 40f),
            makeRecord(2, 0, 40f)    // odometer未設定
        )
        val entries = vm.entriesFrom(records)
        assertNull(entries[1].distanceKm)
        assertNull(entries[1].fuelEconomy)
    }

    @Test
    fun `liters0は燃費計算をスキップしnullを返す`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 40f),
            makeRecord(2, 10400, 0f)   // liters未入力
        )
        val entries = vm.entriesFrom(records)
        assertEquals(400, entries[1].distanceKm)
        assertNull(entries[1].fuelEconomy)
    }

    @Test
    fun `オドメーターが前回より小さい場合distanceKmはnull`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10400, 40f),
            makeRecord(2, 10000, 40f)  // 車を変えた等で減少
        )
        val entries = vm.entriesFrom(records)
        assertNull(entries[1].distanceKm)
        assertNull(entries[1].fuelEconomy)
    }

    @Test
    fun `オドメーターが前回と同じ場合distanceKmはnull`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 40f),
            makeRecord(2, 10000, 40f)  // 差分0 → takeIf { it > 0 } で null
        )
        val entries = vm.entriesFrom(records)
        assertNull(entries[1].distanceKm)
    }

    // ── upsert / delete (DAO 呼び出し) ───────────────────────────

    @Test
    fun `upsertでdao_upsertが呼ばれる`() = runTest(testDispatcher) {
        val vm = makeVm()
        val record = makeRecord(1, 10000, 40f)

        vm.upsert(record)
        advanceUntilIdle()

        coVerify { dao.upsert(record) }
    }

    @Test
    fun `deleteでdao_deleteが呼ばれる`() = runTest(testDispatcher) {
        val vm = makeVm()
        val record = makeRecord(1, 10000, 40f)

        vm.delete(record)
        advanceUntilIdle()

        coVerify { dao.delete(record) }
    }

    @Test
    fun `upsertVehicleでvehicleDao_upsertが呼ばれる`() = runTest(testDispatcher) {
        val vm = makeVm()
        val vehicle = makeVehicle(name = "テスト車両")

        vm.upsertVehicle(vehicle)
        advanceUntilIdle()

        coVerify { vehicleDao.upsert(vehicle) }
    }

    @Test
    fun `deleteVehicleでvehicleDao_deleteが呼ばれる`() = runTest(testDispatcher) {
        val vm = makeVm()
        val vehicle = makeVehicle()

        vm.deleteVehicle(vehicle)
        advanceUntilIdle()

        coVerify { vehicleDao.delete(vehicle) }
    }

    // ── vehicles StateFlow / Vehicle モデル ──────────────────────

    @Test
    fun `vehicles初期値は空リスト`() {
        // stateIn の initial value が emptyList() であることを確認
        val vm = makeVm()
        assertTrue(vm.vehicles.value.isEmpty())
    }

    @Test
    fun `records初期値は空リスト`() {
        val vm = makeVm()
        assertTrue(vm.records.value.isEmpty())
    }

    @Test
    fun `Vehicle_nameとinitialOdometerが保持される`() {
        val v = Vehicle(id = 1L, name = "テスト車両", initialOdometer = 12000)
        assertEquals("テスト車両", v.name)
        assertEquals(12000, v.initialOdometer)
    }

    @Test
    fun `entriesFromで3件連続の燃費は2件計算される`() {
        val vm = makeVm()
        val records = listOf(
            makeRecord(1, 10000, 40f),
            makeRecord(2, 10400, 40f),
            makeRecord(3, 10800, 40f)
        )
        val entries = vm.entriesFrom(records)
        assertNull(entries[0].fuelEconomy)
        assertNotNull(entries[1].fuelEconomy)
        assertNotNull(entries[2].fuelEconomy)
    }
}
