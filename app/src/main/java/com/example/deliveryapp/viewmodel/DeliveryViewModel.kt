package com.rodgers.routist.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.DeliveryGroup
import com.rodgers.routist.model.Room
import com.rodgers.routist.model.colorForIndex
import com.rodgers.routist.util.AddressParser
import com.rodgers.routist.util.GeocodingClient
import com.rodgers.routist.util.RouteOptimizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DeliveryViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private val APARTMENT_KEYWORDS = listOf(
            "マンション", "アパート", "ハイツ", "コーポ", "レジデンス", "コート",
            "荘", "ガーデン", "テラス", "ヴィラ", "ビレッジ", "タワー", "パレス",
            "ハウス", "シティ", "プレイス", "エステート", "グランド", "サンシャイン"
        )
    }

    fun isLikelyApartment(delivery: Delivery): Boolean {
        val text = "${delivery.name.orEmpty()} ${delivery.address} ${delivery.geocodedAddress.orEmpty()}"
        return APARTMENT_KEYWORDS.any { text.contains(it) }
    }

    fun generateRooms(deliveryId: String, roomNumbers: List<String>) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        val newRooms = roomNumbers.map { Room(number = it) }
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = newRooms) else d
        }
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    private val prefs = app.getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val geocodingJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    // グループ一覧
    private val _groups = MutableLiveData<List<DeliveryGroup>>(emptyList())
    val groups: LiveData<List<DeliveryGroup>> = _groups

    // 選択中のグループID
    private val _currentGroupId = MutableLiveData<String>("")
    val currentGroupId: LiveData<String> = _currentGroupId

    // 選択中グループの配達リスト
    private val _deliveries = MutableLiveData<List<Delivery>>(emptyList())
    val deliveries: LiveData<List<Delivery>> = _deliveries

    // 全グループの配達リスト（地図表示用）
    private val _allDeliveries = MutableLiveData<Map<String, List<Delivery>>>(emptyMap())
    val allDeliveries: LiveData<Map<String, List<Delivery>>> = _allDeliveries

    private val _geocodingProgress = MutableLiveData<GeocodingProgress>()
    val geocodingProgress: LiveData<GeocodingProgress> = _geocodingProgress

    private val _areaHint = MutableLiveData("")  // 値はinitで現在グループから読み込む
    val areaHint: LiveData<String> = _areaHint

    private val _isSelectMode = MutableLiveData(false)
    val isSelectMode: LiveData<Boolean> = _isSelectMode
    fun setSelectMode(enabled: Boolean) { _isSelectMode.value = enabled }

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    fun clearError() { _errorMessage.value = null }

    private val _pinAddedFromMap = MutableLiveData<Unit>()
    val pinAddedFromMap: LiveData<Unit> = _pinAddedFromMap

    private val _openEditForDelivery = MutableLiveData<String?>(null)
    val openEditForDelivery: LiveData<String?> = _openEditForDelivery
    fun requestEditDelivery(id: String) { _openEditForDelivery.value = id }
    fun clearEditRequest() { _openEditForDelivery.value = null }

    data class OverwriteConfirmation(val uri: android.net.Uri, val newContent: String)
    private val _pendingOverwrite = MutableLiveData<OverwriteConfirmation?>(null)
    val pendingOverwrite: LiveData<OverwriteConfirmation?> = _pendingOverwrite

    fun confirmOverwrite() {
        val pending = _pendingOverwrite.value ?: return
        _pendingOverwrite.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                context.contentResolver.openOutputStream(pending.uri, "wt")?.bufferedWriter()?.use {
                    it.write(pending.newContent)
                }
            } catch (_: Exception) {}
        }
    }

    fun cancelOverwrite() { _pendingOverwrite.value = null }

    // 配達リストをUIと永続化層に一括コミット
    // ※ファイルへの自動書き出しは行わない（明示的なエクスポート操作のみ）
    private fun commitDeliveries(groupId: String, list: List<Delivery>) {
        _deliveries.value = list
        saveGroupDeliveries(groupId, list)
        updateAllDeliveries(groupId, list)
    }

    // 明示的なエクスポート（≡メニューから呼び出す）
    fun exportCurrentListToDownloads() {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        exportToDownloads(groupId, list)
    }

    private val _mapFilter = MutableLiveData<Set<String>?>(null)
    val mapFilter: LiveData<Set<String>?> = _mapFilter
    fun setMapFilter(ids: Set<String>?) { _mapFilter.value = ids }

    private val _visibleGroupIds = MutableLiveData<Set<String>?>(null) // null = 全グループ表示
    val visibleGroupIds: LiveData<Set<String>?> = _visibleGroupIds
    fun setVisibleGroups(ids: Set<String>?) { _visibleGroupIds.value = ids }

    fun changeGroupColor(groupId: String, colorHex: String) {
        val updated = (_groups.value ?: emptyList()).map {
            if (it.id == groupId) it.copy(colorHex = colorHex) else it
        }
        _groups.value = updated
        saveGroups()
    }

    fun linkPatternToGroup(groupId: String, patternId: Int) {
        val updated = (_groups.value ?: emptyList()).map {
            if (it.id == groupId) it.copy(patternId = patternId) else it
        }
        _groups.value = updated
        saveGroups()
    }

    data class GeocodingProgress(val current: Int, val total: Int, val isRunning: Boolean)

    init {
        GeocodingClient.apiKey = com.rodgers.routist.BuildConfig.MAPS_API_KEY
        loadAll()
        // loadAll() で currentGroupId が確定したあとに、そのグループのエリアヒントを適用
        val groupId = _currentGroupId.value ?: ""
        // 旧グローバル設定 "area_hint" があれば現在のグループに移行してキーを削除
        val legacy = prefs.getString("area_hint", null)
        if (legacy != null && groupId.isNotBlank()) {
            prefs.edit().putString("area_hint_$groupId", legacy).remove("area_hint").apply()
        }
        applyAreaHintForGroup(groupId)
    }

    private fun applyAreaHintForGroup(groupId: String) {
        val hint = if (groupId.isNotBlank()) prefs.getString("area_hint_$groupId", "") ?: "" else ""
        _areaHint.value = hint
        GeocodingClient.areaHint = hint
    }

    // ---- グループ操作 ----

    fun createGroup(name: String): DeliveryGroup {
        val index = _groups.value?.size ?: 0
        val group = DeliveryGroup(name = name, colorHex = colorForIndex(index))
        val updated = (_groups.value ?: emptyList()) + group
        _groups.value = updated
        saveGroups()
        return group
    }

    fun deleteGroup(groupId: String) {
        val group = _groups.value?.find { it.id == groupId }
        val updated = (_groups.value ?: emptyList()).filter { it.id != groupId }
        _groups.value = updated
        saveGroups()
        prefs.edit().remove("group_$groupId").commit()
        group?.let { deleteDownloadsFile(groupId, it.name) }

        val allMap = (_allDeliveries.value ?: emptyMap()).toMutableMap()
        allMap.remove(groupId)
        _allDeliveries.value = allMap

        if (_currentGroupId.value == groupId) {
            val first = updated.firstOrNull()
            if (first != null) switchGroup(first.id) else {
                _currentGroupId.value = ""
                _deliveries.value = emptyList()
            }
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        val oldName = _groups.value?.find { it.id == groupId }?.name
        val updated = (_groups.value ?: emptyList()).map {
            if (it.id == groupId) it.copy(name = newName) else it
        }
        _groups.value = updated
        saveGroups()
        // 旧ファイルを削除して新しいグループ名でファイルを出力
        if (oldName != null && oldName != newName) {
            deleteDownloadsFile(groupId, oldName)
            val list = loadGroupDeliveries(groupId)
            if (list.isNotEmpty()) exportToDownloads(groupId, list)
        }
    }

    fun switchGroup(groupId: String) {
        if (_currentGroupId.value == groupId) return
        // currentGroupId を変える前に visibleGroupIds をリセットしておく
        // → どのオブザーバが先に発火しても「現在のリストのみ」になることを保証
        _visibleGroupIds.value = null
        _currentGroupId.value = groupId
        prefs.edit().putString("current_group_id", groupId).apply()
        applyAreaHintForGroup(groupId)
        val list = loadGroupDeliveries(groupId)
        _deliveries.value = list
        updateAllDeliveries(groupId, list)
    }

    fun currentGroup(): DeliveryGroup? =
        _groups.value?.find { it.id == _currentGroupId.value }

    // ---- 配達操作 ----

    fun importAddresses(text: String, fileUriStr: String? = null) {
        val groupId = _currentGroupId.value ?: return
        // ファイルURIを記憶（自動書き戻し用）
        if (fileUriStr != null) {
            prefs.edit().putString("file_uri_$groupId", fileUriStr).apply()
        }
        val entries = AddressParser.parse(text)
        if (entries.isEmpty()) return
        val list = entries.mapIndexed { i, entry ->
            Delivery(order = i + 1, name = entry.name.ifBlank { null }, address = entry.address)
        }
        _deliveries.value = list
        saveGroupDeliveries(groupId, list)
        updateAllDeliveries(groupId, list)
        startGeocoding(groupId)
    }

    // 起動時: グループがあるのにDownloadsファイルが存在しない場合、再作成する
    private fun createMissingDownloadFiles(activeGroups: List<com.rodgers.routist.model.DeliveryGroup>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    activeGroups.forEach { group ->
                        val list = loadGroupDeliveries(group.id)
                        if (list.isEmpty()) return@forEach
                        val safeName = group.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                        val fileName = "Routist_$safeName.txt"
                        val exists = resolver.query(baseUri, arrayOf(MediaStore.Downloads._ID),
                            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                            arrayOf(fileName), null)?.use { it.count > 0 } ?: false
                        if (!exists) {
                            val content = list.mapIndexed { i, d ->
                                "${i + 1}. ${if (!d.name.isNullOrBlank()) d.name else d.address}"
                            }.joinToString("\n")
                            val values = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            }
                            resolver.insert(baseUri, values)?.let { uri ->
                                resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { w -> w.write(content) }
                                prefs.edit().putString("download_file_uri_${group.id}", uri.toString()).apply()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // 起動時: Downloadsの Routist_*.txt のうち、対応するグループが存在しないファイルを全削除
    private fun cleanupOrphanedDownloadFiles(activeGroups: List<com.rodgers.routist.model.DeliveryGroup>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                    resolver.query(baseUri, projection, null, null, null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val fileId = cursor.getLong(0)
                            val fileName = cursor.getString(1) ?: continue
                            if (!fileName.startsWith("Routist_") || !fileName.endsWith(".txt")) continue
                            val hasGroup = activeGroups.any { g ->
                                val safe = g.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                                "Routist_$safe.txt" == fileName
                            }
                            if (!hasGroup) {
                                resolver.delete(ContentUris.withAppendedId(baseUri, fileId), null, null)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Downloadsフォルダの Routist_グループ名.txt を削除
    private fun deleteDownloadsFile(groupId: String, groupName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    // 保存済みURIで直接削除（確実）
                    val savedUri = prefs.getString("download_file_uri_$groupId", null)
                    if (savedUri != null) {
                        try {
                            resolver.delete(android.net.Uri.parse(savedUri), null, null)
                        } catch (_: Exception) {}
                        prefs.edit().remove("download_file_uri_$groupId").apply()
                    }
                    // フォールバック: ファイル名で検索して削除
                    val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    val safeName = groupName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                    val fileName = "Routist_$safeName.txt"
                    resolver.query(baseUri, arrayOf(MediaStore.Downloads._ID),
                        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                        arrayOf(fileName), null)?.use { cursor ->
                        while (cursor.moveToNext()) {
                            resolver.delete(ContentUris.withAppendedId(baseUri, cursor.getLong(0)), null, null)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // インポートファイルの MediaStore ID を返す（DocumentsProvider/MediaStore 両形式に対応）
    private fun importFileMediaStoreId(groupId: String): Long? {
        val uriStr = prefs.getString("file_uri_$groupId", null) ?: return null
        val uri = android.net.Uri.parse(uriStr)
        return when (uri.authority) {
            "com.android.providers.media.documents" ->
                DocumentsContract.getDocumentId(uri).split(":").lastOrNull()?.toLongOrNull()
            else -> try { ContentUris.parseId(uri) } catch (_: Exception) { null }
        }
    }

    // Downloadsフォルダに Routist_グループ名.txt として出力
    private fun exportToDownloads(groupId: String, list: List<Delivery>) {
        if (list.isEmpty()) return
        val group = _groups.value?.find { it.id == groupId } ?: return
        val content = list.mapIndexed { index, d ->
            val num = index + 1
            val label = if (!d.name.isNullOrBlank()) d.name else d.address
            "$num. $label"
        }.joinToString("\n")
        val safeName = group.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val fileName = "Routist_$safeName.txt"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    // 既存ファイルを検索し、あればそのURIに上書き（削除→再作成だと重複ファイルになる）
                    val projection = arrayOf(MediaStore.Downloads._ID)
                    val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                    val existingCursor = resolver.query(baseUri, projection, selection, arrayOf(fileName), null)
                    val fileUri = if (existingCursor != null && existingCursor.moveToFirst()) {
                        val id = existingCursor.getLong(0)
                        existingCursor.close()
                        val sameAsImport = importFileMediaStoreId(groupId) == id
                        val fileUri = ContentUris.withAppendedId(baseUri, id)
                        if (sameAsImport) {
                            // 内容が変わっていたら確認ダイアログを出す
                            val existing = try {
                                resolver.openInputStream(fileUri)?.bufferedReader()?.readText()
                            } catch (_: Exception) { null }
                            if (existing != null && existing.trim() != content.trim()) {
                                withContext(Dispatchers.Main) {
                                    _pendingOverwrite.value = OverwriteConfirmation(fileUri, content)
                                }
                            }
                            return@launch
                        }
                        fileUri
                    } else {
                        existingCursor?.close()
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        resolver.insert(baseUri, values)
                    }
                    fileUri?.let { uri ->
                        resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { w -> w.write(content) }
                        // 削除時に直接使えるようURIを保存
                        prefs.edit().putString("download_file_uri_$groupId", uri.toString()).apply()
                    }
                } else {
                    File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                        .writeText(content)
                }
            } catch (_: Exception) {}
        }
    }

    fun toggleCompleted(id: String) {
        val groupId = _currentGroupId.value ?: return
        val updated = _deliveries.value?.map { d ->
            if (d.id == id) d.copy(isCompleted = !d.isCompleted) else d
        } ?: return
        commitDeliveries(groupId, updated)
    }

    fun markAllCompleted() {
        val groupId = _currentGroupId.value ?: return
        val updated = (_deliveries.value ?: return).map { it.copy(isCompleted = true) }
        commitDeliveries(groupId, updated)
    }

    fun resetAllCompleted() {
        val groupId = _currentGroupId.value ?: return
        val updated = (_deliveries.value ?: return).map { it.copy(isCompleted = false) }
        commitDeliveries(groupId, updated)
    }

    fun optimizeRoute(currentLat: Double, currentLng: Double) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        val optimized = RouteOptimizer.optimize(list, currentLat, currentLng)
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, optimized)
    }

    // 逆ジオコーディング: 地図長押しで座標からピンを追加
    fun addPinFromLocation(lat: Double, lng: Double) {
        val groupId = _currentGroupId.value ?: return
        _mapFilter.value = null

        // ピンを即座に追加（座標を仮住所として使用）
        val tempAddress = "${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}"
        val newDelivery = Delivery(
            order = (_deliveries.value?.size ?: 0) + 1,
            address = tempAddress,
            lat = lat,
            lng = lng,
            isGeocoded = true
        )
        val newList = (_deliveries.value ?: emptyList()) + newDelivery
        commitDeliveries(groupId, newList)
        _pinAddedFromMap.value = Unit

        // 住所を非同期で取得して更新（グループ切り替え後も正しいグループに反映）
        viewModelScope.launch {
            val result = GeocodingClient.reverseGeocode(lat, lng) ?: return@launch
            val address = result.formattedAddress.ifBlank { return@launch }
            val savedList = loadGroupDeliveries(groupId)
            val updated = savedList.map { d ->
                if (d.id == newDelivery.id) d.copy(address = address) else d
            }
            commitDeliveries(groupId, updated)
        }
    }

    // アイテム編集: 店名・住所を修正して再ジオコーディング
    fun editDelivery(id: String, newName: String, newAddress: String) {
        val groupId = _currentGroupId.value ?: return
        viewModelScope.launch {
            var result = GeocodingClient.geocode(newAddress)
            if (result == null) {
                result = GeocodingClient.searchPlaces(newAddress).firstOrNull()?.let { place ->
                    GeocodingClient.GeoResult(place.lat, place.lng, place.address)
                }
            }
            if (result == null) {
                _errorMessage.value = "住所を検索できませんでした。\nネットワーク接続を確認してください。"
            }
            val officialName = result?.formattedAddress?.ifBlank { newAddress } ?: newAddress
            val updated = _deliveries.value?.map { d ->
                if (d.id == id) d.copy(
                    name = newName.ifBlank { null },
                    address = officialName,
                    geocodedAddress = null,
                    lat = result?.lat ?: d.lat,
                    lng = result?.lng ?: d.lng,
                    isGeocoded = result != null
                ) else d
            } ?: return@launch
            commitDeliveries(groupId, updated)
            if (result == null) startGeocoding(groupId)
        }
    }

    // 1件削除
    fun deleteDelivery(id: String) {
        val groupId = _currentGroupId.value ?: return
        val updated = (_deliveries.value ?: return).filter { it.id != id }
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, updated)
        prefs.edit().remove("file_uri_$groupId").apply()
    }

    fun appendDeliveries(newItems: List<Delivery>) {
        val groupId = _currentGroupId.value ?: return
        val existing = _deliveries.value ?: emptyList()
        val startOrder = (existing.maxOfOrNull { it.order } ?: 0) + 1
        val reordered = newItems.mapIndexed { i, d -> d.copy(order = startOrder + i) }
        commitDeliveries(groupId, existing + reordered)
        startGeocoding(groupId)
    }

    fun replaceDeliveries(newItems: List<Delivery>) {
        val groupId = _currentGroupId.value ?: return
        val reordered = newItems.mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, reordered)
        startGeocoding(groupId)
    }

    // 複数件削除
    fun deleteDeliveries(ids: Set<String>) {
        val groupId = _currentGroupId.value ?: return
        val updated = (_deliveries.value ?: return).filter { it.id !in ids }
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, updated)
        prefs.edit().remove("file_uri_$groupId").apply()
    }

    // リストタブ表示時: 元ファイルと差分があるアイテムだけ更新
    fun refreshFromFile() {
        val groupId = _currentGroupId.value ?: return
        val uriStr = prefs.getString("file_uri_$groupId", null) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val uri = android.net.Uri.parse(uriStr)
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText() ?: return@launch
                val fileEntries = AddressParser.parse(text)
                if (fileEntries.isEmpty()) return@launch

                val current = withContext(Dispatchers.Main) {
                    _deliveries.value?.toList() ?: emptyList()
                }

                var hasChanges = fileEntries.size != current.size
                val merged = fileEntries.mapIndexed { i, entry ->
                    val newName = entry.name.ifBlank { null }
                    val existing = current.getOrNull(i)
                    // 同一判定: syncToFile済みファイル（name+address一致）または元ファイル（住所がname相当）
                    val isSame = existing != null && (
                        (existing.name == newName && existing.address == entry.address) ||
                        (newName == null && existing.name == entry.address)
                    )
                    if (isSame) {
                        existing!!.copy(order = i + 1)
                    } else {
                        hasChanges = true
                        val addrSame = existing?.address == entry.address
                        (existing ?: Delivery(order = i + 1, name = newName, address = entry.address)).copy(
                            order = i + 1,
                            name = newName,
                            address = entry.address,
                            lat = if (addrSame) existing?.lat ?: 0.0 else 0.0,
                            lng = if (addrSame) existing?.lng ?: 0.0 else 0.0,
                            isGeocoded = addrSame && (existing?.isGeocoded ?: false)
                        )
                    }
                }

                if (!hasChanges) return@launch

                withContext(Dispatchers.Main) {
                    _deliveries.value = merged
                    saveGroupDeliveries(groupId, merged)
                    updateAllDeliveries(groupId, merged)
                }
                startGeocoding(groupId)
            } catch (_: Exception) {}
        }
    }

    fun addRoom(deliveryId: String, roomNumber: String) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList + Room(number = roomNumber)) else d
        }
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun updateRoom(deliveryId: String, roomId: String, note: String, isCompleted: Boolean) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList.map { r ->
                if (r.id == roomId) r.copy(note = note, isCompleted = isCompleted) else r
            }) else d
        }
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun setRoomStatus(deliveryId: String, roomId: String, status: String) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
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
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun deleteRoom(deliveryId: String, roomId: String) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = d.roomList.filter { it.id != roomId }) else d
        }
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun clearRooms(deliveryId: String) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value ?: return
        val updated = list.map { d -> if (d.id == deliveryId) d.copy(rooms = emptyList()) else d }
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun clearPhotos(deliveryId: String) {
        val groupId = _currentGroupId.value ?: return
        val updated = _deliveries.value?.map { d ->
            if (d.id == deliveryId) d.copy(photoUris = emptyList(), photoUri = null) else d
        } ?: return
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun editNote(id: String, note: String) {
        val groupId = _currentGroupId.value ?: return
        val updated = _deliveries.value?.map { d ->
            if (d.id == id) d.copy(note = note) else d
        } ?: return
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun updateTimeSlotAndPackage(id: String, timeSlot: String?, packageCount: Int) {
        val groupId = _currentGroupId.value ?: return
        val updated = (_deliveries.value ?: emptyList()).map { d ->
            if (d.id == id) d.copy(timeSlot = timeSlot, packageCount = packageCount) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun batchUpdateTimeSlot(ids: Set<String>, timeSlot: String?) {
        val groupId = _currentGroupId.value ?: return
        val updated = (_deliveries.value ?: emptyList()).map { d ->
            if (d.id in ids) d.copy(timeSlot = timeSlot) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun addPhoto(id: String, photoUri: String) {
        val groupId = _currentGroupId.value ?: return
        val updated = _deliveries.value?.map { d ->
            if (d.id == id) d.copy(photoUris = d.allPhotoUris + photoUri, photoUri = null) else d
        } ?: return
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    fun removePhoto(id: String, index: Int) {
        val groupId = _currentGroupId.value ?: return
        val updated = _deliveries.value?.map { d ->
            if (d.id == id) {
                val newUris = d.allPhotoUris.toMutableList().also { if (index in it.indices) it.removeAt(index) }
                d.copy(photoUris = newUris, photoUri = null)
            } else d
        } ?: return
        _deliveries.value = updated
        saveGroupDeliveries(groupId, updated)
        updateAllDeliveries(groupId, updated)
    }

    // ドラッグ並べ替え後に呼ぶ: 新しい順序でorder番号を振り直して保存
    fun reorderDeliveries(newList: List<Delivery>) {
        val groupId = _currentGroupId.value ?: return
        commitDeliveries(groupId, newList.mapIndexed { i, d -> d.copy(order = i + 1) })
        prefs.edit().remove("file_uri_$groupId").apply()
    }

    fun clearCurrentGroup() {
        val groupId = _currentGroupId.value ?: return
        _deliveries.value = emptyList()
        saveGroupDeliveries(groupId, emptyList())
        updateAllDeliveries(groupId, emptyList())
        prefs.edit().remove("file_uri_$groupId").apply()
    }

    fun setAreaHint(area: String) {
        val groupId = _currentGroupId.value ?: return
        GeocodingClient.areaHint = area
        _areaHint.value = area
        prefs.edit().putString("area_hint_$groupId", area).apply()
    }

    fun setLocationBias(lat: Double, lng: Double) {
        GeocodingClient.biasLat = lat
        GeocodingClient.biasLng = lng
    }

    // ---- 内部処理 ----

    private fun startGeocoding(groupId: String) {
        geocodingJobs[groupId]?.cancel()  // 前回のジオコーディングをキャンセル
        val originalList = _deliveries.value?.toList() ?: return
        if (originalList.isEmpty()) return

        val job = viewModelScope.launch {
            var workingList = originalList.toMutableList()
            var failedCount = 0

            originalList.forEachIndexed { index, delivery ->
                if (_currentGroupId.value != groupId) return@launch

                _geocodingProgress.value = GeocodingProgress(index + 1, originalList.size, true)
                if (!delivery.isGeocoded) {
                    var result = GeocodingClient.geocode(delivery.address)
                    if (result == null) {
                        // 住所APIでヒットしなかった場合は店名・施設名として Places API で検索
                        result = GeocodingClient.searchPlaces(delivery.address).firstOrNull()?.let { place ->
                            GeocodingClient.GeoResult(place.lat, place.lng, place.address)
                        }
                    }
                    if (result != null) {
                        val officialName = result.formattedAddress.ifBlank { delivery.address }
                        workingList = workingList.map { d ->
                            if (d.id == delivery.id) d.copy(
                                name = if (d.name.isNullOrBlank()) delivery.address else d.name,
                                address = officialName,
                                lat = result.lat,
                                lng = result.lng,
                                isGeocoded = true,
                                geocodedAddress = null
                            ) else d
                        }.toMutableList()
                        // ジオコーディング中に addPinFromLocation で追加されたピンを失わないようにマージ
                        val current = _deliveries.value ?: emptyList()
                        val merged = current.map { d -> workingList.find { it.id == d.id } ?: d }
                        if (_currentGroupId.value == groupId) {
                            _deliveries.value = merged
                        }
                        saveGroupDeliveries(groupId, merged)
                        updateAllDeliveries(groupId, merged)
                    } else {
                        failedCount++
                    }
                    delay(200)
                }
            }
            if (_currentGroupId.value == groupId) {
                if (failedCount > 0) {
                    _errorMessage.value = "住所を検索できなかった件数: ${failedCount}件\nネットワーク接続を確認してください。"
                }
                val finalList = _deliveries.value ?: workingList
                exportToDownloads(groupId, finalList)
                // isRunning=false を先に通知するとMainActivityがsubtitleをnullにしてしまうため、
                // 先にdeliveriesを再通知して件数を表示してから完了フラグを立てる
                _deliveries.value = finalList
                _geocodingProgress.value = GeocodingProgress(originalList.size, originalList.size, false)
            }
        }
        geocodingJobs[groupId] = job
    }

    private fun updateAllDeliveries(groupId: String, list: List<Delivery>) {
        val map = (_allDeliveries.value ?: emptyMap()).toMutableMap()
        map[groupId] = list
        _allDeliveries.value = map
    }

    private fun saveGroups() {
        prefs.edit().putString("groups", gson.toJson(_groups.value)).apply()
    }

    private fun saveGroupDeliveries(groupId: String, list: List<Delivery>) {
        prefs.edit().putString("group_$groupId", gson.toJson(list)).apply()
    }

    private fun loadGroupDeliveries(groupId: String): List<Delivery> {
        val json = prefs.getString("group_$groupId", null) ?: return emptyList()
        val type = object : TypeToken<List<Delivery>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun loadAll() {
        // グループ一覧を読み込む
        val groupsJson = prefs.getString("groups", null)
        val loadedGroups: List<DeliveryGroup> = if (groupsJson != null) {
            val type = object : TypeToken<List<DeliveryGroup>>() {}.type
            try { gson.fromJson(groupsJson, type) ?: emptyList() } catch (_: Exception) { emptyList() }
        } else {
            // 旧データ移行: 既存の"deliveries"があればデフォルトグループに移行
            val legacy = prefs.getString("deliveries", null)
            if (legacy != null) {
                val defaultGroup = DeliveryGroup(name = "訪問先リスト", colorHex = colorForIndex(0))
                val type = object : TypeToken<List<Delivery>>() {}.type
                val legacyList: List<Delivery> = try { gson.fromJson(legacy, type) ?: emptyList() } catch (_: Exception) { emptyList() }
                saveGroupDeliveries(defaultGroup.id, legacyList)
                prefs.edit().remove("deliveries").apply()
                listOf(defaultGroup)
            } else {
                emptyList()  // 新規インストール: デフォルトグループを作らない
            }
        }
        // 旧グループ名を移行
        val migrated = loadedGroups.map { g ->
            when (g.name) {
                "配達リスト", "訪問リスト" -> g.copy(name = "訪問先リスト")
                else -> g
            }
        }
        // 移行で名前が変わった場合、旧ファイルを削除して新しい名前で出力
        loadedGroups.zip(migrated).forEach { (old, new) ->
            if (old.name != new.name) {
                deleteDownloadsFile(old.id, old.name)
                val list = loadGroupDeliveries(new.id)
                if (list.isNotEmpty()) exportToDownloads(new.id, list)
            }
        }

        val finalGroups = migrated

        _groups.value = finalGroups
        saveGroups()

        // 対応するグループがない孤立ファイルを削除、逆にファイルがないグループは再作成
        cleanupOrphanedDownloadFiles(finalGroups)
        createMissingDownloadFiles(finalGroups)

        // 全グループの訪問先リストを読み込む
        val allMap = mutableMapOf<String, List<Delivery>>()
        finalGroups.forEach { group ->
            allMap[group.id] = loadGroupDeliveries(group.id)
        }
        _allDeliveries.value = allMap

        // 現在のグループを復元
        val savedId = prefs.getString("current_group_id", null)
        val targetId = if (savedId != null && finalGroups.any { it.id == savedId }) savedId
                       else finalGroups.firstOrNull()?.id ?: ""
        _currentGroupId.value = targetId
        _deliveries.value = allMap[targetId] ?: emptyList()
    }
}
