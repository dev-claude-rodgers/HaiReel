package com.rodgers.routist.viewmodel

import org.junit.Assert.*
import org.junit.Test

class ReportViewModelTest {

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
        val mockDao = io.mockk.mockk<com.rodgers.routist.db.WorkRecordDao>(relaxed = true)
        io.mockk.every { mockDao.recordsForMonthFlow(any(), any()) } returns
            kotlinx.coroutines.flow.flowOf(emptyList())
        return ReportViewModel(mockApp, mockDao)
    }
}
