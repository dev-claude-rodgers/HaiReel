package com.rodgers.routist.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.DeliveryGroup
import com.rodgers.routist.repository.DeliveryRepository
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
    private lateinit var viewModel: DeliveryViewModel

    private val group = DeliveryGroup(id = "g1", name = "テストグループ")
    private fun makeDelivery(id: String, order: Int = 1, completed: Boolean = false) =
        Delivery(id = id, order = order, address = "東京都新宿区$id", isCompleted = completed)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockApp = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)

        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(makeDelivery("d1"), makeDelivery("d2")))
        )
        every { mockRepo.getCurrentGroupId() } returns group.id
        every { mockRepo.getAreaHint(any()) } returns ""
        every { mockRepo.migrateGlobalAreaHint(any()) } returns null

        viewModel = DeliveryViewModel(mockApp, mockRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initで配達リストが読み込まれる`() {
        assertEquals(2, viewModel.deliveries.value?.size)
        assertEquals(group.id, viewModel.currentGroupId.value)
    }

    @Test
    fun `toggleCompletedで完了フラグが反転する`() {
        viewModel.toggleCompleted("d1")

        val d1 = viewModel.deliveries.value?.find { it.id == "d1" }
        assertTrue("d1はcompletedになるはず", d1?.isCompleted == true)
    }

    @Test
    fun `toggleCompletedを2回呼ぶと元に戻る`() {
        viewModel.toggleCompleted("d1")
        viewModel.toggleCompleted("d1")

        val d1 = viewModel.deliveries.value?.find { it.id == "d1" }
        assertFalse("2回反転で元のfalseに戻るはず", d1?.isCompleted == true)
    }

    @Test
    fun `markAllCompletedで全件completedになる`() {
        viewModel.markAllCompleted()

        val allCompleted = viewModel.deliveries.value?.all { it.isCompleted } == true
        assertTrue(allCompleted)
    }

    @Test
    fun `resetAllCompletedで全件未完了になる`() {
        viewModel.markAllCompleted()
        viewModel.resetAllCompleted()

        val allIncomplete = viewModel.deliveries.value?.none { it.isCompleted } == true
        assertTrue(allIncomplete)
    }

    @Test
    fun `deleteDeliveryで対象が削除される`() {
        viewModel.deleteDelivery("d1")

        val ids = viewModel.deliveries.value?.map { it.id }
        assertFalse("d1は削除されるはず", ids?.contains("d1") == true)
        assertTrue("d2は残るはず", ids?.contains("d2") == true)
    }

    @Test
    fun `deleteDeliveryで順番が振り直される`() {
        viewModel.deleteDelivery("d1")

        val orders = viewModel.deliveries.value?.map { it.order }
        assertEquals(listOf(1), orders)
    }

    @Test
    fun `isLikelyApartmentでマンションキーワードを検出する`() {
        val manshon = makeDelivery("a1").copy(address = "東京都新宿区西新宿マンション101号室")
        assertTrue(viewModel.isLikelyApartment(manshon))
    }

    @Test
    fun `isLikelyApartmentで戸建住所はfalse`() {
        val house = makeDelivery("a2").copy(address = "東京都新宿区西新宿1-1-1")
        assertFalse(viewModel.isLikelyApartment(house))
    }

    @Test
    fun `isLikelyApartmentで店名にキーワードがあっても検出する`() {
        val delivery = makeDelivery("a3").copy(name = "ハイツ新宿", address = "東京都新宿区1-1")
        assertTrue(viewModel.isLikelyApartment(delivery))
    }

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
        val vm = DeliveryViewModel(mockApp, mockRepo)

        vm.switchGroup("g2")

        assertEquals("g2", vm.currentGroupId.value)
        assertEquals(2, vm.deliveries.value?.size)
    }

    // ── エラーパス ───────────────────────────────────────────

    @Test
    fun `loadInitialData失敗時はerrorMessageが設定される`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("DB破損")
        val vm = DeliveryViewModel(mockApp, mockRepo)

        assertEquals("データの読み込みに失敗しました", vm.errorMessage.value)
    }

    @Test
    fun `loadInitialData失敗時はdeliveriesが空リストのまま`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("DB破損")
        val vm = DeliveryViewModel(mockApp, mockRepo)

        assertTrue(vm.deliveries.value?.isEmpty() == true)
    }

    @Test
    fun `toggleCompletedで存在しないIDは無視される`() {
        val beforeSize = viewModel.deliveries.value?.size

        viewModel.toggleCompleted("存在しないID")

        assertEquals(beforeSize, viewModel.deliveries.value?.size)
    }

    @Test
    fun `deleteDeliveryで存在しないIDは何も起きない`() {
        val beforeSize = viewModel.deliveries.value?.size

        viewModel.deleteDelivery("存在しないID")

        assertEquals(beforeSize, viewModel.deliveries.value?.size)
    }

    @Test
    fun `switchGroupで同じIDを指定しても二重処理されない`() {
        val beforeId = viewModel.currentGroupId.value

        viewModel.switchGroup(beforeId ?: "")

        assertEquals(beforeId, viewModel.currentGroupId.value)
    }

    @Test
    fun `markAllCompletedで空リストはクラッシュしない`() {
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to emptyList())
        )
        val vm = DeliveryViewModel(mockApp, mockRepo)

        vm.markAllCompleted()

        assertTrue(vm.deliveries.value?.isEmpty() == true)
    }
}

private fun <T> LiveData<T>.getOrNull(): T? = value
