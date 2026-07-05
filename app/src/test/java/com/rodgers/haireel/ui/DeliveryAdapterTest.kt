package com.rodgers.haireel.ui

import com.rodgers.haireel.model.Delivery
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.spyk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeliveryAdapterTest {

    private lateinit var adapter: DeliveryAdapter

    private fun makeDelivery(id: String, order: Int = 1, completed: Boolean = false) =
        Delivery(id = id, order = order, address = id, isCompleted = completed)

    @Before
    fun setUp() {
        adapter = spyk(DeliveryAdapter())
        every { adapter.notifyDataSetChanged() }               just Runs
        every { adapter.notifyItemMoved(any(), any()) }         just Runs
        every { adapter.notifyItemRangeChanged(any(), any()) }  just Runs
        every { adapter.notifyItemRangeInserted(any(), any()) } just Runs
        every { adapter.notifyItemRangeRemoved(any(), any()) }  just Runs
    }

    // ── submitList / getCurrentList ───────────────────────────

    @Test
    fun `submitListで内容が更新される`() {
        val list = listOf(makeDelivery("a"), makeDelivery("b"))
        adapter.submitList(list)
        assertEquals(list, adapter.getCurrentList())
    }

    @Test
    fun `submitListは空リストを受け取れる`() {
        adapter.submitList(listOf(makeDelivery("a")))
        adapter.submitList(emptyList())
        assertTrue(adapter.getCurrentList().isEmpty())
    }

    @Test
    fun `submitListはisDragging中は無視される`() {
        val initial = listOf(makeDelivery("a"))
        adapter.submitList(initial)
        adapter.isDragging = true
        adapter.submitList(listOf(makeDelivery("b"), makeDelivery("c")))
        assertEquals(initial, adapter.getCurrentList())
    }

    @Test
    fun `getItemCountはsubmitList後の件数を返す`() {
        adapter.submitList(listOf(makeDelivery("a"), makeDelivery("b"), makeDelivery("c")))
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun `submitListで件数が増えても正しく反映される`() {
        adapter.submitList(listOf(makeDelivery("a"), makeDelivery("b")))
        adapter.submitList(listOf(makeDelivery("a"), makeDelivery("b"), makeDelivery("c")))
        assertEquals(3, adapter.itemCount)
        assertEquals("c", adapter.getCurrentList()[2].id)
    }

    @Test
    fun `submitListで件数が減っても正しく反映される`() {
        adapter.submitList(listOf(makeDelivery("a"), makeDelivery("b"), makeDelivery("c")))
        adapter.submitList(listOf(makeDelivery("a")))
        assertEquals(1, adapter.itemCount)
    }

    // ── moveItem ──────────────────────────────────────────────

    @Test
    fun `moveItemで先頭と2番目が入れ替わる`() {
        adapter.submitList(listOf(makeDelivery("a"), makeDelivery("b"), makeDelivery("c")))
        adapter.moveItem(0, 1)
        val ids = adapter.getCurrentList().map { it.id }
        assertEquals(listOf("b", "a", "c"), ids)
    }

    @Test
    fun `moveItemで末尾へ移動できる`() {
        adapter.submitList(listOf(makeDelivery("a"), makeDelivery("b"), makeDelivery("c")))
        adapter.moveItem(0, 2)
        val ids = adapter.getCurrentList().map { it.id }
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test
    fun `moveItemでマイナスインデックスは無視される`() {
        val list = listOf(makeDelivery("a"), makeDelivery("b"))
        adapter.submitList(list)
        adapter.moveItem(-1, 0)
        assertEquals(list, adapter.getCurrentList())
    }

    @Test
    fun `moveItemで範囲外インデックスは無視される`() {
        val list = listOf(makeDelivery("a"), makeDelivery("b"))
        adapter.submitList(list)
        adapter.moveItem(0, 5)
        assertEquals(list, adapter.getCurrentList())
    }

    @Test
    fun `moveItemで空リストはクラッシュしない`() {
        adapter.moveItem(0, 1)
        assertTrue(adapter.getCurrentList().isEmpty())
    }

    // ── 選択操作 ──────────────────────────────────────────────

    @Test
    fun `clearSelectionでselectedIdsが空になる`() {
        adapter.selectedIds.add("a")
        adapter.selectedIds.add("b")
        adapter.clearSelection()
        assertTrue(adapter.selectedIds.isEmpty())
    }

    @Test
    fun `selectAllで指定IDが全て選択される`() {
        val ids = setOf("a", "b", "c")
        adapter.selectAll(ids)
        assertEquals(ids, adapter.selectedIds)
    }

    @Test
    fun `selectAllは以前の選択をリセットする`() {
        adapter.selectedIds.add("z")
        adapter.selectAll(setOf("a", "b"))
        assertFalse("z" in adapter.selectedIds)
        assertTrue("a" in adapter.selectedIds)
        assertTrue("b" in adapter.selectedIds)
    }
}
