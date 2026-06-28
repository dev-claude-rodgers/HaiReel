package com.rodgers.haireel.util

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
}
