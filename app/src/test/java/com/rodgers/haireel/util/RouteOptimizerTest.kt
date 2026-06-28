package com.rodgers.haireel.util

import com.rodgers.haireel.model.Delivery
import org.junit.Assert.*
import org.junit.Test

class RouteOptimizerTest {

    private fun delivery(order: Int, lat: Double, lng: Double) = Delivery(
        order = order,
        address = "テスト住所$order",
        lat = lat,
        lng = lng,
        isGeocoded = true
    )

    private fun ungeocoded(order: Int) = Delivery(
        order = order,
        address = "未ジオコード$order",
        isGeocoded = false
    )

    @Test
    fun `空リストはそのまま返る`() {
        val result = RouteOptimizer.optimize(emptyList(), 35.68, 139.76)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `1件はそのまま返る`() {
        val d = delivery(1, 35.68, 139.76)
        val result = RouteOptimizer.optimize(listOf(d), 35.0, 139.0)
        assertEquals(1, result.size)
        assertEquals(d.order, result[0].order)
    }

    @Test
    fun `最も近い地点から順に並ぶ`() {
        // 出発点 (35.0, 139.0) から
        // A (35.1, 139.0) が近く、B (36.0, 139.0) が遠い
        val near = delivery(1, 35.1, 139.0)
        val far  = delivery(2, 36.0, 139.0)
        val result = RouteOptimizer.optimize(listOf(far, near), 35.0, 139.0)
        assertEquals(near.order, result[0].order)
        assertEquals(far.order, result[1].order)
    }

    @Test
    fun `ジオコードされていない住所は末尾に付く`() {
        // ジオコード済み2件 + 未ジオコード1件
        val near       = delivery(1, 35.1, 139.0)
        val far        = delivery(2, 36.0, 139.0)
        val unGeocoded = ungeocoded(3)
        val result = RouteOptimizer.optimize(listOf(unGeocoded, far, near), 35.0, 139.0)
        // 未ジオコードは末尾
        assertFalse(result[2].isGeocoded)
        // ジオコード済みが先頭2件
        assertTrue(result[0].isGeocoded)
        assertTrue(result[1].isGeocoded)
    }

    @Test
    fun `全件未ジオコードの場合は元の順序で返る`() {
        val list = listOf(ungeocoded(1), ungeocoded(2), ungeocoded(3))
        val result = RouteOptimizer.optimize(list, 35.0, 139.0)
        assertEquals(listOf(1, 2, 3), result.map { it.order })
    }
}
