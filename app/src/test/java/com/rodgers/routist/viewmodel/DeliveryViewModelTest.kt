package com.rodgers.routist.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.DeliveryGroup
import com.rodgers.routist.repository.DeliveryRepository
import com.rodgers.routist.util.GeocodingApi
import com.rodgers.routist.util.GeocodingManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var mockApp: Application
    private lateinit var mockRepo: DeliveryRepository
    private lateinit var mockGeocodingManager: GeocodingManager
    private lateinit var mockGeocodingApi: GeocodingApi
    private lateinit var viewModel: DeliveryViewModel

    private val group = DeliveryGroup(id = "g1", name = "テストグループ")
    private fun makeDelivery(id: String, order: Int = 1, completed: Boolean = false) =
        Delivery(id = id, order = order, address = "東京都新宿区$id", isCompleted = completed)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)
        mockGeocodingManager = mockk(relaxed = true)
        mockGeocodingApi = mockk(relaxed = true)
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(makeDelivery("d1"), makeDelivery("d2")))
        )
        every { mockRepo.getCurrentGroupId() } returns group.id
        every { mockRepo.getAreaHint(any()) } returns ""
        every { mockRepo.migrateGlobalAreaHint(any()) } returns null
        viewModel = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 初期化 ────────────────────────────────────────────────

    @Test
    fun `initで配達リストが読み込まれる`() {
        assertEquals(2, viewModel.deliveries.value.size)
        assertEquals(group.id, viewModel.currentGroupId.value)
    }

    // ── 完了操作 ──────────────────────────────────────────────

    @Test
    fun `toggleCompletedで完了フラグが反転する`() {
        viewModel.toggleCompleted("d1")
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertTrue("d1はcompletedになるはず", d1?.isCompleted == true)
    }

    @Test
    fun `toggleCompletedを2回呼ぶと元に戻る`() {
        viewModel.toggleCompleted("d1")
        viewModel.toggleCompleted("d1")
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertFalse("2回反転で元のfalseに戻るはず", d1?.isCompleted == true)
    }

    @Test
    fun `markAllCompletedで全件completedになる`() {
        viewModel.markAllCompleted()
        assertTrue(viewModel.deliveries.value.all { it.isCompleted })
    }

    @Test
    fun `resetAllCompletedで全件未完了になる`() {
        viewModel.markAllCompleted()
        viewModel.resetAllCompleted()
        assertTrue(viewModel.deliveries.value.none { it.isCompleted })
    }

    @Test
    fun `markAllCompletedで空リストはクラッシュしない`() {
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group), allDeliveries = mapOf(group.id to emptyList())
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi)
        vm.markAllCompleted()
        assertTrue(vm.deliveries.value.isEmpty())
    }

    // ── 削除操作 ──────────────────────────────────────────────

    @Test
    fun `deleteDeliveryで対象が削除される`() {
        viewModel.deleteDelivery("d1")
        val ids = viewModel.deliveries.value.map { it.id }
        assertFalse("d1は削除されるはず", "d1" in ids)
        assertTrue("d2は残るはず", "d2" in ids)
    }

    @Test
    fun `deleteDeliveryで順番が振り直される`() {
        viewModel.deleteDelivery("d1")
        assertEquals(listOf(1), viewModel.deliveries.value.map { it.order })
    }

    @Test
    fun `deleteDeliveriesで複数件削除できる`() {
        viewModel.deleteDeliveries(setOf("d1", "d2"))
        assertTrue(viewModel.deliveries.value.isEmpty())
    }

    // ── メモ・編集 ────────────────────────────────────────────

    @Test
    fun `editNoteでメモが更新される`() {
        viewModel.editNote("d1", "テストメモ")
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertEquals("テストメモ", d1?.note)
    }

    @Test
    fun `reorderDeliveriesで順序が変わる`() {
        val d2 = makeDelivery("d2", order = 1)
        val d1 = makeDelivery("d1", order = 2)
        viewModel.reorderDeliveries(listOf(d2, d1))
        val ids = viewModel.deliveries.value.map { it.id }
        assertEquals(listOf("d2", "d1"), ids)
    }

    // ── グループ切替 ──────────────────────────────────────────

    @Test
    fun `switchGroupでcurrentGroupIdが変わる`() {
        val group2 = DeliveryGroup(id = "g2", name = "グループ2")
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group, group2),
            allDeliveries = mapOf(
                group.id to listOf(makeDelivery("d1")),
                group2.id to listOf(makeDelivery("d3"), makeDelivery("d4"))
            )
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi)
        vm.switchGroup("g2")
        assertEquals("g2", vm.currentGroupId.value)
        assertEquals(2, vm.deliveries.value.size)
    }

    @Test
    fun `switchGroupで同じIDを指定しても二重処理されない`() {
        val beforeId = viewModel.currentGroupId.value
        viewModel.switchGroup(beforeId)
        assertEquals(beforeId, viewModel.currentGroupId.value)
    }

    @Test
    fun `createGroupでグループが追加される`() {
        val before = viewModel.groups.value.size
        viewModel.createGroup("新しいルート")
        assertEquals(before + 1, viewModel.groups.value.size)
    }

    @Test
    fun `deleteGroupで対象グループが削除される`() {
        viewModel.createGroup("削除対象")
        val newId = viewModel.groups.value.last().id
        viewModel.deleteGroup(newId)
        assertFalse(viewModel.groups.value.any { it.id == newId })
    }

    // ── アパート判定 ──────────────────────────────────────────

    @Test
    fun `isLikelyApartmentでマンションキーワードを検出する`() {
        val d = makeDelivery("a1").copy(address = "東京都新宿区西新宿マンション101号室")
        assertTrue(viewModel.isLikelyApartment(d))
    }

    @Test
    fun `isLikelyApartmentで戸建住所はfalse`() {
        val d = makeDelivery("a2").copy(address = "東京都新宿区西新宿1-1-1")
        assertFalse(viewModel.isLikelyApartment(d))
    }

    @Test
    fun `isLikelyApartmentで店名にキーワードがあっても検出する`() {
        val d = makeDelivery("a3").copy(name = "ハイツ新宿", address = "東京都新宿区1-1")
        assertTrue(viewModel.isLikelyApartment(d))
    }

    // ── エラーパス ────────────────────────────────────────────

    @Test
    fun `loadInitialData失敗時はerrorMessageが設定される`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("DB破損")
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi)
        assertEquals("データの読み込みに失敗しました", vm.errorMessage.value)
    }

    @Test
    fun `loadInitialData失敗時はdeliveriesが空リストのまま`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("DB破損")
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi)
        assertTrue(vm.deliveries.value.isEmpty())
    }

    @Test
    fun `toggleCompletedで存在しないIDは無視される`() {
        val before = viewModel.deliveries.value.size
        viewModel.toggleCompleted("存在しないID")
        assertEquals(before, viewModel.deliveries.value.size)
    }

    @Test
    fun `deleteDeliveryで存在しないIDは何も起きない`() {
        val before = viewModel.deliveries.value.size
        viewModel.deleteDelivery("存在しないID")
        assertEquals(before, viewModel.deliveries.value.size)
    }
}
