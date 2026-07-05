package com.rodgers.haireel.viewmodel

import android.app.Application
import com.rodgers.haireel.db.DeliveryGroupDao
import com.rodgers.haireel.db.WorkRecordDao
import com.rodgers.haireel.model.WorkRecord
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockDao: WorkRecordDao
    private lateinit var mockGroupDao: DeliveryGroupDao
    private lateinit var vm: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockDao = mockk(relaxed = true)
        mockGroupDao = mockk(relaxed = true)
        every { mockDao.recordsForPeriodFlow(any(), any(), any()) } returns flowOf(emptyList())
        every { mockDao.recordsForYearFlow(any()) } returns flowOf(emptyList())
        coEvery { mockGroupDao.getAll() } returns emptyList()
        vm = DashboardViewModel(mockApp, mockDao, mockGroupDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 年遷移 ─────────────────────────────────────────────────

    @Test
    fun `previousYearで年が1つ前に戻る`() {
        val current = vm.year.value
        vm.previousYear()
        assertEquals(current - 1, vm.year.value)
    }

    @Test
    fun `previousYearを複数回呼んでも連続して減る`() {
        val current = vm.year.value
        vm.previousYear(); vm.previousYear(); vm.previousYear()
        assertEquals(current - 3, vm.year.value)
    }

    @Test
    fun `nextYearは現在年を超えない`() {
        val current = vm.year.value
        vm.nextYear()
        assertEquals(current, vm.year.value)
    }

    @Test
    fun `previousYearしてからnextYearで元に戻る`() {
        val current = vm.year.value
        vm.previousYear()
        vm.nextYear()
        assertEquals(current, vm.year.value)
    }

    @Test
    fun `isCurrentYearで今年はtrueを返す`() {
        assertTrue(vm.isCurrentYear())
    }

    @Test
    fun `previousYearしたらisCurrentYearはfalseになる`() {
        vm.previousYear()
        assertFalse(vm.isCurrentYear())
    }

    @Test
    fun `previousYearしてからnextYearで戻るとisCurrentYearがtrueに戻る`() {
        vm.previousYear()
        vm.nextYear()
        assertTrue(vm.isCurrentYear())
    }

    // ── MonthlySummary マッピング ─────────────────────────────

    @Test
    fun `空のレコードリストはすべての月が0のサマリーになる`() {
        every { mockDao.recordsForYearFlow(any()) } returns flowOf(emptyList())
        val vm2 = DashboardViewModel(mockApp, mockDao, mockGroupDao)
        // stateIn の初期値を確認
        val summaries = vm2.monthlySummaries.value
        assertEquals(0, summaries.size)
    }

}
