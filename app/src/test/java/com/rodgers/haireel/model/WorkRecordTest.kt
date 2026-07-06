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

    @Test
    fun `日またぎで22時から翌6時は480分`() {
        // 実際の夜勤シフト: 22:00 〜 翌06:00 → 8時間 = 480分
        val r = WorkRecord(date = "2026-06-01", startTime = "22:00", endTime = "06:00", endDateOffset = 1)
        assertEquals(480, r.workingMinutes)
    }

    @Test
    fun `00時00分から翌00時00分はendDateOffset=1で1440分`() {
        // 午前0時〜翌午前0時 = 24時間 = 1440分
        val r = WorkRecord(date = "2026-06-01", startTime = "00:00", endTime = "00:00", endDateOffset = 1)
        assertEquals(1440, r.workingMinutes)
    }

    @Test
    fun `endDateOffset=1で終了が開始より後でも翌日分が加算される`() {
        // 09:00 〜 翌10:00 → 25時間 = 1500分
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "10:00", endDateOffset = 1)
        assertEquals(1500, r.workingMinutes)
    }

    @Test
    fun `マイナスにならずcoerceAtLeastで0になる`() {
        // offset=0 で終了 < 開始 → coerceAtLeast(0) = 0
        val r = WorkRecord(date = "2026-06-01", startTime = "23:59", endTime = "00:00")
        assertEquals(0, r.workingMinutes)
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

    @Test
    fun `1分は0時間01分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "09:01")
        assertEquals("0時間01分", r.workingHoursText)
    }

    @Test
    fun `60分は1時間00分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "10:00")
        assertEquals("1時間00分", r.workingHoursText)
    }

    @Test
    fun `59分は0時間59分`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "09:59")
        assertEquals("0時間59分", r.workingHoursText)
    }

    @Test
    fun `負の稼働時間はworkingHoursTextが空文字`() {
        // 終了 < 開始 → workingMinutes=0 → workingHoursText=""
        val r = WorkRecord(date = "2026-06-01", startTime = "18:00", endTime = "09:00")
        assertEquals("", r.workingHoursText)
    }

    // ── noWork フィールド ─────────────────────────────────────

    @Test
    fun `noWorkのデフォルト値はfalse`() {
        val r = WorkRecord(date = "2026-06-01")
        assertFalse(r.noWork)
    }

    @Test
    fun `noWork=trueのときworkingMinutesは時刻から計算される`() {
        // noWork フラグは WorkRecord 自体の計算に影響しない
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "18:00", noWork = true)
        assertEquals(540, r.workingMinutes)
    }

    @Test
    fun `noWork=trueのときworkingHoursTextは正常に返る`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "09:00", endTime = "18:00", noWork = true)
        assertEquals("9時間00分", r.workingHoursText)
    }

    @Test
    fun `noWork=trueでstartTimeが空のときworkingMinutesは0`() {
        val r = WorkRecord(date = "2026-06-01", startTime = "", endTime = "", noWork = true)
        assertEquals(0, r.workingMinutes)
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
