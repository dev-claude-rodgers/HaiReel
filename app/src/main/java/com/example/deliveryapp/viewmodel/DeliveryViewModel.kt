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
import com.rodgers.routist.db.AppDatabase
import com.rodgers.routist.db.toDelivery
import com.rodgers.routist.db.toEntity
import com.rodgers.routist.db.toGroup
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
    private val db = AppDatabase.getInstance(app)
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

    data class OutOfAreaItem(
        val delivery: Delivery,
        val candidates: List<GeocodingClient.GeoResult>
    )
    private val _outOfAreaCandidates = MutableLiveData<List<OutOfAreaItem>?>(null)
    val outOfAreaCandidates: LiveData<List<OutOfAreaItem>?> = _outOfAreaCandidates
    fun clearOutOfAreaCandidates() { _outOfAreaCandidates.value = null }

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
        viewModelScope.launch {
            loadAll()
            val groupId = _currentGroupId.value ?: ""
            val legacy = prefs.getString("area_hint", null)
            if (legacy != null && groupId.isNotBlank()) {
                prefs.edit().putString("area_hint_$groupId", legacy).remove("area_hint").apply()
            }
            applyAreaHintForGroup(groupId)
        }
    }

    private fun applyAreaHintForGroup(groupId: String) {
        val hint = if (groupId.isNotBlank()) prefs.getString("area_hint_$groupId", "") ?: "" else ""
        _areaHint.value = hint
        GeocodingClient.areaHint = hint
        // 配達地域の中心座標を取得して位置バイアスをセット
        if (hint.isNotBlank()) {
            val keyword = hint.split(Regex("[,，、]")).first().trim()
            viewModelScope.launch {
                val geo = GeocodingClient.geocodeExact(keyword)
                if (geo != null) {
                    GeocodingClient.biasLat = geo.lat
                    GeocodingClient.biasLng = geo.lng
                }
            }
        } else {
            GeocodingClient.biasLat = 0.0
            GeocodingClient.biasLng = 0.0
        }
    }

    // ---- グループ操作 ----

    // リスト内の順番に基づいて全グループの色を再割り当てして保存（1番目=赤, 2番目=青…）
    private fun normalizeGroupColors() {
        val current = _groups.value ?: return
        _groups.value = current.mapIndexed { i, g -> g.copy(colorHex = colorForIndex(i)) }
        saveGroups()
    }

    fun createGroup(name: String): DeliveryGroup {
        val index = _groups.value?.size ?: 0
        val group = DeliveryGroup(name = name, colorHex = colorForIndex(index))
        _groups.value = (_groups.value ?: emptyList()) + group
        normalizeGroupColors()
        return _groups.value!!.last()
    }

    fun deleteGroup(groupId: String) {
        val group = _groups.value?.find { it.id == groupId }
        val updated = (_groups.value ?: emptyList()).filter { it.id != groupId }
        _groups.value = updated
        normalizeGroupColors()
        prefs.edit().remove("group_$groupId").apply()
        viewModelScope.launch(Dispatchers.IO) {
            db.deliveryDao().deleteByGroup(groupId)
            db.deliveryGroupDao().deleteById(groupId)
        }
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

    fun copyGroup(sourceGroupId: String) {
        val source = _groups.value?.find { it.id == sourceGroupId } ?: return
        // 末尾の " N"（スペース＋数字）を除いたベース名で番号を管理
        val baseName = source.name.replace(Regex(" \\d+$"), "")
        val existing = _groups.value ?: emptyList()
        val maxNum = existing.mapNotNull { g ->
            when {
                g.name == baseName -> 1
                g.name.matches(Regex("${Regex.escape(baseName)} \\d+")) ->
                    g.name.removePrefix("$baseName ").toIntOrNull()
                else -> null
            }
        }.maxOrNull() ?: 1
        val newGroup = createGroup("$baseName ${maxNum + 1}")
        val copied = (_allDeliveries.value?.get(sourceGroupId) ?: emptyList())
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        if (copied.isNotEmpty()) {
            saveGroupDeliveries(newGroup.id, copied)
            val allMap = (_allDeliveries.value ?: emptyMap()).toMutableMap()
            allMap[newGroup.id] = copied
            _allDeliveries.value = allMap
        }
        switchGroup(newGroup.id)
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
            val list = _allDeliveries.value?.get(groupId) ?: emptyList()
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
        val list = _allDeliveries.value?.get(groupId) ?: emptyList()
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
                        val list = db.deliveryDao().getByGroup(group.id).map { it.toDelivery() }
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
            val savedList = db.deliveryDao().getByGroup(groupId).map { it.toDelivery() }
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
            // 位置バイアス未設定の場合は補完
            val aHint = _areaHint.value ?: ""
            if (aHint.isNotBlank() && GeocodingClient.biasLat == 0.0 && GeocodingClient.biasLng == 0.0) {
                val keyword = aHint.split(Regex("[,，、]")).first().trim()
                val areaGeo = GeocodingClient.geocodeExact(keyword)
                if (areaGeo != null) {
                    GeocodingClient.biasLat = areaGeo.lat
                    GeocodingClient.biasLng = areaGeo.lng
                }
            }
            // エリア外ヒット時は配達地域キーワード＋ローカル部分で再試行
            if (result != null && aHint.isNotBlank() && !isInArea(result.formattedAddress)) {
                val localPart = extractLocalPart(newAddress)
                val keywords = aHint.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
                for (keyword in keywords) {
                    val fixed = GeocodingClient.geocodeExact("$keyword $localPart")
                    if (fixed != null && isInArea(fixed.formattedAddress)) { result = fixed; break }
                    delay(100)
                }
                // geocodeExact で解決しなかった場合は Places API（位置バイアスあり）で再試行
                if (!isInArea(result?.formattedAddress ?: "")) {
                    val fixedPlace = GeocodingClient.searchPlaces(localPart).firstOrNull()?.let {
                        GeocodingClient.GeoResult(it.lat, it.lng, it.address)
                    }
                    if (fixedPlace != null && isInArea(fixedPlace.formattedAddress)) result = fixedPlace
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
        // 複数エリア指定時は最初のエリアをジオコーディングのバイアスに使用
        GeocodingClient.areaHint = area.split(Regex("[,，、]")).first().trim()
        _areaHint.value = area
        prefs.edit().putString("area_hint_$groupId", area).apply()
        // 未ジオコーディング件の再試行＋エリア外住所の修正
        if (area.isNotBlank()) {
            startGeocoding(groupId)
        }
    }

    /** エリア外住所の候補を検索してダイアログ用に公開する */
    fun fetchOutOfAreaCandidates() {
        val hint = _areaHint.value ?: return
        if (hint.isBlank()) return
        val deliveries = _deliveries.value ?: return
        val outOfArea = deliveries.filter { it.isGeocoded && !isInArea(it.address) }
        if (outOfArea.isEmpty()) {
            _outOfAreaCandidates.value = emptyList()
            return
        }
        viewModelScope.launch {
            val initHint = _areaHint.value ?: ""
            if (initHint.isNotBlank() && GeocodingClient.biasLat == 0.0 && GeocodingClient.biasLng == 0.0) {
                val keyword = initHint.split(Regex("[,，、]")).first().trim()
                val areaGeo = GeocodingClient.geocodeExact(keyword)
                if (areaGeo != null) { GeocodingClient.biasLat = areaGeo.lat; GeocodingClient.biasLng = areaGeo.lng }
            }
            val items = outOfArea.map { delivery ->
                val candidates = mutableListOf<GeocodingClient.GeoResult>()
                val keywords = hint.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
                val localPart = extractLocalPart(delivery.address)
                // 店名で Places API 検索
                if (!delivery.name.isNullOrBlank()) {
                    GeocodingClient.searchPlaces(delivery.name).forEach { p ->
                        if (isInArea(p.address)) candidates.add(GeocodingClient.GeoResult(p.lat, p.lng, p.address))
                    }
                    delay(100)
                }
                // エリアキーワード + ローカル部分で geocodeExact
                for (keyword in keywords) {
                    if (candidates.size >= 5) break
                    val r = GeocodingClient.geocodeExact("$keyword $localPart")
                    if (r != null && isInArea(r.formattedAddress) && candidates.none { it.formattedAddress == r.formattedAddress }) candidates.add(r)
                    delay(100)
                }
                // ローカル部分単体で Places API 検索
                if (candidates.size < 5) {
                    GeocodingClient.searchPlaces(localPart).forEach { p ->
                        if (isInArea(p.address) && candidates.none { it.formattedAddress == p.address }) {
                            candidates.add(GeocodingClient.GeoResult(p.lat, p.lng, p.address))
                        }
                    }
                }
                OutOfAreaItem(delivery, candidates.take(5))
            }
            _outOfAreaCandidates.postValue(items)
        }
    }

    /** エリア外修正ダイアログで選択した候補を配達先に適用する */
    fun applyOutOfAreaFix(deliveryId: String, result: GeocodingClient.GeoResult) {
        val groupId = _currentGroupId.value ?: return
        val list = _deliveries.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == deliveryId }
        if (idx < 0) return
        list[idx] = list[idx].copy(address = result.formattedAddress, lat = result.lat, lng = result.lng, isGeocoded = true)
        commitDeliveries(groupId, list)
    }

    fun setLocationBias(lat: Double, lng: Double) {
        GeocodingClient.biasLat = lat
        GeocodingClient.biasLng = lng
    }

    // ---- 内部処理 ----

    // 配達地域のいずれかのキーワードを含む住所かどうか判定
    private fun isInArea(address: String): Boolean {
        val hint = _areaHint.value ?: return true
        if (hint.isBlank()) return true
        return hint.split(Regex("[,，、]"))
            .map { it.trim() }.filter { it.isNotBlank() }
            .any { keyword -> address.contains(keyword) }
    }

    // 住所から都道府県・最上位市郡を除いた「ローカル部分」を抽出
    private fun extractLocalPart(address: String): String {
        var s = address
        // 都道府県を除去
        s = s.replace(Regex("^.{2,4}[都道府県]"), "")
        // 配達地域キーワード自体が先頭にあれば除去（同キーワードを再付与するため）
        val keywords = (_areaHint.value ?: "").split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
        for (kw in keywords) {
            if (s.startsWith(kw)) return s.removePrefix(kw).trim()
        }
        // その他の市・郡を除去
        s = s.replace(Regex("^.{1,8}[市郡]"), "")
        return s.trim().ifBlank { address }
    }

    private fun startGeocoding(groupId: String) {
        geocodingJobs[groupId]?.cancel()  // 前回のジオコーディングをキャンセル
        val originalList = _deliveries.value?.toList() ?: return
        if (originalList.isEmpty()) return

        val job = viewModelScope.launch {
            var workingList = originalList.toMutableList()
            var failedCount = 0

            // 位置バイアス未設定の場合はエリアキーワードをジオコードして補完
            val initHint = _areaHint.value ?: ""
            if (initHint.isNotBlank() && GeocodingClient.biasLat == 0.0 && GeocodingClient.biasLng == 0.0) {
                val keyword = initHint.split(Regex("[,，、]")).first().trim()
                val areaGeo = GeocodingClient.geocodeExact(keyword)
                if (areaGeo != null) {
                    GeocodingClient.biasLat = areaGeo.lat
                    GeocodingClient.biasLng = areaGeo.lng
                }
            }

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
                    // エリア外ヒット時は配達地域キーワード＋ローカル部分で再試行
                    val aHint = _areaHint.value ?: ""
                    if (result != null && aHint.isNotBlank() && !isInArea(result.formattedAddress)) {
                        val localPart = extractLocalPart(delivery.address)
                        val keywords = aHint.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
                        for (keyword in keywords) {
                            val fixed = GeocodingClient.geocodeExact("$keyword $localPart")
                            if (fixed != null && isInArea(fixed.formattedAddress)) { result = fixed; break }
                            delay(100)
                        }
                        // geocodeExact で解決しなかった場合は Places API（位置バイアスあり）で再試行
                        if (!isInArea(result?.formattedAddress ?: "")) {
                            val fixedPlace = GeocodingClient.searchPlaces(localPart).firstOrNull()?.let {
                                GeocodingClient.GeoResult(it.lat, it.lng, it.address)
                            }
                            if (fixedPlace != null && isInArea(fixedPlace.formattedAddress)) result = fixedPlace
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
            // 第2パス: 既にジオコーディング済みでエリア外の住所を修正
            val hint2 = _areaHint.value ?: ""
            if (hint2.isNotBlank() && _currentGroupId.value == groupId) {
                val outOfArea = (_deliveries.value ?: emptyList()).filter { it.isGeocoded && !isInArea(it.address) }
                outOfArea.forEach { delivery ->
                    if (_currentGroupId.value != groupId) return@launch
                    val localPart = extractLocalPart(delivery.address)
                    for (keyword in hint2.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }) {
                        val fixed = GeocodingClient.geocodeExact("$keyword $localPart")
                        if (fixed != null && isInArea(fixed.formattedAddress)) {
                            val cur = _deliveries.value ?: emptyList()
                            val updated = cur.map { d ->
                                if (d.id == delivery.id) d.copy(address = fixed.formattedAddress, lat = fixed.lat, lng = fixed.lng) else d
                            }
                            _deliveries.value = updated
                            saveGroupDeliveries(groupId, updated)
                            updateAllDeliveries(groupId, updated)
                            break
                        }
                        delay(100)
                    }
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
        val groups = _groups.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.deliveryGroupDao().upsertAll(groups.mapIndexed { i, g -> g.toEntity(i) })
        }
    }

    private fun saveGroupDeliveries(groupId: String, list: List<Delivery>) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deliveryDao().deleteByGroup(groupId)
            if (list.isNotEmpty()) db.deliveryDao().upsertAll(list.map { it.toEntity(groupId) })
        }
    }

    private fun loadGroupDeliveriesFromPrefs(groupId: String): List<Delivery> {
        val json = prefs.getString("group_$groupId", null) ?: return emptyList()
        val type = object : TypeToken<List<Delivery>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private suspend fun loadAll() {
        // グループ一覧: Room が空なら SharedPreferences から移行
        val groupRoomCount = db.deliveryGroupDao().count()
        val finalGroups: List<DeliveryGroup>
        if (groupRoomCount == 0) {
            // SharedPreferences → Room グループ移行
            val prefsGroups = loadGroupsFromPrefs()
            val migrated = prefsGroups.map { g ->
                when (g.name) {
                    "配達リスト", "訪問リスト" -> g.copy(name = "訪問先リスト")
                    else -> g
                }
            }
            if (migrated.isNotEmpty()) {
                db.deliveryGroupDao().upsertAll(migrated.mapIndexed { i, g -> g.toEntity(i) })
            }
            // SharedPreferences → Room 配達データ移行
            val deliveryRoomCount = db.deliveryDao().count()
            if (deliveryRoomCount == 0) {
                migrated.forEach { group ->
                    val list = loadGroupDeliveriesFromPrefs(group.id)
                    if (list.isNotEmpty()) db.deliveryDao().upsertAll(list.map { it.toEntity(group.id) })
                }
            }
            finalGroups = migrated
        } else {
            finalGroups = db.deliveryGroupDao().getAll().map { it.toGroup() }
        }

        // Room から全配達データを読み込む
        val allEntities = db.deliveryDao().getAll()
        val allMap = mutableMapOf<String, List<Delivery>>()
        finalGroups.forEach { group ->
            allMap[group.id] = allEntities
                .filter { it.groupId == group.id }
                .sortedBy { it.order }
                .map { it.toDelivery() }
        }

        withContext(Dispatchers.Main) {
            _groups.value = finalGroups

            cleanupOrphanedDownloadFiles(finalGroups)
            createMissingDownloadFiles(finalGroups)

            _allDeliveries.value = allMap

            val savedId = prefs.getString("current_group_id", null)
            val targetId = if (savedId != null && finalGroups.any { it.id == savedId }) savedId
                           else finalGroups.firstOrNull()?.id ?: ""
            _currentGroupId.value = targetId
            _deliveries.value = allMap[targetId] ?: emptyList()
        }
    }

    private fun loadGroupsFromPrefs(): List<DeliveryGroup> {
        val json = prefs.getString("groups", null) ?: run {
            // 旧データ移行: "deliveries" キーがあればデフォルトグループを作成
            val legacy = prefs.getString("deliveries", null) ?: return emptyList()
            val defaultGroup = DeliveryGroup(name = "訪問先リスト", colorHex = colorForIndex(0))
            val type = object : TypeToken<List<Delivery>>() {}.type
            val legacyList: List<Delivery> = try { gson.fromJson(legacy, type) ?: emptyList() } catch (_: Exception) { emptyList() }
            prefs.edit().putString("group_${defaultGroup.id}", gson.toJson(legacyList)).remove("deliveries").apply()
            return listOf(defaultGroup)
        }
        val type = object : TypeToken<List<DeliveryGroup>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
}
