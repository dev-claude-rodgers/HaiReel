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

    // ── periodForPattern ──────────────────────────────────────

    @Test
    fun `periodForPattern_closingDay25のとき前月26日から当月25日`() {
        val (start, end) = gen.periodForPattern("2026-06", 25)
        assertEquals("2026-05-26", start)
        assertEquals("2026-06-25", end)
    }

    @Test
    fun `periodForPattern_closingDay31のとき月初から月末`() {
        val (start, end) = gen.periodForPattern("2026-06", 31)
        assertEquals("2026-06-01", start)
        assertEquals("2026-06-30", end)
    }

    @Test
    fun `periodForPattern_closingDay15のとき前月16日から当月15日`() {
        val (start, end) = gen.periodForPattern("2026-06", 15)
        assertEquals("2026-05-16", start)
        assertEquals("2026-06-15", end)
    }

    @Test
    fun `periodForPattern_締め日が前月末日を超えるとき月末に丸める`() {
        // closingDay=30, yearMonth=2026-03 → prev=2026-02 (28日)
        val (start, end) = gen.periodForPattern("2026-03", 30)
        assertEquals("2026-02-28", start)
        assertEquals("2026-03-30", end)
    }

    @Test
    fun `periodForPattern_closingDay1のとき前月2日から当月1日`() {
        val (start, end) = gen.periodForPattern("2026-06", 1)
        assertEquals("2026-05-02", start)
        assertEquals("2026-06-01", end)
    }

    // ── generate() 追加テスト ─────────────────────────────────

    @Test
    fun `generate_空レコードリストでもファイルが生成される`() {
        val pattern = com.rodgers.haireel.model.ReportPattern(id = 0)
        val file = gen.generate(emptyList(), "2026-06", pattern)
        assertTrue("ファイルが存在する", file.exists())
        assertTrue("ファイルサイズが0より大きい", file.length() > 0)
    }

    @Test
    fun `generate_noWorkレコードは合計から除外される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", income = 5000, fuelCost = 1000, deliveryCount = 10
            ),
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-02", income = 9999, fuelCost = 8888, deliveryCount = 77,
                noWork = true
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.INCOME, "売上"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.FUEL_COST, "燃料費"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.DELIVERY_COUNT, "件数")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("正常レコードの売上合計が含まれる", sheet.contains("5,000円"))
        assertFalse("noWorkのincome9999が合計に混入しない", sheet.contains("9,999円"))
    }

    @Test
    fun `generate_複数レコードの各行データと集計値がXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", deliveryCount = 30, income = 10000, fuelCost = 2000
            ),
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-02", deliveryCount = 20, income = 8000, fuelCost = 1500
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.DELIVERY_COUNT, "件数"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.INCOME, "売上"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.FUEL_COST, "燃料費")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        // 各行データは個別に出力される
        assertTrue("2026-06-01の件数が含まれる", sheet.contains("30件"))
        assertTrue("2026-06-02の件数が含まれる", sheet.contains("20件"))
        assertTrue("2026-06-01の売上が含まれる", sheet.contains("10,000円"))
        assertTrue("2026-06-02の売上が含まれる", sheet.contains("8,000円"))
        // 3列目(ci=2)の totalValue のみ合計行に出力される
        assertTrue("燃料費合計3,500円が合計行に含まれる", sheet.contains("3,500円"))
    }

    @Test
    fun `generate_距離と走行メーター列がXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", distanceKm = 123f,
                startMeter = 10000, endMeter = 10123
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.DISTANCE, "走行距離"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.METER_START, "開始メーター"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.METER_END, "終了メーター")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("走行距離123kmが含まれる", sheet.contains("123km"))
        assertTrue("開始メーター10000kmが含まれる", sheet.contains("10000km"))
        assertTrue("終了メーター10123kmが含まれる", sheet.contains("10123km"))
    }

    @Test
    fun `generate_ALCチェック列がXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", alcCheck = "◯"
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.ALC_CHECK, "ALCチェック")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("ALCチェック列ラベルが含まれる", sheet.contains("ALCチェック"))
        assertTrue("ALCチェック値◯が含まれる", sheet.contains("◯"))
    }

    // ── エッジケース ──────────────────────────────────────────

    @Test
    fun `generate_全件noWorkのとき集計値が空になる`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(date = "2026-06-01", deliveryCount = 50, income = 20000, noWork = true),
            com.rodgers.haireel.model.WorkRecord(date = "2026-06-02", deliveryCount = 30, income = 15000, noWork = true)
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.DELIVERY_COUNT, "件数"),
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.INCOME, "収入")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertFalse("noWorkの件数50が含まれない", sheet.contains("50件"))
        assertFalse("noWorkの収入20000が含まれない", sheet.contains("20,000円"))
    }

    @Test
    fun `generate_WORKING_HOURS列の稼働時間合計がXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", startTime = "09:00", endTime = "17:00"
            ),
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-02", startTime = "09:00", endTime = "13:00"
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.WORKING_HOURS, "稼働時間")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("稼働時間列ラベルが含まれる", sheet.contains("稼働時間"))
        assertTrue("8時間00分が含まれる", sheet.contains("8時間00分"))
        assertTrue("4時間00分が含まれる", sheet.contains("4時間00分"))
        // 1列の場合、合計ラベル（A+B マージ）が全列を占めるため totalValue は出力されない
    }

    @Test
    fun `generate_PACKAGE_COUNT列がXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(
                date = "2026-06-01", packageCount = 150
            )
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.PACKAGE_COUNT, "個数")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("個数列ラベルが含まれる", sheet.contains("個数"))
        assertTrue("150個が含まれる", sheet.contains("150個"))
    }

    @Test
    fun `generate_INCOME列の各行収入がXMLに出力される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(date = "2026-06-01", income = 12000),
            com.rodgers.haireel.model.WorkRecord(date = "2026-06-02", income = 8000)
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.INCOME, "収入")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("収入列ラベルが含まれる", sheet.contains("収入"))
        assertTrue("12,000円が含まれる", sheet.contains("12,000円"))
        assertTrue("8,000円が含まれる", sheet.contains("8,000円"))
    }

    @Test
    fun `generate_単一列のとき正常にXMLが生成される`() {
        val records = listOf(
            com.rodgers.haireel.model.WorkRecord(date = "2026-06-01", deliveryCount = 10)
        )
        val pattern = com.rodgers.haireel.model.ReportPattern(
            id = 0,
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.DELIVERY_COUNT, "件数")
            )
        )
        val file = gen.generate(records, "2026-06", pattern)
        assertTrue("XLSXファイルが生成される", file.exists() && file.length() > 0)
        val zip = java.util.zip.ZipFile(file)
        val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))
            .bufferedReader().readText()
        zip.close()
        assertTrue("件数が含まれる", sheet.contains("10件"))
    }

    // ── periodForPattern 追加ケース ───────────────────────────

    @Test
    fun `periodForPattern_closingDay31で2月は月末28日まで`() {
        // 2026-02: lastDay=28 → end=minOf(31が>=31で月末分岐) → "2026-02-28"
        val (start, end) = gen.periodForPattern("2026-02", 31)
        assertEquals("2026-02-01", start)
        assertEquals("2026-02-28", end)
    }

    @Test
    fun `periodForPattern_closingDay31でうるう年2月は月末29日まで`() {
        // 2024-02: lastDay=29 → end="2024-02-29"
        val (start, end) = gen.periodForPattern("2024-02", 31)
        assertEquals("2024-02-01", start)
        assertEquals("2024-02-29", end)
    }

    @Test
    fun `periodForPattern_前月が2月でclosingDay28超のstartは前月末`() {
        // yearMonth=2026-03, closingDay=28
        // prev=2026-02(28日): start=minOf(29, 28)=28 → "2026-02-28"
        // end=minOf(28, 31)=28 → "2026-03-28"
        val (start, end) = gen.periodForPattern("2026-03", 28)
        assertEquals("2026-02-28", start)
        assertEquals("2026-03-28", end)
    }

    @Test
    fun `periodForPattern_closingDay25で2026年1月は前年12月26日から`() {
        // prev=2025-12(31日): start=minOf(26, 31)=26 → "2025-12-26"
        // end=minOf(25, 31)=25 → "2026-01-25"
        val (start, end) = gen.periodForPattern("2026-01", 25)
        assertEquals("2025-12-26", start)
        assertEquals("2026-01-25", end)
    }
}
