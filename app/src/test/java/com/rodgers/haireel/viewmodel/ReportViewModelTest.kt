package com.rodgers.haireel.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rodgers.haireel.model.WorkRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ReportViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    // ── computePeriod: 月末締め (closingDay >= 31) ──────────────

    @Test
    fun `31日締めは当月1日から月末まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-03", 31)
        assertEquals("2026-03-01", start)
        assertEquals("2026-03-31", end)
    }

    @Test
    fun `31日締めで2月は月末28日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-02", 31)
        assertEquals("2026-02-01", start)
        assertEquals("2026-02-28", end)
    }

    @Test
    fun `31日締めで閏年2月は29日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2024-02", 31)
        assertEquals("2024-02-01", start)
        assertEquals("2024-02-29", end)
    }

    @Test
    fun `31日締めで30日しかない月は30日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-04", 31)
        assertEquals("2026-04-01", start)
        assertEquals("2026-04-30", end)
    }

    // ── computePeriod: N日締め ────────────────────────────────

    @Test
    fun `20日締めは前月21日から当月20日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-03", 20)
        assertEquals("2026-02-21", start)
        assertEquals("2026-03-20", end)
    }

    @Test
    fun `25日締めは前月26日から当月25日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-06", 25)
        assertEquals("2026-05-26", start)
        assertEquals("2026-06-25", end)
    }

    @Test
    fun `15日締めは前月16日から当月15日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-01", 15)
        assertEquals("2025-12-16", start)
        assertEquals("2026-01-15", end)
    }

    @Test
    fun `1月の場合は前月が12月になる`() {
        val (start, end) = ReportViewModel.computePeriod("2026-01", 20)
        assertEquals("2025-12-21", start)
        assertEquals("2026-01-20", end)
    }

    @Test
    fun `前月が2月で締め日が前月末を超える場合は前月末になる`() {
        // closingDay=30 で前月が2月(28日)の場合 → start は2月28日
        val (start, end) = ReportViewModel.computePeriod("2026-03", 30)
        assertEquals("2026-02-28", start)
        assertEquals("2026-03-30", end)
    }

    @Test
    fun `前月が閏年2月で締め日29以上の場合は29日になる`() {
        val (start, end) = ReportViewModel.computePeriod("2024-03", 30)
        assertEquals("2024-02-29", start)
        assertEquals("2024-03-30", end)
    }

    @Test
    fun `締め日が当月日数を超える場合は当月末になる`() {
        // closingDay=30 で2月(28日) → end は2月28日
        val (start, end) = ReportViewModel.computePeriod("2026-02", 30)
        assertEquals("2026-01-31", start)
        assertEquals("2026-02-28", end)
    }

    @Test
    fun `締め日5日は前月6日から当月5日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-05", 5)
        assertEquals("2026-04-06", start)
        assertEquals("2026-05-05", end)
    }

    // ── ReportViewModel 状態遷移 ──────────────────────────────

    @Test
    fun `previousMonthで月が1つ前に戻る`() {
        val vm = makeVm()
        val before = vm.yearMonth.value
        vm.previousMonth()
        val ym = java.time.YearMonth.parse(vm.yearMonth.value)
        val beforeYm = java.time.YearMonth.parse(before)
        assertEquals(beforeYm.minusMonths(1), ym)
    }

    @Test
    fun `nextMonthで月が1つ進む`() {
        val vm = makeVm()
        vm.previousMonth()
        val before = vm.yearMonth.value
        vm.nextMonth()
        val ym = java.time.YearMonth.parse(vm.yearMonth.value)
        val beforeYm = java.time.YearMonth.parse(before)
        assertEquals(beforeYm.plusMonths(1), ym)
    }

    @Test
    fun `jumpToTodayで今月に戻る`() {
        val vm = makeVm()
        vm.previousMonth(); vm.previousMonth(); vm.previousMonth()
        vm.jumpToToday()
        val expected = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
        assertEquals(expected, vm.yearMonth.value)
    }

    @Test
    fun `setClosingDayで締め日が更新される`() {
        val vm = makeVm()
        vm.setClosingDay(20)
        assertEquals(20, vm.closingDay.value)
    }

    @Test
    fun `setAssignmentIdで案件IDが更新される`() {
        val vm = makeVm()
        vm.setAssignmentId("job_01")
        assertEquals("job_01", vm.assignmentId.value)
    }

    private fun makeVm(): ReportViewModel {
        val mockApp = io.mockk.mockk<android.app.Application>(relaxed = true)
        val mockDao = io.mockk.mockk<com.rodgers.haireel.db.WorkRecordDao>(relaxed = true)
        // recordsは締め日ベースのPeriodFlowを使用（relaxed=trueで自動stub）
        io.mockk.every { mockDao.recordsForPeriodFlow(any(), any(), any()) } returns
            kotlinx.coroutines.flow.flowOf(emptyList())
        return ReportViewModel(mockApp, mockDao)
    }

    // ── computePeriod 追加境界値 ─────────────────────────────

    @Test
    fun `1日締めは前月2日から当月1日まで`() {
        val (start, end) = ReportViewModel.computePeriod("2026-06", 1)
        assertEquals("2026-05-02", start)
        assertEquals("2026-06-01", end)
    }

    @Test
    fun `30日締めで前月が2月の場合は前月末から`() {
        val (start, end) = ReportViewModel.computePeriod("2026-03", 30)
        assertEquals("2026-02-28", start)  // 2月は28日まで → minOf(31, 28)
        assertEquals("2026-03-30", end)
    }

    @Test
    fun `月をまたいだ締め日の期間日数は正しい`() {
        val (start, end) = ReportViewModel.computePeriod("2026-06", 25)
        val s = java.time.LocalDate.parse(start)
        val e = java.time.LocalDate.parse(end)
        val days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1
        assertEquals(31L, days)  // 5/26〜6/25 = 31日間
    }

    @Test
    fun `月末締めの期間日数は当月の日数と一致する`() {
        val (start, end) = ReportViewModel.computePeriod("2026-06", 31)
        val s = java.time.LocalDate.parse(start)
        val e = java.time.LocalDate.parse(end)
        val days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1
        assertEquals(30L, days)  // 6月は30日
    }

    // ── setNoWork ─────────────────────────────────────────────

    @Test
    fun `setNoWorkで既存レコードがない場合_新規レコードをnoWorkでupsertする`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            coEvery { dao.recordForDate(any(), any()) } returns null

            vm.setNoWork("2026-06-10", true)
            advanceUntilIdle()

            coVerify { dao.upsert(match { it.date == "2026-06-10" && it.noWork }) }
        }

    @Test
    fun `setNoWorkで既存レコードがある場合_noWorkを更新してupsertする`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            val existing = WorkRecord(date = "2026-06-10", deliveryCount = 50, noWork = false)
            coEvery { dao.recordForDate("2026-06-10", any()) } returns existing

            vm.setNoWork("2026-06-10", true)
            advanceUntilIdle()

            coVerify { dao.upsert(match { it.date == "2026-06-10" && it.noWork && it.deliveryCount == 50 }) }
        }

    @Test
    fun `setNoWorkでfalseに戻すとnoWorkがfalseでupsertされる`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            val existing = WorkRecord(date = "2026-06-10", noWork = true)
            coEvery { dao.recordForDate("2026-06-10", any()) } returns existing

            vm.setNoWork("2026-06-10", false)
            advanceUntilIdle()

            coVerify { dao.upsert(match { it.date == "2026-06-10" && !it.noWork }) }
        }

    // ── recordForDate ─────────────────────────────────────────

    @Test
    fun `recordForDateでdao_recordForDateの結果を返す`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            val record = WorkRecord(date = "2026-06-15", deliveryCount = 30)
            coEvery { dao.recordForDate("2026-06-15", any()) } returns record

            val result = vm.recordForDate("2026-06-15")

            assertEquals(record, result)
        }

    @Test
    fun `recordForDateでレコードがない場合nullを返す`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            coEvery { dao.recordForDate(any(), any()) } returns null

            assertNull(vm.recordForDate("2026-06-01"))
        }

    // ── save / delete ─────────────────────────────────────────

    @Test
    fun `saveでdao_upsertが呼ばれる`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            val record = WorkRecord(date = "2026-06-20", deliveryCount = 10)

            vm.save(record)
            advanceUntilIdle()

            coVerify { dao.upsert(record) }
        }

    @Test
    fun `deleteでdao_deleteが呼ばれる`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            val record = WorkRecord(date = "2026-06-20")

            vm.delete(record)
            advanceUntilIdle()

            coVerify { dao.delete(record) }
        }

    @Test
    fun `saveAndWaitでdao_upsertが呼ばれる`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            val record = WorkRecord(date = "2026-06-25", deliveryCount = 5)

            vm.saveAndWait(record)

            coVerify { dao.upsert(record) }
        }

    // ── setAssignmentId が recordForDate に反映される ────────────

    @Test
    fun `setAssignmentId後のrecordForDateは設定した案件IDで検索する`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, dao) = makeVmWithDao()
            coEvery { dao.recordForDate(any(), any()) } returns null

            vm.setAssignmentId("job_02")
            vm.recordForDate("2026-06-10")

            coVerify { dao.recordForDate("2026-06-10", "job_02") }
        }

    private fun makeVmWithDao(): Pair<ReportViewModel, com.rodgers.haireel.db.WorkRecordDao> {
        val mockApp = io.mockk.mockk<android.app.Application>(relaxed = true)
        val mockDao = io.mockk.mockk<com.rodgers.haireel.db.WorkRecordDao>(relaxed = true)
        io.mockk.every { mockDao.recordsForPeriodFlow(any(), any(), any()) } returns
            kotlinx.coroutines.flow.flowOf(emptyList())
        return ReportViewModel(mockApp, mockDao) to mockDao
    }
}
