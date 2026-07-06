package com.rodgers.haireel.util

import org.junit.Assert.*
import org.junit.Test

class TimeSlotColorTest {

    // ── null / 空白 ───────────────────────────────────────────

    @Test
    fun `nullはnullを返す`() {
        assertNull(TimeSlotColor.colorFor(null))
    }

    @Test
    fun `空文字はnullを返す`() {
        assertNull(TimeSlotColor.colorFor(""))
    }

    @Test
    fun `空白のみはnullを返す`() {
        assertNull(TimeSlotColor.colorFor("   "))
    }

    // ── テンプレート一致 ──────────────────────────────────────

    @Test
    fun `テンプレート名と完全一致した場合はその色を返す`() {
        val templates = listOf(
            AppSettings.TimeSlotTemplate(name = "午前便", colorHex = "#1565C0"),
            AppSettings.TimeSlotTemplate(name = "午後便", colorHex = "#E65100")
        )
        val color = TimeSlotColor.colorFor("午前便", templates)
        assertNotNull(color)
    }

    @Test
    fun `テンプレートに存在しない名前は時刻推測にフォールバックする`() {
        val templates = listOf(
            AppSettings.TimeSlotTemplate(name = "午前便", colorHex = "#1565C0")
        )
        // "13:00" はテンプレートにないので時刻推測
        val color = TimeSlotColor.colorFor("13:00", templates)
        assertNotNull(color)
    }

    // ── 午前キーワード ────────────────────────────────────────

    @Test
    fun `午前を含む場合はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("午前便"))
    }

    @Test
    fun `午前中を含む場合もnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("午前中"))
    }

    // ── 時刻パターン HH:MM ───────────────────────────────────

    @Test
    fun `9時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("9:00"))
    }

    @Test
    fun `10時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("10:00"))
    }

    @Test
    fun `12時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("12:00"))
    }

    @Test
    fun `13時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("13:00"))
    }

    @Test
    fun `15時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("15:00"))
    }

    @Test
    fun `17時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("17:00"))
    }

    @Test
    fun `19時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("19:00"))
    }

    @Test
    fun `21時はnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("21:00"))
    }

    // ── 先頭数字のみのパターン ────────────────────────────────

    @Test
    fun `先頭が数字のみのパターンはnon-nullを返す`() {
        assertNotNull(TimeSlotColor.colorFor("10時"))
    }

    // ── 時刻を含まない場合 ───────────────────────────────────

    @Test
    fun `日本語文字だけで時刻も午前も含まない場合はnullを返す`() {
        assertNull(TimeSlotColor.colorFor("特別便"))
    }

    @Test
    fun `記号のみはnullを返す`() {
        assertNull(TimeSlotColor.colorFor("---"))
    }

    // ── PALETTEの確認 ────────────────────────────────────────

    @Test
    fun `PALETTEは12色を持つ`() {
        assertEquals(12, TimeSlotColor.PALETTE.size)
    }

    @Test
    fun `PALETTEの各色は先頭がシャープ記号`() {
        TimeSlotColor.PALETTE.forEach { hex ->
            assertTrue("$hex が # で始まらない", hex.startsWith("#"))
        }
    }
}
