package com.rodgers.haireel.viewmodel

import com.rodgers.haireel.model.FuelRecord
import org.junit.Assert.*
import org.junit.Test

class FuelViewModelTest {

    private fun makeRecord(id: Long, odometer: Int, liters: Float) = FuelRecord(
        id = id,
        date = "2026-07-0$id",
        liters = liters,
        pricePerLiter = 170,
        totalCost = (liters * 170).toInt(),
        odometer = odometer
    )

    private fun makeVm(): FuelViewModel {
        val dao       = io.mockk.mockk<com.rodgers.haireel.db.FuelRecordDao>(relaxed = true)
        val vehicleDao = io.mockk.mockk<com.rodgers.haireel.db.VehicleDao>(relaxed = true)
        io.mockk.every { dao.getAllFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        io.mockk.every { vehicleDao.getAllFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        return FuelViewModel(dao, vehicleDao)
    }

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
}
