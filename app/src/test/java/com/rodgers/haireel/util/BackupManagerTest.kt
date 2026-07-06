package com.rodgers.haireel.util

import com.rodgers.haireel.model.TenkoRecord
import com.rodgers.haireel.model.WorkRecord
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BackupManagerTest {

    // ── isEncryptedData ───────────────────────────────────────

    @Test
    fun `暗号化ヘッダーで始まるバイト列は暗号化済みと判定される`() {
        val header = byteArrayOf(0x52, 0x53, 0x54, 0x42)  // "RSTB"
        val data   = header + ByteArray(100)
        assertTrue(BackupManager.isEncryptedData(data))
    }

    @Test
    fun `PK（ZIPシグネチャ）で始まるバイト列は非暗号化と判定される`() {
        val data = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(100)
        assertFalse(BackupManager.isEncryptedData(data))
    }

    @Test
    fun `4バイト未満のデータは非暗号化と判定される`() {
        val data = byteArrayOf(0x52, 0x53)
        assertFalse(BackupManager.isEncryptedData(data))
    }

    @Test
    fun `空配列は非暗号化と判定される`() {
        assertFalse(BackupManager.isEncryptedData(ByteArray(0)))
    }

    @Test
    fun `v2ヘッダーRSTCも暗号化済みと判定される`() {
        val header = byteArrayOf(0x52, 0x53, 0x54, 0x43)  // "RSTC"
        val data   = header + ByteArray(100)
        assertTrue(BackupManager.isEncryptedData(data))
    }

    @Test
    fun `ヘッダーが全く異なる場合は非暗号化と判定される`() {
        val data = byteArrayOf(0x41, 0x42, 0x43, 0x44) + ByteArray(100)  // "ABCD"
        assertFalse(BackupManager.isEncryptedData(data))
    }

    // ── toByteArray4 / toInt4 ────────────────────────────────

    @Test
    fun `toByteArray4_0のラウンドトリップ`() = with(BackupManager) {
        assertEquals(0, 0.toByteArray4().toInt4())
    }

    @Test
    fun `toByteArray4_正の値のラウンドトリップ`() = with(BackupManager) {
        val value = 0x01020304
        assertEquals(value, value.toByteArray4().toInt4())
    }

    @Test
    fun `toByteArray4_Int最大値のラウンドトリップ`() = with(BackupManager) {
        assertEquals(Int.MAX_VALUE, Int.MAX_VALUE.toByteArray4().toInt4())
    }

    @Test
    fun `toByteArray4_既知の値のバイト列が正しい`() = with(BackupManager) {
        val bytes = 65536.toByteArray4() // 0x00_01_00_00
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x00, 0x00), bytes)
    }

    // ── recordsToJson / recordFromJson ───────────────────────

    @Test
    fun `WorkRecord_フルデータのラウンドトリップが正しい`() {
        val original = WorkRecord(
            id            = 42L,
            date          = "2026-07-05",
            startTime     = "09:00",
            endTime       = "18:30",
            endDateOffset = 1,
            deliveryCount = 120,
            packageCount  = 135,
            distanceKm    = 55.5f,
            startMeter    = 10000,
            endMeter      = 10055,
            area          = "東京北部",
            alcCheck      = "○",
            remarks       = "雨天",
            income        = 18000,
            fuelCost      = 2500,
            assignmentId  = "A001",
            noWork        = false
        )
        val arr = BackupManager.recordsToJson(listOf(original))
        val restored = BackupManager.recordFromJson(arr.getJSONObject(0))
        assertEquals(original, restored)
    }

    @Test
    fun `WorkRecord_デフォルト値フィールドのラウンドトリップが正しい`() {
        val original = WorkRecord(date = "2026-01-01")
        val arr = BackupManager.recordsToJson(listOf(original))
        val restored = BackupManager.recordFromJson(arr.getJSONObject(0))
        assertEquals(original.date, restored.date)
        assertEquals(0, restored.deliveryCount)
        assertEquals("", restored.startTime)
        assertFalse(restored.noWork)
    }

    @Test
    fun `WorkRecord_distanceKmが小数値として正しく保持される`() {
        val original = WorkRecord(date = "2026-07-05", distanceKm = 12.345f)
        val arr = BackupManager.recordsToJson(listOf(original))
        val restored = BackupManager.recordFromJson(arr.getJSONObject(0))
        assertEquals(original.distanceKm, restored.distanceKm, 0.001f)
    }

    @Test
    fun `recordsToJson_複数レコードのリストサイズが保持される`() {
        val records = listOf(
            WorkRecord(date = "2026-07-01"),
            WorkRecord(date = "2026-07-02"),
            WorkRecord(date = "2026-07-03")
        )
        val arr = BackupManager.recordsToJson(records)
        assertEquals(3, arr.length())
    }

    @Test
    fun `recordsToJson_空リストは空JSONArrayを返す`() {
        assertEquals(0, BackupManager.recordsToJson(emptyList()).length())
    }

    @Test
    fun `recordFromJson_dateだけのJSONObjectからWorkRecordを生成できる`() {
        val j = JSONObject().apply { put("date", "2026-07-05") }
        val record = BackupManager.recordFromJson(j)
        assertEquals("2026-07-05", record.date)
        assertEquals(0, record.deliveryCount)
        assertEquals(0f, record.distanceKm)
        assertFalse(record.noWork)
    }

    // ── tenkoToJson / tenkoFromJson ──────────────────────────

    @Test
    fun `TenkoRecord_全フィールド指定のラウンドトリップが正しい`() {
        val original = TenkoRecord(
            id                = 7,
            date              = "2026-07-05",
            assignmentId      = "B002",
            beforeMethod      = "対面",
            beforeTime        = "08:45",
            beforeHealth      = true,
            beforeFatigue     = false,
            beforeAlcohol     = 0.00,
            beforeInspection  = true,
            beforeInstruction = "安全確認を徹底すること",
            beforeChecker     = "田中",
            afterMethod       = "電話",
            afterTime         = "18:30",
            afterHealth       = true,
            afterFatigue      = false,
            afterAlcohol      = 0.00,
            afterAccident     = false,
            afterVehicle      = true,
            afterInstruction  = "お疲れ様でした",
            afterChecker      = "佐藤",
            note              = "特記事項なし",
            vehicleNumber     = "品川 100 あ 1234"
        )
        val arr = BackupManager.tenkoToJson(listOf(original))
        val restored = BackupManager.tenkoFromJson(arr.getJSONObject(0))
        assertEquals(original.date,              restored.date)
        assertEquals(original.beforeMethod,      restored.beforeMethod)
        assertEquals(original.beforeTime,        restored.beforeTime)
        assertEquals(original.beforeHealth,      restored.beforeHealth)
        assertEquals(original.beforeFatigue,     restored.beforeFatigue)
        assertEquals(original.beforeAlcohol,     restored.beforeAlcohol)
        assertEquals(original.beforeInspection,  restored.beforeInspection)
        assertEquals(original.beforeInstruction, restored.beforeInstruction)
        assertEquals(original.beforeChecker,     restored.beforeChecker)
        assertEquals(original.afterMethod,       restored.afterMethod)
        assertEquals(original.afterTime,         restored.afterTime)
        assertEquals(original.afterHealth,       restored.afterHealth)
        assertEquals(original.afterFatigue,      restored.afterFatigue)
        assertEquals(original.afterAlcohol,      restored.afterAlcohol)
        assertEquals(original.afterAccident,     restored.afterAccident)
        assertEquals(original.afterVehicle,      restored.afterVehicle)
        assertEquals(original.afterInstruction,  restored.afterInstruction)
        assertEquals(original.afterChecker,      restored.afterChecker)
        assertEquals(original.note,              restored.note)
        assertEquals(original.vehicleNumber,     restored.vehicleNumber)
    }

    @Test
    fun `TenkoRecord_全nullable項目がnullのラウンドトリップが正しい`() {
        val original = TenkoRecord(date = "2026-07-05")
        val arr = BackupManager.tenkoToJson(listOf(original))
        val restored = BackupManager.tenkoFromJson(arr.getJSONObject(0))
        assertNull(restored.beforeMethod)
        assertNull(restored.beforeTime)
        assertNull(restored.beforeHealth)
        assertNull(restored.beforeFatigue)
        assertNull(restored.beforeAlcohol)
        assertNull(restored.beforeInspection)
        assertNull(restored.beforeInstruction)
        assertNull(restored.beforeChecker)
        assertNull(restored.afterMethod)
        assertNull(restored.afterTime)
        assertNull(restored.afterHealth)
        assertNull(restored.afterFatigue)
        assertNull(restored.afterAlcohol)
        assertNull(restored.afterAccident)
        assertNull(restored.afterVehicle)
        assertNull(restored.afterInstruction)
        assertNull(restored.afterChecker)
        assertNull(restored.note)
        assertNull(restored.vehicleNumber)
    }

    @Test
    fun `TenkoRecord_アルコール値の小数が保持される`() {
        val original = TenkoRecord(date = "2026-07-05", beforeAlcohol = 0.15, afterAlcohol = 0.05)
        val arr = BackupManager.tenkoToJson(listOf(original))
        val restored = BackupManager.tenkoFromJson(arr.getJSONObject(0))
        assertEquals(0.15, restored.beforeAlcohol!!, 0.001)
        assertEquals(0.05, restored.afterAlcohol!!, 0.001)
    }

    @Test
    fun `tenkoToJson_空リストは空JSONArrayを返す`() {
        assertEquals(0, BackupManager.tenkoToJson(emptyList()).length())
    }

    // ── noWork フィールドのシリアライズ ───────────────────────

    @Test
    fun `noWork=trueのWorkRecordはJSON化後も真のまま`() {
        val record = WorkRecord(date = "2026-07-06", noWork = true)
        val arr = BackupManager.recordsToJson(listOf(record))
        assertTrue(arr.getJSONObject(0).getBoolean("noWork"))
    }

    @Test
    fun `noWork=trueのフルラウンドトリップ`() {
        val original = WorkRecord(
            date = "2026-07-06",
            startTime = "09:00", endTime = "18:00",
            deliveryCount = 5, income = 10000,
            noWork = true
        )
        val arr = BackupManager.recordsToJson(listOf(original))
        val restored = BackupManager.recordFromJson(arr.getJSONObject(0))
        assertTrue(restored.noWork)
        assertEquals(original.date, restored.date)
        assertEquals(original.income, restored.income)
    }

    @Test
    fun `noWorkキーがないJSONはデフォルトfalseを返す`() {
        val json = org.json.JSONObject().apply { put("date", "2026-07-06") }
        val record = BackupManager.recordFromJson(json)
        assertFalse(record.noWork)
    }

    @Test
    fun `noWork=falseとtrueの混在リストは個別に正しく復元される`() {
        val r1 = WorkRecord(date = "2026-07-05", noWork = false)
        val r2 = WorkRecord(date = "2026-07-06", noWork = true)
        val arr = BackupManager.recordsToJson(listOf(r1, r2))
        assertFalse(BackupManager.recordFromJson(arr.getJSONObject(0)).noWork)
        assertTrue(BackupManager.recordFromJson(arr.getJSONObject(1)).noWork)
    }
}
