package com.rodgers.haireel.excel

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExcelGeneratorTest {

    private val tmpDir = File(System.getProperty("java.io.tmpdir")!!)
    private val ctx = mockk<Context>(relaxed = true).also {
        every { it.filesDir } returns tmpDir
        every { it.getExternalFilesDir(null) } returns null
    }
    private val gen = ExcelGenerator(ctx)

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
        // "2026/01/01" = displayLen=10 → 10*1.1+2=13.0
        assertEquals(13.0, w, 0.01)
    }

    @Test
    fun `calcWidthでheaderが最長のとき正しく計算される`() {
        val w = gen.calcWidth("東京都新宿区", listOf("短"))
        // "東京都新宿区" = displayLen=12 → 12*1.1+2=15.2
        assertEquals(15.2, w, 0.01)
    }

    // ── showTotal制御（合計行の出力） ─────────────────────────────

    @Test
    fun `合計行は常にXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", deliveryCount = 10,
                income = 5000, fuelCost = 1000, distanceKm = 50f
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(id = 0)
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("合計行テキストが含まれる", sheet.contains("合計"))
    }

    @Test
    fun `カスタム列ラベルがXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(date = "2026-06-01", deliveryCount = 5)
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(
                    com.rodgers.haireel.model.ColumnType.DELIVERY_COUNT, "出荷件数"
                )
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("カスタムラベルが含まれる", sheet.contains("出荷件数"))
    }
}
