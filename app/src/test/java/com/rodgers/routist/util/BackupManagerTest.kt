package com.rodgers.routist.util

import org.junit.Assert.*
import org.junit.Test

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
    fun `ヘッダーが1バイトだけ違う場合は非暗号化と判定される`() {
        val data = byteArrayOf(0x52, 0x53, 0x54, 0x43) + ByteArray(100)  // 最後が0x42でなく0x43
        assertFalse(BackupManager.isEncryptedData(data))
    }
}
