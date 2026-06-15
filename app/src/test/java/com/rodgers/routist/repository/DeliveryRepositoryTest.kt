package com.rodgers.routist.repository

import android.app.Application
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodgers.routist.db.AppDatabase
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.DeliveryGroup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeliveryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var repo: DeliveryRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = ctx.getSharedPreferences("test_delivery_prefs", android.content.Context.MODE_PRIVATE)
        repo = DeliveryRepository(db, prefs)
    }

    @After
    fun tearDown() {
        db.close()
        prefs.edit().clear().apply()
    }

    // ── Delivery CRUD ────────────────────────────────────────

    @Test
    fun `saveDeliveriesとloadDeliveriesのラウンドトリップ`() = runBlocking {
        val deliveries = listOf(
            Delivery(id = "d1", order = 1, address = "東京都新宿区1-1"),
            Delivery(id = "d2", order = 2, address = "東京都渋谷区2-2")
        )
        repo.saveDeliveries("g1", deliveries)
        val loaded = repo.loadDeliveries("g1")

        assertEquals(2, loaded.size)
        assertEquals("d1", loaded[0].id)
        assertEquals("東京都新宿区1-1", loaded[0].address)
        assertEquals("d2", loaded[1].id)
    }

    @Test
    fun `saveDeliveriesは既存データを置き換える`() = runBlocking {
        repo.saveDeliveries("g1", listOf(Delivery(id = "old", order = 1, address = "旧住所")))
        repo.saveDeliveries("g1", listOf(
            Delivery(id = "new1", order = 1, address = "新住所A"),
            Delivery(id = "new2", order = 2, address = "新住所B")
        ))
        val loaded = repo.loadDeliveries("g1")

        assertEquals(2, loaded.size)
        assertFalse("旧データは消えるはず", loaded.any { it.id == "old" })
        assertTrue(loaded.any { it.id == "new1" })
    }

    @Test
    fun `空リストを保存するとloadは空を返す`() = runBlocking {
        repo.saveDeliveries("g1", listOf(Delivery(id = "d1", order = 1, address = "住所")))
        repo.saveDeliveries("g1", emptyList())
        val loaded = repo.loadDeliveries("g1")

        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `別グループのデータは混在しない`() = runBlocking {
        repo.saveDeliveries("g1", listOf(Delivery(id = "d1", order = 1, address = "G1住所")))
        repo.saveDeliveries("g2", listOf(Delivery(id = "d2", order = 1, address = "G2住所")))

        val g1 = repo.loadDeliveries("g1")
        val g2 = repo.loadDeliveries("g2")

        assertEquals(1, g1.size)
        assertEquals("G1住所", g1[0].address)
        assertEquals(1, g2.size)
        assertEquals("G2住所", g2[0].address)
    }

    @Test
    fun `completedやgeocoded等のフラグが保持される`() = runBlocking {
        val d = Delivery(
            id = "d1", order = 1, address = "住所",
            isCompleted = true, isGeocoded = true,
            lat = 35.689, lng = 139.691,
            note = "メモ", packageCount = 3
        )
        repo.saveDeliveries("g1", listOf(d))
        val loaded = repo.loadDeliveries("g1")[0]

        assertTrue(loaded.isCompleted)
        assertTrue(loaded.isGeocoded)
        assertEquals(35.689, loaded.lat, 0.001)
        assertEquals(139.691, loaded.lng, 0.001)
        assertEquals("メモ", loaded.note)
        assertEquals(3, loaded.packageCount)
    }

    // ── Group CRUD ───────────────────────────────────────────

    @Test
    fun `saveGroupsとloadInitialDataでグループが復元される`() = runBlocking {
        val groups = listOf(
            DeliveryGroup(id = "g1", name = "グループA"),
            DeliveryGroup(id = "g2", name = "グループB")
        )
        repo.saveGroups(groups)
        val data = repo.loadInitialData()

        assertEquals(2, data.groups.size)
        assertTrue(data.groups.any { it.id == "g1" && it.name == "グループA" })
        assertTrue(data.groups.any { it.id == "g2" && it.name == "グループB" })
    }

    @Test
    fun `deleteGroupでグループと配達先が一緒に削除される`() = runBlocking {
        val group = DeliveryGroup(id = "g1", name = "削除対象")
        repo.saveGroups(listOf(group))
        repo.saveDeliveries("g1", listOf(Delivery(id = "d1", order = 1, address = "住所")))

        repo.deleteGroup("g1")

        val loaded = repo.loadDeliveries("g1")
        assertTrue("配達先も削除されるはず", loaded.isEmpty())
    }

    @Test
    fun `deleteGroupは他グループのデータを消さない`() = runBlocking {
        repo.saveGroups(listOf(
            DeliveryGroup(id = "g1", name = "削除"),
            DeliveryGroup(id = "g2", name = "残す")
        ))
        repo.saveDeliveries("g1", listOf(Delivery(id = "d1", order = 1, address = "G1")))
        repo.saveDeliveries("g2", listOf(Delivery(id = "d2", order = 1, address = "G2")))

        repo.deleteGroup("g1")

        val g2deliveries = repo.loadDeliveries("g2")
        assertEquals(1, g2deliveries.size)
        assertEquals("G2", g2deliveries[0].address)
    }

    // ── loadInitialData 移行ロジック ─────────────────────────

    @Test
    fun `DBが空のときSharedPrefsからグループを移行する`() = runBlocking {
        val gson = com.google.gson.Gson()
        val groupsJson = gson.toJson(listOf(
            mapOf("id" to "g_old", "name" to "旧グループ", "colorHex" to "#FF0000", "patternId" to -1)
        ))
        prefs.edit().putString("groups", groupsJson).apply()

        val data = repo.loadInitialData()

        assertTrue("旧グループが移行されるはず", data.groups.any { it.id == "g_old" })
    }

    @Test
    fun `DBが空のときSharedPrefsから配達先も移行する`() = runBlocking {
        val gson = com.google.gson.Gson()
        val groupsJson = gson.toJson(listOf(
            mapOf("id" to "g1", "name" to "グループ", "colorHex" to "#F44336", "patternId" to -1)
        ))
        val deliveryJson = gson.toJson(listOf(
            mapOf("id" to "d1", "order" to 1, "address" to "移行住所",
                  "isCompleted" to false, "isGeocoded" to false,
                  "lat" to 0.0, "lng" to 0.0, "packageCount" to 0)
        ))
        prefs.edit()
            .putString("groups", groupsJson)
            .putString("group_g1", deliveryJson)
            .apply()

        val data = repo.loadInitialData()

        val deliveries = data.allDeliveries["g1"]
        assertNotNull("g1の配達先が移行されるはず", deliveries)
        assertEquals(1, deliveries?.size)
        assertEquals("移行住所", deliveries?.get(0)?.address)
    }

    @Test
    fun `DBにデータがあるときはSharedPrefsを無視する`() = runBlocking {
        val group = DeliveryGroup(id = "g_db", name = "DBのグループ")
        repo.saveGroups(listOf(group))

        // SharedPrefsに古いデータを仕込んでも無視される
        prefs.edit().putString("groups", """[{"id":"g_old","name":"古い","colorHex":"#000","patternId":-1}]""").apply()

        val data = repo.loadInitialData()

        assertEquals(1, data.groups.size)
        assertEquals("g_db", data.groups[0].id)
    }

    // ── SharedPreferences ヘルパー ───────────────────────────

    @Test
    fun `getCurrentGroupIdとsaveCurrentGroupIdのラウンドトリップ`() {
        assertNull(repo.getCurrentGroupId())
        repo.saveCurrentGroupId("g999")
        assertEquals("g999", repo.getCurrentGroupId())
    }

    @Test
    fun `getAreaHintとsaveAreaHintのラウンドトリップ`() {
        assertEquals("", repo.getAreaHint("g1"))
        repo.saveAreaHint("g1", "新宿区")
        assertEquals("新宿区", repo.getAreaHint("g1"))
    }

    @Test
    fun `getAreaHintはグループIDが異なれば独立している`() {
        repo.saveAreaHint("g1", "新宿区")
        repo.saveAreaHint("g2", "渋谷区")
        assertEquals("新宿区", repo.getAreaHint("g1"))
        assertEquals("渋谷区", repo.getAreaHint("g2"))
    }

    @Test
    fun `migrateGlobalAreaHintがグループ別キーに移行する`() {
        prefs.edit().putString("area_hint", "豊島区").apply()

        val result = repo.migrateGlobalAreaHint("g1")

        assertEquals("豊島区", result)
        assertEquals("豊島区", repo.getAreaHint("g1"))
        assertNull("旧キーは削除されるはず", prefs.getString("area_hint", null))
    }

    @Test
    fun `migrateGlobalAreaHintで旧キーがなければnullを返す`() {
        val result = repo.migrateGlobalAreaHint("g1")
        assertNull(result)
    }

    @Test
    fun `clearGroupPrefsでグループ関連Prefsが一括削除される`() {
        repo.saveCurrentGroupId("g1")
        repo.saveAreaHint("g1", "新宿区")
        repo.saveFileUri("g1", "content://test")

        repo.clearGroupPrefs("g1")

        assertNull(repo.getFileUri("g1"))
        assertEquals("", repo.getAreaHint("g1"))
    }

    @Test
    fun `fileUriのsave_get_clearサイクル`() {
        assertNull(repo.getFileUri("g1"))
        repo.saveFileUri("g1", "content://media/123")
        assertEquals("content://media/123", repo.getFileUri("g1"))
        repo.clearFileUri("g1")
        assertNull(repo.getFileUri("g1"))
    }
}
