package com.rodgers.routist.excel

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ExcelGeneratorTest {

    private val gen = ExcelGenerator(mockk<Context>(relaxed = true))

    // ── displayLen ────────────────────────────────────────────

    @Test
    fun `displayLenで空文字は0`() {
        assertEquals(0, gen.displayLen(""))
    }

    @Test
    fun `displayLenでASCII文字は1文字あたり1`() {
        assertEquals(5, gen.displayLen("hello"))
    }

    @Test
    fun `displayLenで全角ひらがなは1文字あたり2`() {
        assertEquals(6, gen.displayLen("あいう"))
    }

    @Test
    fun `displayLenで全角カタカナは1文字あたり2`() {
        assertEquals(8, gen.displayLen("アイウエ"))
    }

    @Test
    fun `displayLenで漢字は1文字あたり2`() {
        assertEquals(4, gen.displayLen("東京"))
    }

    @Test
    fun `displayLenで混在文字列は正しく計算される`() {
        // "A" = 1, "東" = 2, "B" = 1  → 合計4
        assertEquals(4, gen.displayLen("A東B"))
    }

    @Test
    fun `displayLenで全角英数字は1文字あたり2`() {
        // FF01 以上: 全角！ = U+FF01
        assertEquals(2, gen.displayLen("！"))
    }

    // ── colLetter ─────────────────────────────────────────────

    @Test
    fun `colLetterで0はA`() {
        assertEquals("A", gen.colLetter(0))
    }

    @Test
    fun `colLetterで25はZ`() {
        assertEquals("Z", gen.colLetter(25))
    }

    @Test
    fun `colLetterで26はAA`() {
        assertEquals("AA", gen.colLetter(26))
    }

    @Test
    fun `colLetterで27はAB`() {
        assertEquals("AB", gen.colLetter(27))
    }

    @Test
    fun `colLetterで51はAZ`() {
        assertEquals("AZ", gen.colLetter(51))
    }

    @Test
    fun `colLetterで52はBA`() {
        assertEquals("BA", gen.colLetter(52))
    }

    // ── calcWidth ─────────────────────────────────────────────

    @Test
    fun `calcWidthは最低8を返す`() {
        val w = gen.calcWidth("A", listOf("B"))
        assertTrue("最低幅8以上", w >= 8.0)
    }

    @Test
    fun `calcWidthはheaderとvaluesの最大長に基づく`() {
        val w = gen.calcWidth("日付", listOf("2026/01/01", "合計"))
        // "2026/01/01" = 10文字（ASCII）→ displayLen=10, +4=14
        assertEquals(14.0, w, 0.01)
    }

    @Test
    fun `calcWidthでheaderが最長のとき正しく計算される`() {
        val w = gen.calcWidth("東京都新宿区", listOf("短"))
        // "東京都新宿区" = 12（全角6文字）→ +4=16
        assertEquals(16.0, w, 0.01)
    }
}
