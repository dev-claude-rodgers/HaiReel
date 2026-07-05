package com.rodgers.haireel.ui

import com.rodgers.haireel.model.WorkRecord
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DayEntryAdapterTest {

    private lateinit var adapter: DayEntryAdapter

    private fun makeEntry(date: String, record: WorkRecord? = null) =
        DayEntry(date = date, record = record)

    private fun makeRecord(date: String, deliveryCount: Int = 0) =
        WorkRecord(date = date, deliveryCount = deliveryCount)

    @Before
    fun setUp() {
        adapter = spyk(DayEntryAdapter(
            onTap    = {},
            onDelete = {},
            onShare  = {}
        ))
        every { adapter.notifyDataSetChanged() }               just Runs
        every { adapter.notifyItemMoved(any(), any()) }         just Runs
        every { adapter.notifyItemRangeChanged(any(), any()) }  just Runs
        every { adapter.notifyItemRangeInserted(any(), any()) } just Runs
        every { adapter.notifyItemRangeRemoved(any(), any()) }  just Runs
    }

    // ── 初期状態 ──────────────────────────────────────────────

    @Test
    fun `初期状態の件数は0`() {
        assertEquals(0, adapter.itemCount)
    }

    // ── submitList ────────────────────────────────────────────

    @Test
    fun `submitListで件数が更新される`() {
        adapter.submitList(listOf(makeEntry("2026-07-01"), makeEntry("2026-07-02")))
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `submitListは空リストを受け取れる`() {
        adapter.submitList(listOf(makeEntry("2026-07-01")))
        adapter.submitList(emptyList())
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun `submitListで件数が増えても正しく反映される`() {
        adapter.submitList(listOf(makeEntry("2026-07-01")))
        adapter.submitList(listOf(
            makeEntry("2026-07-01"),
            makeEntry("2026-07-02"),
            makeEntry("2026-07-03")
        ))
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun `submitListで件数が減っても正しく反映される`() {
        adapter.submitList(listOf(
            makeEntry("2026-07-01"),
            makeEntry("2026-07-02"),
            makeEntry("2026-07-03")
        ))
        adapter.submitList(listOf(makeEntry("2026-07-01")))
        assertEquals(1, adapter.itemCount)
    }

    @Test
    fun `同じリストを渡すと件数は変わらない`() {
        val list = listOf(makeEntry("2026-07-01"), makeEntry("2026-07-02"))
        adapter.submitList(list)
        adapter.submitList(list)
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `recordありとなしのエントリを混在できる`() {
        val list = listOf(
            makeEntry("2026-07-01", makeRecord("2026-07-01", 30)),
            makeEntry("2026-07-02"),
            makeEntry("2026-07-03", makeRecord("2026-07-03", 50))
        )
        adapter.submitList(list)
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun `submitList後に別の月のリストを渡すと正しく入れ替わる`() {
        adapter.submitList(listOf(
            makeEntry("2026-06-28"),
            makeEntry("2026-06-29"),
            makeEntry("2026-06-30")
        ))
        adapter.submitList(listOf(
            makeEntry("2026-07-01"),
            makeEntry("2026-07-02")
        ))
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `1件のリストをsubmitListしてから空にできる`() {
        adapter.submitList(listOf(makeEntry("2026-07-01")))
        assertEquals(1, adapter.itemCount)
        adapter.submitList(emptyList())
        assertEquals(0, adapter.itemCount)
    }
}
