package com.rodgers.haireel.util

import org.junit.Assert.*
import org.junit.Test

class AddressValidatorTest {

    // ── validate: 正常系 ──────────────────────────────────────────

    @Test
    fun `住所キーワードがあればissueなし`() {
        val result = AddressValidator.validate("東京都新宿区西新宿1-1-1")
        assertFalse(result.hasIssue)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `住所キーワードがなければNO_ADDRESS_KEYWORD issue`() {
        val result = AddressValidator.validate("テスト会社 本社ビル")
        assertTrue(result.issues.contains(AddressValidator.Issue.NO_ADDRESS_KEYWORD))
    }

    // ── validate: 郵便番号 ────────────────────────────────────────

    @Test
    fun `郵便番号が含まれればPOSTAL_CODE issue`() {
        val result = AddressValidator.validate("〒160-0023 東京都新宿区")
        assertTrue(result.issues.contains(AddressValidator.Issue.POSTAL_CODE))
    }

    @Test
    fun `郵便番号の数字部分が抽出される`() {
        val result = AddressValidator.validate("〒160-0023 東京都新宿区")
        assertNotNull(result.extractedPostalCode)
        assertTrue(result.extractedPostalCode!!.contains("160"))
    }

    @Test
    fun `郵便番号はcleaned後に除去される`() {
        val result = AddressValidator.validate("〒160-0023 東京都新宿区")
        assertFalse(result.cleaned.contains("160-0023"))
    }

    @Test
    fun `郵便番号があっても住所キーワードあればNO_ADDRESS_KEYWORDは出ない`() {
        val result = AddressValidator.validate("〒160-0023 東京都新宿区")
        assertFalse(result.issues.contains(AddressValidator.Issue.NO_ADDRESS_KEYWORD))
    }

    // ── validate: 電話番号 ────────────────────────────────────────

    @Test
    fun `電話番号が含まれればPHONE_NUMBER issue`() {
        val result = AddressValidator.validate("東京都新宿区 03-1234-5678")
        assertTrue(result.issues.contains(AddressValidator.Issue.PHONE_NUMBER))
    }

    @Test
    fun `郵便番号と電話番号が両方あれば2つのissue`() {
        val result = AddressValidator.validate("〒160-0023 東京都新宿区 03-1234-5678")
        assertTrue(result.issues.contains(AddressValidator.Issue.POSTAL_CODE))
        assertTrue(result.issues.contains(AddressValidator.Issue.PHONE_NUMBER))
        assertEquals(2, result.issues.size)
    }

    // ── hasIssue ──────────────────────────────────────────────────

    @Test
    fun `hasIssueはissueがあるときtrue`() {
        assertTrue(AddressValidator.validate("テスト会社").hasIssue)
    }

    @Test
    fun `hasIssueはissueがないときfalse`() {
        assertFalse(AddressValidator.validate("東京都新宿区").hasIssue)
    }

    // ── toHyphenFormat ────────────────────────────────────────────

    @Test
    fun `7桁数字をハイフン付きに変換する`() {
        assertEquals("160-0023", AddressValidator.toHyphenFormat("1600023"))
    }

    @Test
    fun `7桁以外はそのまま返す`() {
        assertEquals("123456", AddressValidator.toHyphenFormat("123456"))
        assertEquals("12345678", AddressValidator.toHyphenFormat("12345678"))
    }
}
