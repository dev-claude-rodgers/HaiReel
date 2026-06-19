package com.rodgers.routist.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rodgers.routist.db.TenkoDao
import com.rodgers.routist.model.TenkoRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class TenkoViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApp: Application
    private lateinit var mockDao: TenkoDao
    private lateinit var viewModel: TenkoViewModel

    private fun makeRecord(date: String, aid: String = "") = TenkoRecord(
        id = 0, date = date, assignmentId = aid,
        beforeMethod = "対面", beforeTime = "08:00",
        beforeHealth = true, beforeFatigue = false,
        beforeAlcohol = 0.0, beforeInspection = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockDao = mockk(relaxed = true)
        coEvery { mockDao.getByMonthFlow(any(), any()) } returns flowOf(emptyList())
        viewModel = TenkoViewModel(mockApp, mockDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 月ナビゲーション ──────────────────────────────────────

    @Test
    fun `初期yearMonthは今月`() {
        val expected = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        assertEquals(expected, viewModel.yearMonth.value)
    }

    @Test
    fun `previousMonthで1ヶ月前に移動する`() {
        val before = viewModel.yearMonth.value
        viewModel.previousMonth()
        val after = viewModel.yearMonth.value
        assertNotEquals(before, after)
        val beforeDate = LocalDate.parse("$before-01")
        val afterDate  = LocalDate.parse("$after-01")
        assertEquals(beforeDate.minusMonths(1), afterDate)
    }

    @Test
    fun `nextMonthで1ヶ月後に移動する`() {
        viewModel.previousMonth()
        val before = viewModel.yearMonth.value
        viewModel.nextMonth()
        val after = viewModel.yearMonth.value
        val beforeDate = LocalDate.parse("$before-01")
        val afterDate  = LocalDate.parse("$after-01")
        assertEquals(beforeDate.plusMonths(1), afterDate)
    }

    @Test
    fun `jumpToTodayで今月に戻る`() {
        viewModel.previousMonth()
        viewModel.previousMonth()
        viewModel.jumpToToday()
        val expected = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        assertEquals(expected, viewModel.yearMonth.value)
    }

    @Test
    fun `previousMonthを12回で1年前になる`() {
        val start = LocalDate.parse("${viewModel.yearMonth.value}-01")
        repeat(12) { viewModel.previousMonth() }
        val end = LocalDate.parse("${viewModel.yearMonth.value}-01")
        assertEquals(start.minusYears(1), end)
    }

    // ── 案件ID フィルタ ───────────────────────────────────────

    @Test
    fun `setAssignmentIdでassignmentIdが変わる`() {
        viewModel.setAssignmentId("case-1")
        assertEquals("case-1", viewModel.assignmentId.value)
    }

    @Test
    fun `初期assignmentIdは空文字`() {
        assertEquals("", viewModel.assignmentId.value)
    }

    // ── 乗務前点呼保存 ────────────────────────────────────────

    @Test
    fun `saveBeforeで新規レコードをinsertする`() = runTest {
        val date = viewModel.todayDate()
        viewModel.saveBefore(
            date = date, existing = null,
            method = "対面", time = "08:00",
            health = true, fatigue = false,
            alcohol = 0.0, inspection = true,
            instruction = "", checker = "田中"
        )
        coVerify { mockDao.insert(any()) }
    }

    @Test
    fun `saveBeforeで既存レコードはupdateする`() = runTest {
        val existing = makeRecord(viewModel.todayDate())
        viewModel.saveBefore(
            date = existing.date, existing = existing,
            method = "電話", time = "09:00",
            health = true, fatigue = false,
            alcohol = 0.0, inspection = true,
            instruction = "", checker = "鈴木"
        )
        coVerify { mockDao.update(any()) }
    }

    @Test
    fun `saveBeforeでcheckerが空白のときnullになる`() = runTest {
        val slot = slot<TenkoRecord>()
        coEvery { mockDao.insert(capture(slot)) } returns Unit
        viewModel.saveBefore(
            date = viewModel.todayDate(), existing = null,
            method = "対面", time = "08:00",
            health = true, fatigue = false,
            alcohol = 0.0, inspection = true,
            instruction = "", checker = ""
        )
        assertNull(slot.captured.beforeChecker)
    }

    // ── 乗務後点呼保存 ────────────────────────────────────────

    @Test
    fun `saveAfterで新規レコードをinsertする`() = runTest {
        val date = viewModel.todayDate()
        viewModel.saveAfter(
            date = date, existing = null,
            method = "対面", time = "18:00",
            health = true, fatigue = false,
            alcohol = 0.0, accident = false, vehicle = true,
            instruction = "", checker = "田中"
        )
        coVerify { mockDao.insert(any()) }
    }

    @Test
    fun `saveAfterでassignmentIdが反映される`() = runTest {
        viewModel.setAssignmentId("route-abc")
        val slot = slot<TenkoRecord>()
        coEvery { mockDao.insert(capture(slot)) } returns Unit
        viewModel.saveAfter(
            date = viewModel.todayDate(), existing = null,
            method = "対面", time = "18:00",
            health = true, fatigue = false,
            alcohol = 0.0, accident = false, vehicle = true,
            instruction = "", checker = ""
        )
        assertEquals("route-abc", slot.captured.assignmentId)
    }

    // ── todayDate ─────────────────────────────────────────────

    @Test
    fun `todayDateはISO形式の今日の日付`() {
        val expected = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        assertEquals(expected, viewModel.todayDate())
    }

    // ── delete / restore ─────────────────────────────────────

    @Test
    fun `deleteでdao_deleteが呼ばれる`() = runTest {
        val record = makeRecord(viewModel.todayDate())
        viewModel.delete(record)
        coVerify { mockDao.delete(record) }
    }

    @Test
    fun `restoreでdao_insertが呼ばれる`() = runTest {
        val record = makeRecord(viewModel.todayDate())
        viewModel.restore(record)
        coVerify { mockDao.insert(record) }
    }
}
