package com.rodgers.haireel.model

import org.junit.Assert.*
import org.junit.Test

class TenkoRecordTest {

    @Test
    fun `beforeTimeがnullのときbeforeDoneはfalse`() {
        val r = TenkoRecord(date = "2026-07-06", beforeTime = null)
        assertFalse(r.beforeDone)
    }

    @Test
    fun `beforeTimeが設定されているときbeforeDoneはtrue`() {
        val r = TenkoRecord(date = "2026-07-06", beforeTime = "08:30")
        assertTrue(r.beforeDone)
    }

    @Test
    fun `afterTimeがnullのときafterDoneはfalse`() {
        val r = TenkoRecord(date = "2026-07-06", afterTime = null)
        assertFalse(r.afterDone)
    }

    @Test
    fun `afterTimeが設定されているときafterDoneはtrue`() {
        val r = TenkoRecord(date = "2026-07-06", afterTime = "18:00")
        assertTrue(r.afterDone)
    }
}
