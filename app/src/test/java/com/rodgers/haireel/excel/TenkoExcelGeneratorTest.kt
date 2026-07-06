package com.rodgers.haireel.excel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rodgers.haireel.model.TenkoRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TenkoExcelGeneratorTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    // ── toAlcPresence ─────────────────────────────────────────

    @Test
    fun `toAlcPresenceでnullは空文字`() {
        assertEquals("", TenkoExcelGenerator.toAlcPresence(null))
    }

    @Test
    fun `toAlcPresenceで値があれば有`() {
        assertEquals("有", TenkoExcelGenerator.toAlcPresence(0.0))
        assertEquals("有", TenkoExcelGenerator.toAlcPresence(0.15))
    }

    // ── toDrunk ───────────────────────────────────────────────

    @Test
    fun `toDrunkでnullは空文字`() {
        assertEquals("", TenkoExcelGenerator.toDrunk(null))
    }

    @Test
    fun `toDrunkで0_0以下は無`() {
        assertEquals("無", TenkoExcelGenerator.toDrunk(0.0))
        assertEquals("無", TenkoExcelGenerator.toDrunk(-0.01))
    }

    @Test
    fun `toDrunkで0より大きければ有`() {
        assertEquals("有", TenkoExcelGenerator.toDrunk(0.01))
        assertEquals("有", TenkoExcelGenerator.toDrunk(0.15))
    }

    // ── toCondition ───────────────────────────────────────────

    @Test
    fun `toConditionで両方nullは空文字`() {
        assertEquals("", TenkoExcelGenerator.toCondition(null, null))
    }

    @Test
    fun `toConditionでhealthがfalseなら要確認`() {
        assertEquals("要確認", TenkoExcelGenerator.toCondition(false, null))
        assertEquals("要確認", TenkoExcelGenerator.toCondition(false, false))
    }

    @Test
    fun `toConditionでfatigueがtrueなら要確認`() {
        assertEquals("要確認", TenkoExcelGenerator.toCondition(true, true))
        assertEquals("要確認", TenkoExcelGenerator.toCondition(null, true))
    }

    @Test
    fun `toConditionで正常なら異常なし`() {
        assertEquals("異常なし", TenkoExcelGenerator.toCondition(true, false))
    }

    // ── toRunStatus ───────────────────────────────────────────

    @Test
    fun `toRunStatusで両方nullは空文字`() {
        assertEquals("", TenkoExcelGenerator.toRunStatus(null, null))
    }

    @Test
    fun `toRunStatusでaccidentがtrueなら要確認`() {
        assertEquals("要確認", TenkoExcelGenerator.toRunStatus(true, null))
        assertEquals("要確認", TenkoExcelGenerator.toRunStatus(true, true))
    }

    @Test
    fun `toRunStatusでvehicleがfalseなら要確認`() {
        assertEquals("要確認", TenkoExcelGenerator.toRunStatus(false, false))
        assertEquals("要確認", TenkoExcelGenerator.toRunStatus(null, false))
    }

    @Test
    fun `toRunStatusで正常なら異常なし`() {
        assertEquals("異常なし", TenkoExcelGenerator.toRunStatus(false, true))
    }

    // ── toInspection ──────────────────────────────────────────

    @Test
    fun `toInspectionでnullは空文字`() {
        assertEquals("", TenkoExcelGenerator.toInspection(null))
    }

    @Test
    fun `toInspectionでtrueは○`() {
        assertEquals("○", TenkoExcelGenerator.toInspection(true))
    }

    @Test
    fun `toInspectionでfalseは×`() {
        assertEquals("×", TenkoExcelGenerator.toInspection(false))
    }

    // ── generate() ───────────────────────────────────────────

    @Test
    fun `generate_空リストでもファイルが生成される`() {
        val file = TenkoExcelGenerator(ctx).generate(emptyList(), "2026-07")
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun `generate_出力ファイルはZIP形式`() {
        val file = TenkoExcelGenerator(ctx).generate(emptyList(), "2026-07")
        val bytes = file.readBytes()
        // XLSX は ZIP: PK シグネチャ 0x50 0x4B
        assertEquals(0x50.toByte(), bytes[0])
        assertEquals(0x4B.toByte(), bytes[1])
    }

    @Test
    fun `generate_ZIPにsheet1が含まれる`() {
        val file = TenkoExcelGenerator(ctx).generate(emptyList(), "2026-07")
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("xl/worksheets/sheet1.xml")
            assertNotNull(entry)
        }
    }

    @Test
    fun `generate_点呼記録のbeforeTimeがsharedStringsに含まれる`() {
        val record = TenkoRecord(date = "2026-07-01", beforeTime = "08:30")
        val file = TenkoExcelGenerator(ctx).generate(listOf(record), "2026-07")
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("xl/sharedStrings.xml")
            assertNotNull(entry)
            val content = zip.getInputStream(entry).bufferedReader().readText()
            assertTrue(content.contains("08:30"))
        }
    }
}
