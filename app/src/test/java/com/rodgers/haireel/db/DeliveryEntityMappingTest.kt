package com.rodgers.haireel.db

import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.Room
import org.junit.Assert.*
import org.junit.Test

class DeliveryEntityMappingTest {

    private fun baseDelivery(
        id: String = "d1",
        order: Int = 0,
        address: String = "東京都新宿区1-1-1"
    ) = Delivery(id = id, order = order, address = address)

    @Test
    fun `toEntityで全フィールドが保存される`() {
        val d = Delivery(
            id = "id1",
            order = 3,
            name = "テスト店",
            address = "東京都新宿区1-1",
            geocodedAddress = "東京都新宿区1丁目1番",
            note = "メモ",
            photoUri = "file:///old.jpg",
            photoUris = listOf("file:///a.jpg", "file:///b.jpg"),
            timeSlot = "14:00-16:00",
            packageCount = 3,
            lat = 35.6895,
            lng = 139.6917,
            isCompleted = true,
            isGeocoded = true
        )
        val entity = d.toEntity("g1")

        assertEquals("id1", entity.id)
        assertEquals("g1", entity.groupId)
        assertEquals(3, entity.order)
        assertEquals("テスト店", entity.name)
        assertEquals("東京都新宿区1-1", entity.address)
        assertEquals("東京都新宿区1丁目1番", entity.geocodedAddress)
        assertEquals("メモ", entity.note)
        assertEquals("file:///old.jpg", entity.photoUri)
        assertNotNull(entity.photoUrisJson)
        assertEquals("14:00-16:00", entity.timeSlot)
        assertEquals(3, entity.packageCount)
        assertEquals(35.6895, entity.lat, 0.0001)
        assertEquals(139.6917, entity.lng, 0.0001)
        assertTrue(entity.isCompleted)
        assertTrue(entity.isGeocoded)
    }

    @Test
    fun `toDeliveryで全フィールドが復元される`() {
        val original = Delivery(
            id = "id2",
            order = 1,
            address = "大阪府大阪市北区2-2",
            name = "大阪店",
            isCompleted = true,
            isGeocoded = true,
            lat = 34.693,
            lng = 135.502
        )
        val restored = original.toEntity("g2").toDelivery()

        assertEquals("id2", restored.id)
        assertEquals(1, restored.order)
        assertEquals("大阪府大阪市北区2-2", restored.address)
        assertEquals("大阪店", restored.name)
        assertTrue(restored.isCompleted)
        assertTrue(restored.isGeocoded)
        assertEquals(34.693, restored.lat, 0.0001)
        assertEquals(135.502, restored.lng, 0.0001)
    }

    @Test
    fun `photoUrisのJSONラウンドトリップ`() {
        val uris = listOf("file:///photo1.jpg", "file:///photo2.jpg", "file:///photo3.jpg")
        val restored = baseDelivery().copy(photoUris = uris).toEntity("g1").toDelivery()

        assertEquals(uris, restored.photoUris)
    }

    @Test
    fun `roomsのJSONラウンドトリップ`() {
        val rooms = listOf(
            Room(number = "101", note = "1階"),
            Room(number = "202", isCompleted = true)
        )
        val restored = baseDelivery().copy(rooms = rooms).toEntity("g1").toDelivery()

        assertEquals(2, restored.rooms?.size)
        assertEquals("101", restored.rooms?.get(0)?.number)
        assertEquals("1階", restored.rooms?.get(0)?.note)
        assertEquals("202", restored.rooms?.get(1)?.number)
        assertTrue(restored.rooms?.get(1)?.isCompleted == true)
    }

    @Test
    fun `nullフィールドは復元後もnull`() {
        val restored = baseDelivery().toEntity("g1").toDelivery()

        assertNull(restored.name)
        assertNull(restored.note)
        assertNull(restored.photoUri)
        assertNull(restored.photoUris)
        assertNull(restored.rooms)
        assertNull(restored.timeSlot)
        assertNull(restored.geocodedAddress)
    }

    @Test
    fun `photoUrisがnullの場合はJSONもnull`() {
        val entity = baseDelivery().toEntity("g1")
        assertNull(entity.photoUrisJson)
    }

    @Test
    fun `roomsがnullの場合はJSONもnull`() {
        val entity = baseDelivery().toEntity("g1")
        assertNull(entity.roomsJson)
    }

    @Test
    fun `空のphotoUrisリストはラウンドトリップで空リストを返す`() {
        val restored = baseDelivery().copy(photoUris = emptyList()).toEntity("g1").toDelivery()
        assertTrue(restored.photoUris?.isEmpty() == true)
    }

    @Test
    fun `isCompleted=falseのデフォルト値はround-tripで保持される`() {
        val restored = baseDelivery().copy(isCompleted = false).toEntity("g1").toDelivery()
        assertFalse(restored.isCompleted)
    }

    @Test
    fun `rooms空リストはround-trip後も空リスト`() {
        val restored = baseDelivery().copy(rooms = emptyList()).toEntity("g1").toDelivery()
        assertNotNull(restored.rooms)
        assertTrue(restored.rooms!!.isEmpty())
    }

    @Test
    fun `nameKanaフィールドが保持される`() {
        val restored = baseDelivery().copy(nameKana = "シンジュクテン").toEntity("g1").toDelivery()
        assertEquals("シンジュクテン", restored.nameKana)
    }

    @Test
    fun `groupIdはtoEntityで指定した値が設定される`() {
        val entity = baseDelivery().toEntity("group-xyz")
        assertEquals("group-xyz", entity.groupId)
    }
}
