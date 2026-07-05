package com.rodgers.haireel.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.rodgers.haireel.db.KnownAddressDao
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.DeliveryGroup
import com.rodgers.haireel.repository.DeliveryRepository
import com.rodgers.haireel.util.GeocodingApi
import com.rodgers.haireel.util.GeocodingManager
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
    private lateinit var mockKnownAddressDao: KnownAddressDao
    private lateinit var viewModel: DeliveryViewModel

    private val group = DeliveryGroup(id = "g1", name = "テストグループ")
    private fun makeDelivery(id: String, order: Int = 1, completed: Boolean = false) =
        Delivery(id = id, order = order, address = "東京都新宿区$id", isCompleted = completed)
    private fun makeNamedDelivery(id: String, name: String, address: String, order: Int = 1) =
        Delivery(id = id, order = order, name = name, address = address)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApp = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)
        mockGeocodingManager = mockk(relaxed = true)
        mockGeocodingApi = mockk(relaxed = true)
        mockKnownAddressDao = mockk(relaxed = true)
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(makeDelivery("d1"), makeDelivery("d2")))
        )
        every { mockRepo.getCurrentGroupId() } returns group.id
        every { mockRepo.getAreaHint(any()) } returns ""
        every { mockRepo.migrateGlobalAreaHint(any()) } returns null
        viewModel = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
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
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
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
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
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

    // ── エラーパス ────────────────────────────────────────────

    @Test
    fun `loadInitialData失敗時はerrorMessageが設定される`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("DB破損")
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        assertEquals("データの読み込みに失敗しました", vm.errorMessage.value)
    }

    @Test
    fun `loadInitialData失敗時はdeliveriesが空リストのまま`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("DB破損")
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
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

    // ── 選択操作 ──────────────────────────────────────────────

    @Test
    fun `markSelectedCompletedで対象だけ完了になる`() {
        viewModel.markSelectedCompleted(setOf("d1"))
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        val d2 = viewModel.deliveries.value.find { it.id == "d2" }
        assertTrue(d1?.isCompleted == true)
        assertFalse(d2?.isCompleted == true)
    }

    @Test
    fun `resetSelectedCompletedで対象だけ未完了に戻る`() {
        viewModel.markAllCompleted()
        viewModel.resetSelectedCompleted(setOf("d1"))
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        val d2 = viewModel.deliveries.value.find { it.id == "d2" }
        assertFalse(d1?.isCompleted == true)
        assertTrue(d2?.isCompleted == true)
    }

    @Test
    fun `setSelectModeでisSelectModeが変わる`() {
        assertFalse(viewModel.isSelectMode.value)
        viewModel.setSelectMode(true)
        assertTrue(viewModel.isSelectMode.value)
        viewModel.setSelectMode(false)
        assertFalse(viewModel.isSelectMode.value)
    }

    // ── エラー・状態管理 ─────────────────────────────────────

    @Test
    fun `clearErrorでerrorMessageがnullになる`() {
        coEvery { mockRepo.loadInitialData() } throws RuntimeException("テストエラー")
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        assertNotNull(vm.errorMessage.value)
        vm.clearError()
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `setMapFilterでmapFilterが変わる`() {
        assertNull(viewModel.mapFilter.value)
        viewModel.setMapFilter(setOf("d1"))
        assertEquals(setOf("d1"), viewModel.mapFilter.value)
        viewModel.setMapFilter(null)
        assertNull(viewModel.mapFilter.value)
    }

    @Test
    fun `setVisibleGroupsでvisibleGroupIdsが変わる`() {
        assertNull(viewModel.visibleGroupIds.value)
        viewModel.setVisibleGroups(setOf("g1"))
        assertEquals(setOf("g1"), viewModel.visibleGroupIds.value)
    }

    // ── グループ属性変更 ──────────────────────────────────────

    @Test
    fun `changeGroupColorでグループのカラーが変わる`() {
        viewModel.changeGroupColor(group.id, "#FF5722")
        val g = viewModel.groups.value.find { it.id == group.id }
        assertEquals("#FF5722", g?.colorHex)
    }

    @Test
    fun `linkPatternToGroupでpatternIdが変わる`() {
        viewModel.linkPatternToGroup(group.id, 42)
        val g = viewModel.groups.value.find { it.id == group.id }
        assertEquals(42, g?.patternId)
    }

    // ── 名前・ふりがな更新 ────────────────────────────────────

    @Test
    fun `updateNameKanaでふりがなが更新される`() {
        viewModel.updateNameKana("d1", "とうきょうとしんじゅくく")
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertEquals("とうきょうとしんじゅくく", d1?.nameKana)
    }

    @Test
    fun `updateNameAndAddressOnlyで名前と住所が更新される`() {
        viewModel.updateNameAndAddressOnly("d1", "佐藤商店", "東京都渋谷区1-1", null)
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertEquals("佐藤商店", d1?.name)
        assertEquals("東京都渋谷区1-1", d1?.address)
    }

    @Test
    fun `updateNameAndAddressOnlyで空の名前はnullになる`() {
        viewModel.updateNameAndAddressOnly("d1", "", "東京都渋谷区1-1", null)
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertNull(d1?.name)
    }

    // ── 候補適用 ──────────────────────────────────────────────

    @Test
    fun `applyCandidateで座標と住所が更新される`() {
        viewModel.applyCandidate("d1", "テスト商店", "東京都千代田区1-1", 35.68, 139.76)
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertEquals(35.68, d1?.lat ?: 0.0, 0.001)
        assertEquals(139.76, d1?.lng ?: 0.0, 0.001)
        assertTrue(d1?.isGeocoded == true)
    }

    @Test
    fun `applyCandidateで名前が空の場合は既存名を維持する`() {
        viewModel.updateNameAndAddressOnly("d1", "既存の名前", "東京都新宿区d1", null)
        viewModel.applyCandidate("d1", "", "東京都千代田区1-1", 35.68, 139.76)
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertEquals("既存の名前", d1?.name)
    }

    // ── searchDeliveriesByName ────────────────────────────────

    @Test
    fun `searchDeliveriesByName_1文字以下のクエリは空リストを返す`() {
        assertEquals(emptyList<Any>(), viewModel.searchDeliveriesByName("あ"))
        assertEquals(emptyList<Any>(), viewModel.searchDeliveriesByName(""))
    }

    @Test
    fun `searchDeliveriesByName_名前がnullの配達先はヒットしない`() {
        val result = viewModel.searchDeliveriesByName("新宿")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchDeliveriesByName_名前でマッチする配達先を返す`() {
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(
                makeNamedDelivery("d1", "山田商店", "東京都新宿区1"),
                makeNamedDelivery("d2", "鈴木薬局", "東京都渋谷区2")
            ))
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        val result = vm.searchDeliveriesByName("山田")
        assertEquals(1, result.size)
        assertEquals("山田商店", result[0].name)
    }

    @Test
    fun `searchDeliveriesByName_住所でもマッチする`() {
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(
                makeNamedDelivery("d1", "テスト店", "東京都渋谷区神南1-1")
            ))
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        val result = vm.searchDeliveriesByName("渋谷区")
        assertEquals(1, result.size)
    }

    @Test
    fun `searchDeliveriesByName_excludeIdで指定IDは除外される`() {
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(
                makeNamedDelivery("d1", "テスト商店", "東京都新宿区1"),
                makeNamedDelivery("d2", "テスト薬局", "東京都新宿区2")
            ))
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        val result = vm.searchDeliveriesByName("テスト", excludeId = "d1")
        assertTrue(result.none { it.id == "d1" })
        assertTrue(result.any { it.id == "d2" })
    }

    // ── DeliveryViewModelGroups extension functions ────────────

    @Test
    fun `currentGroupで現在のグループが取得できる`() {
        val result = viewModel.currentGroup()
        assertEquals(group.id, result?.id)
        assertEquals(group.name, result?.name)
    }

    @Test
    fun `switchGroup後のcurrentGroupが切り替わる`() {
        val group2 = DeliveryGroup(id = "g2", name = "グループ2")
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group, group2),
            allDeliveries = mapOf(group.id to emptyList(), group2.id to emptyList())
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        vm.switchGroup("g2")
        assertEquals("g2", vm.currentGroup()?.id)
    }

    @Test
    fun `renameGroupで名前が更新される`() {
        viewModel.renameGroup(group.id, "新しい名前")
        val renamed = viewModel.groups.value.find { it.id == group.id }
        assertEquals("新しい名前", renamed?.name)
    }

    @Test
    fun `renameGroupで存在しないIDは何も起きない`() {
        val before = viewModel.groups.value.size
        viewModel.renameGroup("存在しないID", "新しい名前")
        assertEquals(before, viewModel.groups.value.size)
    }

    @Test
    fun `copyGroupでグループが追加される`() {
        val before = viewModel.groups.value.size
        viewModel.copyGroup(group.id)
        assertEquals(before + 1, viewModel.groups.value.size)
    }

    @Test
    fun `copyGroupのコピー名は元の名前と番号が付く`() {
        viewModel.copyGroup(group.id)
        val copied = viewModel.groups.value.last()
        assertTrue("コピー名が元の名前を含む", copied.name.startsWith(group.name))
        assertTrue("コピー名に数字が付く", copied.name.matches(Regex(".*\\d+")))
    }

    @Test
    fun `deleteGroupで現在のグループを削除すると先頭グループに切り替わる`() {
        val group2 = DeliveryGroup(id = "g2", name = "グループ2")
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group, group2),
            allDeliveries = mapOf(group.id to emptyList(), group2.id to emptyList())
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        vm.switchGroup(group.id)
        vm.deleteGroup(group.id)
        assertEquals("g2", vm.currentGroupId.value)
    }

    @Test
    fun `deleteGroupで最後の1件を削除するとcurrentGroupIdが空になる`() {
        viewModel.deleteGroup(group.id)
        assertEquals("", viewModel.currentGroupId.value)
        assertTrue(viewModel.deliveries.value.isEmpty())
    }

    // ── requestEditDelivery / clearEditRequest ─────────────────

    @Test
    fun `requestEditDeliveryでopenEditForDeliveryが設定される`() {
        viewModel.requestEditDelivery("d1")
        assertEquals("d1", viewModel.openEditForDelivery.value)
    }

    @Test
    fun `clearEditRequestでopenEditForDeliveryがnullになる`() {
        viewModel.requestEditDelivery("d1")
        viewModel.clearEditRequest()
        assertNull(viewModel.openEditForDelivery.value)
    }

    @Test
    fun `requestEditDelivery後に別のIDを設定すると上書きされる`() {
        viewModel.requestEditDelivery("d1")
        viewModel.requestEditDelivery("d2")
        assertEquals("d2", viewModel.openEditForDelivery.value)
    }

    // ── generateRooms ─────────────────────────────────────────

    @Test
    fun `generateRoomsで対象IDのroomsが設定される`() {
        viewModel.generateRooms("d1", listOf("101", "102", "103"))
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertEquals(3, d1?.roomList?.size)
        assertEquals("101", d1?.roomList?.get(0)?.number)
    }

    @Test
    fun `generateRoomsで空リストを渡すとroomsが空になる`() {
        viewModel.generateRooms("d1", listOf("101"))
        viewModel.generateRooms("d1", emptyList())
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertTrue(d1?.roomList?.isEmpty() == true)
    }

    @Test
    fun `generateRoomsで存在しないIDは他の配達先に影響しない`() {
        viewModel.generateRooms("存在しないID", listOf("101"))
        val d1 = viewModel.deliveries.value.find { it.id == "d1" }
        assertTrue(d1?.roomList?.isEmpty() == true)
    }

    // ── clearGeocodingFailure ─────────────────────────────────

    @Test
    fun `clearGeocodingFailureでgeocodingFailedCountが0になる`() {
        viewModel._geocodingFailedCount.value = 5
        viewModel.clearGeocodingFailure()
        assertEquals(0, viewModel.geocodingFailedCount.value)
    }

    // ── DeliveryViewModelGroups: 未カバー領域 ──────────────────

    @Test
    fun `copyGroupでコピー元の配達先がコピー先のallDeliveriesに追加される`() {
        val d1 = makeDelivery("d1", order = 1)
        val d2 = makeDelivery("d2", order = 2)
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(d1, d2))
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        vm.copyGroup(group.id)

        val newGroupId = vm.groups.value.last().id
        val copied = vm.allDeliveries.value[newGroupId]
        assertEquals(2, copied?.size)
    }

    @Test
    fun `copyGroupでコピー元の配達先が空のときグループだけ追加される`() {
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to emptyList())
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        val before = vm.groups.value.size
        vm.copyGroup(group.id)

        assertEquals(before + 1, vm.groups.value.size)
        val newGroupId = vm.groups.value.last().id
        assertTrue(vm.allDeliveries.value[newGroupId].isNullOrEmpty())
    }

    @Test
    fun `deleteGroup_現在グループ以外を削除しても現在グループは変わらない`() {
        val group2 = DeliveryGroup(id = "g2", name = "グループ2")
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group, group2),
            allDeliveries = mapOf(group.id to emptyList(), group2.id to emptyList())
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        vm.switchGroup(group.id)
        vm.deleteGroup(group2.id)

        assertEquals(group.id, vm.currentGroupId.value)
        assertEquals(1, vm.groups.value.size)
    }

    @Test
    fun `renameGroupでグループ名が更新されてもdeliveriesは変わらない`() {
        val d1 = makeDelivery("d1", order = 1)
        coEvery { mockRepo.loadInitialData() } returns DeliveryRepository.InitialData(
            groups = listOf(group),
            allDeliveries = mapOf(group.id to listOf(d1))
        )
        val vm = DeliveryViewModel(mockApp, mockRepo, mockGeocodingManager, mockGeocodingApi, mockKnownAddressDao)
        vm.renameGroup(group.id, "新しい名前")

        assertEquals("新しい名前", vm.groups.value.first().name)
        assertEquals(1, vm.deliveries.value.size)
    }
}
