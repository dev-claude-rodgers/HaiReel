package com.rodgers.haireel.repository

import android.content.SharedPreferences
import com.rodgers.haireel.db.AppDatabase
import com.rodgers.haireel.db.DeliveryDao
import com.rodgers.haireel.db.DeliveryEntity
import com.rodgers.haireel.db.DeliveryGroupDao
import com.rodgers.haireel.db.DeliveryGroupEntity
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.DeliveryGroup
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeliveryRepositoryTest {

    @MockK private lateinit var mockDb: AppDatabase
    @MockK private lateinit var mockDeliveryDao: DeliveryDao
    @MockK private lateinit var mockGroupDao: DeliveryGroupDao

    private val data = mutableMapOf<String, String?>()
    private lateinit var repo: DeliveryRepository

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { mockDb.deliveryDao() } returns mockDeliveryDao
        every { mockDb.deliveryGroupDao() } returns mockGroupDao
        data.clear()
        repo = DeliveryRepository(mockDb, fakePrefs(data))
    }

    // ── SharedPreferences ファクトリ ──────────────────────────

    private fun fakePrefs(store: MutableMap<String, String?>): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { store[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { store.remove(firstArg()); editor }
        every { editor.apply() } just Runs
        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any<String>()) } answers { store[firstArg()] ?: secondArg() }
        every { prefs.edit() } returns editor
        return prefs
    }

    // ── saveDeliveries / loadDeliveries ────────────────────────

    @Test
    fun `saveDeliveriesで空リストのときdeleteByGroupを呼ぶ`() = runTest {
        coJustRun { mockDeliveryDao.deleteByGroup(any()) }

        repo.saveDeliveries("g1", emptyList())

        coVerify(exactly = 1) { mockDeliveryDao.deleteByGroup("g1") }
        coVerify(exactly = 0) { mockDeliveryDao.upsertAll(any()) }
    }

    @Test
    fun `saveDeliveriesで非空リストのときdeleteしてからupsertする`() = runTest {
        coJustRun { mockDeliveryDao.deleteByGroup(any()) }
        coJustRun { mockDeliveryDao.upsertAll(any()) }
        val delivery = Delivery(id = "d1", order = 1, address = "東京都新宿区")

        repo.saveDeliveries("g1", listOf(delivery))

        coVerify(exactly = 1) { mockDeliveryDao.deleteByGroup("g1") }
        coVerify(exactly = 1) { mockDeliveryDao.upsertAll(any()) }
    }

    @Test
    fun `loadDeliveriesでDAOの結果をDeliveryに変換して返す`() = runTest {
        coEvery { mockDeliveryDao.getByGroup("g1") } returns emptyList()

        val result = repo.loadDeliveries("g1")

        assertTrue(result.isEmpty())
    }

    // ── saveGroups / deleteGroup ───────────────────────────────

    @Test
    fun `saveGroupsでDAOのupsertAllを呼ぶ`() = runTest {
        coJustRun { mockGroupDao.upsertAll(any()) }
        val group = DeliveryGroup(id = "g1", name = "テスト")

        repo.saveGroups(listOf(group))

        coVerify(exactly = 1) { mockGroupDao.upsertAll(any()) }
    }

    @Test
    fun `deleteGroupでDAOのdeleteByIdとdeliveryDaoのdeleteByGroupを呼ぶ`() = runTest {
        coJustRun { mockGroupDao.deleteById(any()) }
        coJustRun { mockDeliveryDao.deleteByGroup(any()) }

        repo.deleteGroup("g1")

        coVerify { mockGroupDao.deleteById("g1") }
        coVerify { mockDeliveryDao.deleteByGroup("g1") }
    }

    // ── loadInitialData ────────────────────────────────────────

    @Test
    fun `loadInitialDataでグループが存在する場合はRoomから読み込む`() = runTest {
        val groupEntity = DeliveryGroupEntity(
            id = "g1", name = "テストグループ", colorHex = "#F44336", patternId = 0, sortOrder = 0
        )
        coEvery { mockGroupDao.count() } returns 1
        coEvery { mockGroupDao.getAll() } returns listOf(groupEntity)
        coEvery { mockDeliveryDao.getAll() } returns emptyList()

        val result = repo.loadInitialData()

        assertEquals(1, result.groups.size)
        assertEquals("テストグループ", result.groups[0].name)
        assertTrue(result.allDeliveries["g1"]?.isEmpty() == true)
    }

    @Test
    fun `loadInitialDataでグループが空の場合はマイグレーションを試みる`() = runTest {
        coEvery { mockGroupDao.count() } returns 0
        coJustRun { mockGroupDao.upsertAll(any()) }
        coEvery { mockDeliveryDao.count() } returns 0
        coEvery { mockDeliveryDao.getAll() } returns emptyList()

        val result = repo.loadInitialData()

        assertTrue(result.groups.isEmpty())
    }

    // ── SharedPreferences: getAreaHint / saveAreaHint ──────────

    @Test
    fun `saveAreaHintで保存した値をgetAreaHintで取得できる`() {
        repo.saveAreaHint("g1", "東京都新宿区")
        assertEquals("東京都新宿区", repo.getAreaHint("g1"))
    }

    @Test
    fun `getAreaHintで未設定の場合は空文字を返す`() {
        assertEquals("", repo.getAreaHint("g_unknown"))
    }

    @Test
    fun `getAreaHintでgroupIdが空の場合は空文字を返す`() {
        assertEquals("", repo.getAreaHint(""))
    }

    @Test
    fun `異なるグループIDは独立して管理される`() {
        repo.saveAreaHint("g1", "東京都新宿区")
        repo.saveAreaHint("g2", "大阪府大阪市")
        assertEquals("東京都新宿区", repo.getAreaHint("g1"))
        assertEquals("大阪府大阪市", repo.getAreaHint("g2"))
    }

    // ── migrateGlobalAreaHint ──────────────────────────────────

    @Test
    fun `旧グローバルキーが存在すればグループキーに移行してnullになる`() {
        data["area_hint"] = "茨城県つくば市"
        val result = repo.migrateGlobalAreaHint("g1")
        assertEquals("茨城県つくば市", result)
        assertEquals("茨城県つくば市", data["area_hint_g1"])
        assertNull(data["area_hint"])
    }

    @Test
    fun `旧グローバルキーがなければnullを返す`() {
        assertNull(repo.migrateGlobalAreaHint("g1"))
    }

    // ── getCurrentGroupId / saveCurrentGroupId ─────────────────

    @Test
    fun `saveCurrentGroupIdで保存した値をgetCurrentGroupIdで取得できる`() {
        repo.saveCurrentGroupId("g42")
        assertEquals("g42", repo.getCurrentGroupId())
    }

    @Test
    fun `getCurrentGroupIdで未設定の場合はnullを返す`() {
        assertNull(repo.getCurrentGroupId())
    }

    // ── clearGroupPrefs ────────────────────────────────────────

    @Test
    fun `clearGroupPrefsで全関連キーが削除される`() {
        data["group_g1"] = "x"; data["file_uri_g1"] = "y"
        data["download_file_uri_g1"] = "z"; data["area_hint_g1"] = "w"
        repo.clearGroupPrefs("g1")
        assertNull(data["group_g1"]); assertNull(data["file_uri_g1"])
        assertNull(data["download_file_uri_g1"]); assertNull(data["area_hint_g1"])
    }

    @Test
    fun `clearGroupPrefsは他グループのキーに影響しない`() {
        data["area_hint_g1"] = "東京都"; data["area_hint_g2"] = "大阪府"
        repo.clearGroupPrefs("g1")
        assertEquals("大阪府", data["area_hint_g2"])
    }

    // ── fileUri / downloadFileUri ──────────────────────────────

    @Test
    fun `saveFileUriとclearFileUriが動作する`() {
        repo.saveFileUri("g1", "content://test")
        assertEquals("content://test", repo.getFileUri("g1"))
        repo.clearFileUri("g1")
        assertNull(repo.getFileUri("g1"))
    }

    @Test
    fun `saveDownloadFileUriとclearDownloadFileUriが動作する`() {
        repo.saveDownloadFileUri("g1", "content://downloads/file")
        assertEquals("content://downloads/file", repo.getDownloadFileUri("g1"))
        repo.clearDownloadFileUri("g1")
        assertNull(repo.getDownloadFileUri("g1"))
    }

    // ── getAllDeliveries ───────────────────────────────────────

    @Test
    fun `getAllDeliveriesで全グループの配達先を返す`() = runTest {
        val entities = listOf(
            DeliveryEntity(id = "d1", groupId = "g1", order = 1, address = "東京都新宿区"),
            DeliveryEntity(id = "d2", groupId = "g2", order = 1, address = "大阪府大阪市")
        )
        coEvery { mockDeliveryDao.getAll() } returns entities

        val result = repo.getAllDeliveries()

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "d1" })
        assertTrue(result.any { it.id == "d2" })
    }

    @Test
    fun `getAllDeliveriesが空の場合は空リストを返す`() = runTest {
        coEvery { mockDeliveryDao.getAll() } returns emptyList()

        val result = repo.getAllDeliveries()

        assertTrue(result.isEmpty())
    }

    // ── loadDeliveries（データあり）──────────────────────────

    @Test
    fun `loadDeliveriesでグループ内の配達先リストを返す`() = runTest {
        val entities = listOf(
            DeliveryEntity(id = "d1", groupId = "g1", order = 1, address = "東京都渋谷区"),
            DeliveryEntity(id = "d2", groupId = "g1", order = 2, address = "東京都港区")
        )
        coEvery { mockDeliveryDao.getByGroup("g1") } returns entities

        val result = repo.loadDeliveries("g1")

        assertEquals(2, result.size)
        assertEquals("東京都渋谷区", result[0].address)
        assertEquals("東京都港区", result[1].address)
    }

    // ── normalizeAllAddressesToFullWidth ──────────────────────

    @Test
    fun `normalizeAllAddressesToFullWidthで半角住所はupsertAllが呼ばれる`() = runTest {
        val entity = DeliveryEntity(id = "d1", groupId = "g1", order = 1, address = "Tokyo")
        coEvery { mockDeliveryDao.getAll() } returns listOf(entity)
        coJustRun { mockDeliveryDao.upsertAll(any()) }

        repo.normalizeAllAddressesToFullWidth()

        coVerify(exactly = 1) { mockDeliveryDao.upsertAll(any()) }
    }

    @Test
    fun `normalizeAllAddressesToFullWidthで既に全角の場合はupsertAllが呼ばれない`() = runTest {
        val entity = DeliveryEntity(id = "d1", groupId = "g1", order = 1, address = "東京都新宿区")
        coEvery { mockDeliveryDao.getAll() } returns listOf(entity)

        repo.normalizeAllAddressesToFullWidth()

        coVerify(exactly = 0) { mockDeliveryDao.upsertAll(any()) }
    }

    @Test
    fun `normalizeAllAddressesToFullWidthで空リストの場合はupsertAllが呼ばれない`() = runTest {
        coEvery { mockDeliveryDao.getAll() } returns emptyList()

        repo.normalizeAllAddressesToFullWidth()

        coVerify(exactly = 0) { mockDeliveryDao.upsertAll(any()) }
    }
}
