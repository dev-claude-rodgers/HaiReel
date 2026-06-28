package com.rodgers.haireel.model

import org.junit.Assert.*
import org.junit.Test

class WorkRecordTest {

    // ── workingMinutes ────────────────────────────────────────

    @Test
    fun `通常の稼働時間を分で返す`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "18:00")
        assertEquals(540, r.workingMinutes)
    }

    @Test
    fun `深夜またぎはendDateOffsetで補正される`() {
        // 22:00 〜 翌02:00 → 4時間 = 240分
        val r = WorkRecord(date = "2026-06-01", startTime = "22:00", endTime = "02:00", endDateOffset = 1)
        assertEquals(240, r.workingMinutes)
    }

    @Test
    fun `開始終了が同じ時刻は0分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "09:00")
        assertEquals(0, r.workingMinutes)
    }

    @Test
    fun `終了が開始より早い場合は0を返す`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "18:00", endTime = "09:00")
        assertEquals(0, r.workingMinutes)
    }

    @Test
    fun `startTimeが空文字は0分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "", endTime = "18:00")
        assertEquals(0, r.workingMinutes)
    }

    @Test
    fun `endTimeが空文字は0分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "")
        assertEquals(0, r.workingMinutes)
    }

    @Test
    fun `不正なフォーマットは0分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "abc", endTime = "xyz")
        assertEquals(0, r.workingMinutes)
    }

    @Test
    fun `endDateOffset=2で翌々日またぎ`() {
        // 23:00 〜 翌々01:00 → 26時間 = 1560分
        val r = WorkRecord(date = "2026-06-01", startTime = "23:00", endTime = "01:00", endDateOffset = 2)
        assertEquals(1560, r.workingMinutes)
    }

    // ── workingHoursText ──────────────────────────────────────

    @Test
    fun `540分は9時間00分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "18:00")
        assertEquals("9時間00分", r.workingHoursText)
    }

    @Test
    fun `90分は1時間30分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "10:30")
        assertEquals("1時間30分", r.workingHoursText)
    }

    @Test
    fun `0分は空文字`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "", endTime = "")
        assertEquals("", r.workingHoursText)
    }

    // ── TenkoRecord.beforeDone / afterDone ────────────────────

    @Test
    fun `beforeTimeがnullはbeforeDoneがfalse`() {
        val r = com.rodgers.haireel.model.TenkoRecord(date = "2026-06-01", beforeTime = null)
        assertFalse(r.beforeDone)
    }

    @Test
    fun `beforeTimeが設定済みはbeforeDoneがtrue`() {
        val r = com.rodgers.haireel.model.TenkoRecord(date = "2026-06-01", beforeTime = "08:30")
        assertTrue(r.beforeDone)
    }

    @Test
    fun `afterTimeがnullはafterDoneがfalse`() {
        val r = com.rodgers.haireel.model.TenkoRecord(date = "2026-06-01", afterTime = null)
        assertFalse(r.afterDone)
    }

    @Test
    fun `afterTimeが設定済みはafterDoneがtrue`() {
        val r = com.rodgers.haireel.model.TenkoRecord(date = "2026-06-01", afterTime = "18:00")
        assertTrue(r.afterDone)
    }
}
