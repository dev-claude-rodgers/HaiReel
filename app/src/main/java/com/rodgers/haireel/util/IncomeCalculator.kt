package com.rodgers.haireel.util

import com.rodgers.haireel.model.ReportPattern

fun calcIncome(
    pattern: ReportPattern,
    delivCount: Int,
    workMinutes: Int,
    packageCount: Int = delivCount
): Int = when (pattern.paymentType) {
    0    -> pattern.unitPrice * packageCount        // 個建て（個数×単価）
    1    -> pattern.unitPrice                       // 車建て（日当）
    2    -> pattern.unitPrice * (workMinutes / 60)  // 時間制（時間×単価）
    else -> 0
}
