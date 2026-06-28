package com.rodgers.haireel.db

import com.rodgers.haireel.model.DeliveryGroup
import org.junit.Assert.*
import org.junit.Test

class DeliveryGroupEntityMappingTest {

    // ── toEntity / toGroup 往復変換 ───────────────────────────────

    @Test
    fun `toEntityで全フィールドが保存される`() {
        val group = DeliveryGroup(
            id = "g1", name = "テスト案件",
            colorHex = "#FF5722", patternId = 3
        )
        val entity = group.toEntity(sortOrder = 2)

        assertEquals("g1", entity.id)
        assertEquals("テスト案件", entity.name)
        assertEquals("#FF5722", entity.colorHex)
        assertEquals(3, entity.patternId)
        assertEquals(2, entity.sortOrder)
    }

    @Test
    fun `toGroupで全フィールドが復元される`() {
        val entity = DeliveryGroupEntity(
            id = "g2", name = "別案件",
            colorHex = "#2196F3", patternId = 7, sortOrder = 1
        )
        val group = entity.toGroup()

        assertEquals("g2", group.id)
        assertEquals("別案件", group.name)
        assertEquals("#2196F3", group.colorHex)
        assertEquals(7, group.patternId)
    }

    @Test
    fun `patternIdがデフォルト(-1)のとき往復後も-1を保つ`() {
        val group = DeliveryGroup(id = "g3", name = "未設定案件")
        assertEquals(-1, group.patternId)

        val restored = group.toEntity(0).toGroup()
        assertEquals(-1, restored.patternId)
    }

    @Test
    fun `patternIdを設定してtoEntityしtoGroupしても値が保持される`() {
        val group = DeliveryGroup(id = "g4", name = "帳票あり案件", patternId = 12)
        val restored = group.toEntity(0).toGroup()
        assertEquals(12, restored.patternId)
    }

    @Test
    fun `sortOrderはtoGroupに反映されない`() {
        // toGroup() は sortOrder フィールドを DeliveryGroup に含まない
        val entity = DeliveryGroupEntity(id = "g5", name = "並び順", sortOrder = 99)
        val group = entity.toGroup()
        // DeliveryGroup に sortOrder がないことを確認（コンパイル時に型で保証）
        assertNotNull(group)
    }

    @Test
    fun `colorHexのデフォルト値はエンティティと一致する`() {
        val group = DeliveryGroup(id = "g6", name = "デフォルト色")
        val entity = group.toEntity(0)
        // DeliveryGroup.colorHex と DeliveryGroupEntity.colorHex のデフォルトが一致
        assertEquals(group.colorHex, entity.colorHex)
    }
}
