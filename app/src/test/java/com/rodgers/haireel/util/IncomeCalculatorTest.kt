package com.rodgers.haireel.util

import com.rodgers.haireel.model.ReportPattern
import org.junit.Assert.*
import org.junit.Test

class IncomeCalculatorTest {

    private fun pattern(paymentType: Int, unitPrice: Int) = ReportPattern(
        id = 0,
        paymentType = paymentType,
        unitPrice = unitPrice
    )

    // ── paymentType=0: 個建て ────────────────────────────────────

    @Test
    fun `個建てはpackageCount×unitPrice`() {
        val p = pattern(0, 150)
        assertEquals(1500, calcIncome(p, delivCount = 10, workMinutes = 0, packageCount = 10))
    }

    @Test
    fun `個建てでpackageCountが0は0円`() {
        val p = pattern(0, 150)
        assertEquals(0, calcIncome(p, delivCount = 0, workMinutes = 0, packageCount = 0))
    }

    @Test
    fun `個建てはpackageCountを使いdelivCountを使わない`() {
        val p = pattern(0, 200)
        // delivCount=5, packageCount=8 → 200×8=1600
        assertEquals(1600, calcIncome(p, delivCount = 5, workMinutes = 0, packageCount = 8))
    }

    @Test
    fun `packageCount省略時はdelivCountをpackageCountとして使う`() {
        val p = pattern(0, 200)
        assertEquals(1000, calcIncome(p, delivCount = 5, workMinutes = 0))
    }

    // ── paymentType=1: 車建て（日当） ────────────────────────────

    @Test
    fun `車建てはunitPriceそのまま`() {
        val p = pattern(1, 15000)
        assertEquals(15000, calcIncome(p, delivCount = 99, workMinutes = 480))
    }

    @Test
    fun `車建ては件数や時間に関係なく固定`() {
        val p = pattern(1, 12000)
        assertEquals(12000, calcIncome(p, delivCount = 0, workMinutes = 0))
    }

    // ── paymentType=2: 時間制 ────────────────────────────────────

    @Test
    fun `時間制は時間（分÷60整数除算）×unitPrice`() {
        val p = pattern(2, 1200)
        // 480分 = 8時間 → 1200×8 = 9600
        assertEquals(9600, calcIncome(p, delivCount = 0, workMinutes = 480))
    }

    @Test
    fun `時間制で端数は切り捨て`() {
        val p = pattern(2, 1000)
        // 90分 = 1.5時間 → 切り捨て1時間 → 1000
        assertEquals(1000, calcIncome(p, delivCount = 0, workMinutes = 90))
    }

    @Test
    fun `時間制で0分は0円`() {
        val p = pattern(2, 1200)
        assertEquals(0, calcIncome(p, delivCount = 0, workMinutes = 0))
    }

    @Test
    fun `時間制で59分は0時間扱いで0円`() {
        val p = pattern(2, 1200)
        assertEquals(0, calcIncome(p, delivCount = 0, workMinutes = 59))
    }

    @Test
    fun `時間制で60分は1時間扱いでunitPrice`() {
        val p = pattern(2, 1200)
        assertEquals(1200, calcIncome(p, delivCount = 0, workMinutes = 60))
    }

    // ── paymentType=3以上: なし ──────────────────────────────────

    @Test
    fun `paymentType3は0円`() {
        val p = pattern(3, 10000)
        assertEquals(0, calcIncome(p, delivCount = 100, workMinutes = 480))
    }

    @Test
    fun `paymentType未定義は0円`() {
        val p = pattern(99, 10000)
        assertEquals(0, calcIncome(p, delivCount = 100, workMinutes = 480))
    }
}
