package com.rodgers.routist.excel

import org.junit.Assert.*
import org.junit.Test

class TenkoExcelGeneratorTest {

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
}
