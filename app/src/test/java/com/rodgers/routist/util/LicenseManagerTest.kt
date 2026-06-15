package com.rodgers.routist.util

import org.junit.Assert.*
import org.junit.Test

class LicenseManagerTest {

    @Test
    fun `generateKey が isValidKey を通過する`() {
        repeat(10) {
            val formatted = LicenseManager.generateKey()
            val raw = formatted.replace("-", "")
            assertTrue("生成キー '$formatted' が無効と判定された", LicenseManager.isValidKey(raw))
        }
    }

    @Test
    fun `キーの長さが16文字でない場合は無効`() {
        assertFalse(LicenseManager.isValidKey("SHORT"))
        assertFalse(LicenseManager.isValidKey("AAAAAAAAAAAAAAAAA"))  // 17文字
    }

    @Test
    fun `ペイロードが改ざんされたキーは無効`() {
        val raw = LicenseManager.generateKey().replace("-", "")
        val tampered = "X" + raw.substring(1)
        assertFalse(LicenseManager.isValidKey(tampered))
    }

    @Test
    fun `チェックサム部分が改ざんされたキーは無効`() {
        val raw = LicenseManager.generateKey().replace("-", "")
        val tampered = raw.substring(0, 8) + "00000000"
        assertFalse(LicenseManager.isValidKey(tampered))
    }

    @Test
    fun `全て同じ文字のキーは無効`() {
        assertFalse(LicenseManager.isValidKey("AAAAAAAAAAAAAAAA"))
        assertFalse(LicenseManager.isValidKey("0000000000000000"))
    }
}
