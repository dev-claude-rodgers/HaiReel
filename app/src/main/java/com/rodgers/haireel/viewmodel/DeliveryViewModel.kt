package com.rodgers.haireel.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.DeliveryGroup
import com.rodgers.haireel.model.Room
import com.rodgers.haireel.model.colorForIndex
import com.rodgers.haireel.db.KnownAddressDao
import com.rodgers.haireel.db.KnownAddressEntity
import com.rodgers.haireel.repository.DeliveryRepository
import com.rodgers.haireel.util.AddressParser
import com.rodgers.haireel.util.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DeliveryViewModel @Inject constructor(
    app: Application,
    internal val repo: DeliveryRepository,
    private val knownAddressDao: KnownAddressDao
) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences(AppSettings.HAIREEL_PREFS, android.content.Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "DeliveryViewModel"
    }

    fun generateRooms(deliveryId: String, roomNumbers: List<String>) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val newRooms = roomNumbers.map { Room(number = it) }
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = newRooms) else d
        }
        commitDeliveries(groupId, updated)
    }

    // グループ一覧
    internal val _groups = MutableStateFlow<List<DeliveryGroup>>(emptyList())
    val groups: StateFlow<List<DeliveryGroup>> = _groups.asStateFlow()

    // 選択中のグループID
    internal val _currentGroupId = MutableStateFlow("")
    val currentGroupId: StateFlow<String> = _currentGroupId.asStateFlow()

    // 選択中グループの配達リスト
    internal val _deliveries = MutableStateFlow<List<Delivery>>(emptyList())
    val deliveries: StateFlow<List<Delivery>> = _deliveries.asStateFlow()

    // 全グループの配達リスト（地図表示用）
    internal val _allDeliveries = MutableStateFlow<Map<String, List<Delivery>>>(emptyMap())
    val allDeliveries: StateFlow<Map<String, List<Delivery>>> = _allDeliveries.asStateFlow()

    fun searchDeliveriesByName(query: String, excludeId: String = ""): List<Delivery> {
        if (query.length < 2) return emptyList()
        val all = (_allDeliveries.value.values.flatten()) +
                  _deliveries.value
        return all
            .filter { it.id != excludeId && !it.name.isNullOrBlank() &&
                      (it.name.contains(query, ignoreCase = true) ||
                       it.address.contains(query, ignoreCase = true)) }
            .distinctBy { "${it.name?.trim()}|${it.address.trim()}" }
            .sortedBy { it.name }
            .take(8)
    }

    internal val _areaHint = MutableStateFlow("")  // 値はinitで現在グループから読み込む
    val areaHint: StateFlow<String> = _areaHint.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()
    fun setSelectMode(enabled: Boolean) { _isSelectMode.value = enabled }

    internal val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    fun clearError() { _errorMessage.value = null }

    internal val _openEditForDelivery = MutableStateFlow<String?>(null)
    val openEditForDelivery: StateFlow<String?> = _openEditForDelivery.asStateFlow()
    fun requestEditDelivery(id: String) { _openEditForDelivery.value = id }
    fun clearEditRequest() { _openEditForDelivery.value = null }

    data class OverwriteConfirmation(val uri: android.net.Uri, val newContent: String)
    internal val _pendingOverwrite = MutableStateFlow<OverwriteConfirmation?>(null)
    val pendingOverwrite: StateFlow<OverwriteConfirmation?> = _pendingOverwrite.asStateFlow()

    fun confirmOverwrite() {
        val pending = _pendingOverwrite.value ?: return
        _pendingOverwrite.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(pending.uri, "wt")?.bufferedWriter()?.use {
                    it.write(pending.newContent)
                }
            } catch (e: Exception) { Log.w(TAG, "ファイル上書き失敗", e) }
        }
    }

    fun cancelOverwrite() { _pendingOverwrite.value = null }

    // 配達リストをUIと永続化層に一括コミット
    // ※ファイルへの自動書き出しは行わない（明示的なエクスポート操作のみ）
    internal fun commitDeliveries(groupId: String, list: List<Delivery>) {
        _deliveries.value = list
        saveGroupDeliveries(groupId, list)
        updateAllDeliveries(groupId, list)
        val groupName = _groups.value.find { it.id == groupId }?.name ?: ""
        com.rodgers.haireel.ui.HaiReelWidget.saveStats(
            getApplication(), groupName,
            list.count { it.isCompleted }, list.size
        )
    }

    internal val _mapFilter = MutableStateFlow<Set<String>?>(null)
    val mapFilter: StateFlow<Set<String>?> = _mapFilter.asStateFlow()
    fun setMapFilter(ids: Set<String>?) { _mapFilter.value = ids }

    internal val _visibleGroupIds = MutableStateFlow<Set<String>?>(null) // null = 全グループ表示
    val visibleGroupIds: StateFlow<Set<String>?> = _visibleGroupIds.asStateFlow()
    fun setVisibleGroups(ids: Set<String>?) { _visibleGroupIds.value = ids }

    fun changeGroupColor(groupId: String, colorHex: String) {
        val updated = _groups.value.map {
            if (it.id == groupId) it.copy(colorHex = colorHex) else it
        }
        _groups.value = updated
        saveGroups()
    }

    init {
        viewModelScope.launch {
            loadAll()
            try {
                val groupId = _currentGroupId.value
                repo.migrateGlobalAreaHint(groupId)
                _areaHint.value = repo.getAreaHint(groupId)
            } catch (e: Exception) { android.util.Log.e(TAG, "エリアヒント初期化失敗", e) }
        }
        // 既存配達先を住所履歴に一括登録（v2: DBスキーマ修正後に再実行されるよう番号管理）
        viewModelScope.launch(Dispatchers.IO) {
            if (!prefs.getBoolean("known_addr_backfill_v2", false)) {
                try {
                    val now = System.currentTimeMillis()
                    val entities = repo.getAllDeliveries()
                        .filter { it.address.isNotBlank() }
                        .distinctBy { it.address.trim() }
                        .map { d ->
                            KnownAddressEntity(
                                address         = d.address.trim(),
                                name            = d.name,
                                deliveryCount   = 1,
                                lastDeliveredAt = now
                            )
                        }
                    knownAddressDao.insertAllIfNew(entities)
                    prefs.edit().putBoolean("known_addr_backfill_v2", true).apply()
                } catch (_: Exception) { }
            }
        }
    }

    // ---- 配達操作 ----

    fun importAddresses(text: String, fileUriStr: String? = null) {
        val groupId = _currentGroupId.value
        if (fileUriStr != null) repo.saveFileUri(groupId, fileUriStr)
        val entries = AddressParser.parse(text)
        if (entries.isEmpty()) return
        val list = entries.mapIndexed { i, entry ->
            Delivery(order = i + 1, name = entry.name.ifBlank { null }, address = entry.address)
        }
        commitDeliveries(groupId, list)
    }


    // TTS: 配達完了後に次の住所を読み上げるためのイベント
    private val _ttsNextAddress = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val ttsNextAddress: SharedFlow<String> = _ttsNextAddress.asSharedFlow()

    fun toggleCompleted(id: String) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(isCompleted = !d.isCompleted) else d
        }
        commitDeliveries(groupId, updated)

        // 完了マークをつけた場合のみ次の住所を読み上げ
        val justCompleted = updated.find { it.id == id }
        if (justCompleted?.isCompleted == true) {
            val next = updated.filter { !it.isCompleted }.minByOrNull { it.order }
            val text = when {
                next != null -> buildString {
                    append("次は")
                    // ふりがながあればそちらを読む、なければ名前をそのまま読む
                    val readName = next.nameKana?.ifBlank { null } ?: next.name
                    if (!readName.isNullOrBlank()) append("${readName}、")
                    append(next.address)
                }
                else -> "全件配達完了です"
            }
            viewModelScope.launch { _ttsNextAddress.emit(text) }
        }
    }

    /** 全グループの住所・名前を全角に一括変換（既存データ対応） */
    fun normalizeAllAddresses() = viewModelScope.launch(Dispatchers.IO) {
        try {
            repo.normalizeAllAddressesToFullWidth()
            withContext(Dispatchers.Main) { loadAll() }
        } catch (e: Exception) { android.util.Log.e(TAG, "全角変換失敗", e) }
    }

    /** ふりがなのみ更新（ジオコーディング不要） */
    fun updateNameKana(id: String, kana: String?) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(nameKana = kana) else d
        }
        commitDeliveries(groupId, updated)
    }

    /** 名前・住所を座標そのままで更新（再ジオコーディングなし） */
    fun updateNameAndAddressOnly(id: String, name: String, address: String, kana: String?) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(
                name = name.ifBlank { null },
                address = address,
                nameKana = kana
            ) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun markAllCompleted() {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { it.copy(isCompleted = true) }
        commitDeliveries(groupId, updated)
    }

    fun resetAllCompleted() {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { it.copy(isCompleted = false) }
        commitDeliveries(groupId, updated)
    }

    fun markSelectedCompleted(ids: Set<String>) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id in ids) d.copy(isCompleted = true) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun resetSelectedCompleted(ids: Set<String>) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id in ids) d.copy(isCompleted = false) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun appendDelivery(template: com.rodgers.haireel.model.Delivery) {
        if (_currentGroupId.value.isBlank() || _groups.value.none { it.id == _currentGroupId.value }) return
        val groupId = _currentGroupId.value
        val current = _deliveries.value
        val newDelivery = template.copy(
            id          = java.util.UUID.randomUUID().toString(),
            order       = (current.maxOfOrNull { it.order } ?: 0) + 1,
            isCompleted = false,
            photoUris   = null,
            rooms       = template.rooms?.map { it.copy(isCompleted = false, status = null, visitedAt = null) }
        )
        commitDeliveries(groupId, current + newDelivery)
    }

    // 台帳から完全削除（全グループの完了済みレコードをDBから削除。進行中の配達先は保護）
    fun deleteLedgerEntry(ledgerKey: String) {
        val name    = ledgerKey.substringBefore("|").trim()
        val address = ledgerKey.substringAfter("|").trim()
        viewModelScope.launch {
            try {
                repo.deleteLedgerEntry(ledgerKey)
                // _allDeliveries・_deliveries をインプレースで更新（未完了は保護するため残す）
                val newAll = _allDeliveries.value.mapValues { (_, list) ->
                    list.filter { !(it.isCompleted && it.name?.trim().orEmpty() == name && it.address.trim() == address) }
                }
                _allDeliveries.value = newAll
                _deliveries.value = newAll[_currentGroupId.value] ?: emptyList()
            } catch (e: Exception) { android.util.Log.e(TAG, "台帳削除失敗", e) }
        }
    }

    // 1件削除
    fun deleteDelivery(id: String) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.filter { it.id != id }
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, updated)
        repo.clearFileUri(groupId)
    }

    fun appendDeliveries(newItems: List<Delivery>) {
        val groupId = _currentGroupId.value
        val existing = _deliveries.value
        val startOrder = (existing.maxOfOrNull { it.order } ?: 0) + 1
        val reordered = newItems.mapIndexed { i, d -> d.copy(order = startOrder + i) }
        commitDeliveries(groupId, existing + reordered)
        saveKnownAddresses(newItems)
    }

    fun replaceDeliveries(newItems: List<Delivery>) {
        val groupId = _currentGroupId.value
        val reordered = newItems.mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, reordered)
        saveKnownAddresses(newItems)
    }

    /** 追加・置換時に住所をknown_addressesに自動保存する */
    private fun saveKnownAddresses(items: List<Delivery>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                items.forEach { d ->
                    val addr = d.address.trim()
                    if (addr.isBlank()) return@forEach
                    val existing = knownAddressDao.findByAddress(addr)
                    if (existing != null) {
                        knownAddressDao.incrementCount(addr, d.name, System.currentTimeMillis())
                    } else {
                        knownAddressDao.insertIfNew(KnownAddressEntity(
                            address = addr,
                            name = d.name,
                            deliveryCount = 1,
                            lastDeliveredAt = System.currentTimeMillis()
                        ))
                    }
                }
            } catch (e: Exception) { android.util.Log.e(TAG, "住所履歴保存失敗", e) }
        }
    }

    /** 住所履歴から1件削除 */
    fun deleteKnownAddress(entity: com.rodgers.haireel.db.KnownAddressEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            try { knownAddressDao.delete(entity) }
            catch (e: Exception) { android.util.Log.e(TAG, "住所履歴削除失敗", e) }
        }

    // ─── 住所履歴 設定・クリーンアップ ──────────────────────────

    data class HistorySettings(
        val maxCount: Int,      // 上限件数（0 = 無制限）
        val deleteDays: Int     // 自動削除期間（0 = 削除しない）
    )

    fun getHistorySettings(): HistorySettings = HistorySettings(
        maxCount   = prefs.getInt("history_max_count", 500),
        deleteDays = prefs.getInt("history_delete_days", 90)
    )

    fun saveHistorySettings(maxCount: Int, deleteDays: Int) {
        prefs.edit()
            .putInt("history_max_count", maxCount)
            .putInt("history_delete_days", deleteDays)
            .apply()
    }

    /** 設定に従って住所履歴をクリーンアップ（ダイアログ開封時に呼ぶ） */
    fun cleanupAddressHistory() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val s = getHistorySettings()
            // 期間超え & 回数少ない（3回未満）を削除
            if (s.deleteDays > 0) {
                val cutoff = System.currentTimeMillis() - s.deleteDays * 24 * 60 * 60 * 1000L
                knownAddressDao.deleteOldLowCount(cutoff, protectMinCount = 3)
            }
            // 上限超えを削除
            if (s.maxCount > 0) {
                knownAddressDao.deleteOverLimit(s.maxCount)
            }
        } catch (_: Exception) { }
    }

    /** 住所の候補を返す（オートコンプリート用）
     *  空文字 → 最近の配達先15件
     *  入力あり → 前方一致 → ゼロなら部分一致 */
    suspend fun searchKnownAddresses(prefix: String): List<KnownAddressEntity> {
        if (prefix.isBlank()) return knownAddressDao.getRecent()
        val byPrefix = knownAddressDao.searchByPrefix(prefix)
        if (byPrefix.isNotEmpty()) return byPrefix
        return if (prefix.length >= 2) knownAddressDao.searchByKeyword(prefix) else emptyList()
    }

    // 複数件削除
    fun deleteDeliveries(ids: Set<String>) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.filter { it.id !in ids }
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, updated)
        repo.clearFileUri(groupId)
    }

    fun addRoom(deliveryId: String, roomNumber: String) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList + Room(number = roomNumber)) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun updateRoom(deliveryId: String, roomId: String, note: String, isCompleted: Boolean) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList.map { r ->
                if (r.id == roomId) r.copy(note = note, isCompleted = isCompleted) else r
            }) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun setRoomStatus(deliveryId: String, roomId: String, status: String) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val now = System.currentTimeMillis()
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList.map { r ->
                if (r.id == roomId) r.copy(
                    status = status.ifBlank { null },
                    visitedAt = if (status.isBlank()) null else now,
                    isCompleted = status == "アポ獲得" || status == "断り"
                ) else r
            }) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun deleteRoom(deliveryId: String, roomId: String) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList.filter { it.id != roomId }) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun clearRooms(deliveryId: String) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val updated = list.map { d -> if (d.id == deliveryId) d.copy(rooms = emptyList()) else d }
        commitDeliveries(groupId, updated)
    }

    fun clearPhotos(deliveryId: String) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == deliveryId) d.copy(photoUris = emptyList()) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun editNote(id: String, note: String) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(note = note) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun updateBusinessHours(id: String, openTime: String?, closeTime: String?) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(openTime = openTime, closeTime = closeTime) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun updateTimeSlotAndPackage(id: String, timeSlot: String?, packageCount: Int) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(timeSlot = timeSlot, packageCount = packageCount) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun batchUpdateTimeSlot(ids: Set<String>, timeSlot: String?) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id in ids) d.copy(timeSlot = timeSlot) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun addPhoto(id: String, photoUri: String) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(photoUris = d.allPhotoUris + photoUri) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun removePhoto(id: String, index: Int) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) {
                val newUris = d.allPhotoUris.toMutableList().also { if (index in it.indices) it.removeAt(index) }
                d.copy(photoUris = newUris)
            } else d
        }
        commitDeliveries(groupId, updated)
    }

    // ドラッグ並べ替え後に呼ぶ: 新しい順序でorder番号を振り直して保存
    fun reorderDeliveries(newList: List<Delivery>) {
        val groupId = _currentGroupId.value
        commitDeliveries(groupId, newList.mapIndexed { i, d -> d.copy(order = i + 1) })
        repo.clearFileUri(groupId)
    }

    // 開始時刻（openTime優先、なければtimeSlotから推定）の早い順に並べ替える。GPS不要
    fun sortByTimeSlot() {
        val sorted = _deliveries.value.sortedBy { d ->
            com.rodgers.haireel.util.TimeSlotColor.sortMinutesFor(d.openTime, d.timeSlot) ?: Int.MAX_VALUE
        }
        reorderDeliveries(sorted)
    }

    fun clearCurrentGroup() {
        val groupId = _currentGroupId.value
        commitDeliveries(groupId, emptyList())
        repo.clearFileUri(groupId)
    }

    fun setAreaHint(area: String) {
        val groupId = _currentGroupId.value
        _areaHint.value = area
        repo.saveAreaHint(groupId, area)
    }

    // ---- 内部処理 ----

    internal fun updateAllDeliveries(groupId: String, list: List<Delivery>) {
        val map = _allDeliveries.value.toMutableMap()
        map[groupId] = list
        _allDeliveries.value = map
    }

    internal fun saveGroups() {
        val groups = _groups.value
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.saveGroups(groups) }
            catch (e: Exception) { android.util.Log.e(TAG, "グループ保存失敗", e) }
        }
    }

    internal fun saveGroupDeliveries(groupId: String, list: List<Delivery>) {
        viewModelScope.launch(Dispatchers.IO) {
            try { repo.saveDeliveries(groupId, list) }
            catch (e: Exception) { android.util.Log.e(TAG, "配達先保存失敗", e) }
        }
    }

    fun reloadFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val groupId = _currentGroupId.value
                if (groupId.isBlank()) return@launch
                val list = repo.loadDeliveries(groupId)
                withContext(Dispatchers.Main) { _deliveries.value = list }
            } catch (e: Exception) { android.util.Log.e(TAG, "DB再読み込み失敗", e) }
        }
    }

    private suspend fun loadAll() {
        try {
            val data = repo.loadInitialData()
            withContext(Dispatchers.Main) {
                _groups.value = data.groups
                cleanupOrphanedDownloadFiles(data.groups)
                createMissingDownloadFiles(data.groups)
                _allDeliveries.value = data.allDeliveries
                val savedId = repo.getCurrentGroupId()
                val targetId = if (savedId != null && data.groups.any { it.id == savedId }) savedId
                               else data.groups.firstOrNull()?.id ?: ""
                _currentGroupId.value = targetId
                val targetDeliveries = data.allDeliveries[targetId] ?: emptyList()
                _deliveries.value = targetDeliveries
            }
        } catch (e: Exception) {
            Log.w(TAG, "初期データ読み込み失敗", e)
            _errorMessage.value = "データの読み込みに失敗しました"
        }
    }

}
