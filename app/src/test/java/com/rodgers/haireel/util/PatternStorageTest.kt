package com.rodgers.haireel.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rodgers.haireel.model.ReportPattern
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PatternStorageTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // テスト間の干渉を避けるため SharedPreferences をクリア
        ctx.getSharedPreferences("report_patterns", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // ── save / get ──────────────────────────────────────────────

    @Test
    fun `saveしたパターンをgetで取得できる`() {
        val pattern = ReportPattern(
            id = 1, title = "テスト帳票",
            clientName = "テスト取引先", driverName = "テスト担当者",
            closingDay = 20
        )
        PatternStorage.save(ctx, pattern)
        val got = PatternStorage.get(ctx, 1)
        assertNotNull(got)
        assertEquals("テスト帳票", got!!.title)
        assertEquals("テスト取引先", got.clientName)
        assertEquals(20, got.closingDay)
    }

    @Test
    fun `saveでexcelColumnsが正しく保存される`() {
        val cols = listOf(
            com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.DELIVERY_COUNT, "出荷件数"),
            com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.FUEL_COST, "ガス代")
        )
        val pattern = ReportPattern(id = 2, excelColumns = cols, paymentType = 1, unitPrice = 5000)
        PatternStorage.save(ctx, pattern)
        val got = PatternStorage.get(ctx, 2)!!
        assertEquals(2, got.excelColumns.size)
        assertEquals("出荷件数", got.excelColumns[0].label)
        assertEquals("ガス代", got.excelColumns[1].label)
        assertEquals(1, got.paymentType)
        assertEquals(5000, got.unitPrice)
    }

    // ── delete: 回帰テスト ──

    @Test
    fun `deleteで全キーが削除されsave後デフォルト値に戻る`() {
        val original = ReportPattern(
            id = 10, title = "削除テスト",
            excelColumns = listOf(
                com.rodgers.haireel.model.ExcelColumn(com.rodgers.haireel.model.ColumnType.AREA, "エリア")
            ),
            paymentType = 0, unitPrice = 1000
        )
        PatternStorage.save(ctx, original)
        PatternStorage.delete(ctx, 10)

        // 同じIDで新規パターンを保存
        PatternStorage.save(ctx, ReportPattern(id = 10, title = "新規"))
        val restored = PatternStorage.get(ctx, 10)!!

        // デフォルト値が返るはず（削除前の値が残っていてはいけない）
        assertTrue("excelColumnsはデフォルト列を含む", restored.excelColumns.isNotEmpty())
        assertEquals("paymentTypeはデフォルト3", 3, restored.paymentType)
        assertEquals("unitPriceはデフォルト0", 0, restored.unitPrice)
    }

    @Test
    fun `deleteでidsリストからパターンが除去される`() {
        PatternStorage.save(ctx, ReportPattern(id = 20, title = "A"))
        PatternStorage.save(ctx, ReportPattern(id = 21, title = "B"))
        PatternStorage.delete(ctx, 20)
        val ids = PatternStorage.getIds(ctx)
        assertFalse("削除したidがリストに残らない", ids.contains(20))
        assertTrue("残ったidはリストに存在する", ids.contains(21))
    }

    @Test
    fun `deleteしたidはgetでnullを返す`() {
        PatternStorage.save(ctx, ReportPattern(id = 30, title = "消える"))
        PatternStorage.delete(ctx, 30)
        assertNull(PatternStorage.get(ctx, 30))
    }

    @Test
    fun `activeIdが削除されたとき残ったidに切り替わる`() {
        PatternStorage.save(ctx, ReportPattern(id = 40, title = "先頭"))
        PatternStorage.save(ctx, ReportPattern(id = 41, title = "後続"))
        PatternStorage.setActiveId(ctx, 40)
        PatternStorage.delete(ctx, 40)
        val active = PatternStorage.getActiveId(ctx)
        assertNotEquals("削除されたidがactiveIdに残らない", 40, active)
    }

    // ── getAll / nextId ──────────────────────────────────────────

    @Test
    fun `getAllで保存済みパターンを全件取得できる`() {
        PatternStorage.save(ctx, ReportPattern(id = 50, title = "X"))
        PatternStorage.save(ctx, ReportPattern(id = 51, title = "Y"))
        val all = PatternStorage.getAll(ctx)
        assertTrue(all.any { it.id == 50 })
        assertTrue(all.any { it.id == 51 })
    }

    @Test
    fun `nextIdは呼ぶたびに異なる値を返す`() {
        val id1 = PatternStorage.nextId(ctx)
        val id2 = PatternStorage.nextId(ctx)
        assertNotEquals(id1, id2)
    }

    // ── ensureDefault ────────────────────────────────────────────

    @Test
    fun `パターンが空のときensureDefaultがデフォルトパターンを作成する`() {
        val pattern = PatternStorage.ensureDefault(ctx)
        assertNotNull(pattern)
        val ids = PatternStorage.getIds(ctx)
        assertTrue("デフォルトパターンがidsに追加される", ids.isNotEmpty())
    }
}
