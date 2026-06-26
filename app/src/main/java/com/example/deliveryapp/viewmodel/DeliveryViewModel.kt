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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.DeliveryGroup
import com.rodgers.routist.model.Room
import com.rodgers.routist.model.colorForIndex
import com.rodgers.routist.db.KnownAddressDao
import com.rodgers.routist.db.KnownAddressEntity
import com.rodgers.routist.repository.DeliveryRepository
import com.rodgers.routist.util.AddressParser
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.DeliveryGeofenceManager
import com.rodgers.routist.util.GeocodingApi
import com.rodgers.routist.util.GeocodingClient
import com.rodgers.routist.util.GeocodingManager
import com.rodgers.routist.util.RouteOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val geocodingManager: GeocodingManager,
    private val geocodingApi: GeocodingApi,
    private val knownAddressDao: KnownAddressDao,
    internal val geofenceManager: DeliveryGeofenceManager
) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("haireel_prefs", android.content.Context.MODE_PRIVATE)

    // ─── バン荷室レイアウト（複数ビュー対応）─────────────────────
    private val _vanLayout = MutableStateFlow(com.rodgers.routist.model.VanLayout())
    val vanLayout: StateFlow<com.rodgers.routist.model.VanLayout> = _vanLayout.asStateFlow()

    fun loadVanLayout(groupId: String) {
        val json = prefs.getString("van_layout2_$groupId", null) ?: return
        runCatching {
            val arr = org.json.JSONArray(json)
            val views = (0 until arr.length()).map { vi ->
                val v = arr.getJSONObject(vi)
                val pinsArr = v.optJSONArray("pins")
                val pins = if (pinsArr != null) (0 until pinsArr.length()).map { pi ->
                    val p = pinsArr.getJSONObject(pi)
                    com.rodgers.routist.model.VanLayoutPin(
                        id = p.getString("id"),
                        xPercent = p.getDouble("x").toFloat(),
                        yPercent = p.getDouble("y").toFloat(),
                        deliveryId = p.getString("deliveryId"),
                        orderLabel = p.getInt("orderLabel")
                    )
                } else emptyList()
                com.rodgers.routist.model.VanView(
                    id = v.getString("id"),
                    name = v.getString("name"),
                    photoUri = v.optString("photoUri", ""),
                    pins = pins
                )
            }
            _vanLayout.value = com.rodgers.routist.model.VanLayout(views)
        }
    }

    fun saveVanLayout(groupId: String, layout: com.rodgers.routist.model.VanLayout) {
        _vanLayout.value = layout
        val arr = org.json.JSONArray()
        layout.views.forEach { view ->
            val pinsArr = org.json.JSONArray()
            view.pins.forEach { pin ->
                pinsArr.put(org.json.JSONObject().apply {
                    put("id", pin.id); put("x", pin.xPercent.toDouble())
                    put("y", pin.yPercent.toDouble()); put("deliveryId", pin.deliveryId)
                    put("orderLabel", pin.orderLabel)
                })
            }
            arr.put(org.json.JSONObject().apply {
                put("id", view.id); put("name", view.name)
                put("photoUri", view.photoUri); put("pins", pinsArr)
            })
        }
        prefs.edit().putString("van_layout2_$groupId", arr.toString()).apply()
    }

    fun clearVanLayout(groupId: String) {
        _vanLayout.value = com.rodgers.routist.model.VanLayout()
        prefs.edit().remove("van_layout2_$groupId").apply()
    }

    companion object {
        private const val TAG = "DeliveryViewModel"
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
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val newRooms = roomNumbers.map { Room(number = it) }
        val updated = list.map { d ->
            if (d.id == deliveryId) d.copy(rooms = newRooms) else d
        }
        commitDeliveries(groupId, updated)
    }

    internal val geocodingJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

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

    internal val _geocodingProgress = MutableStateFlow<GeocodingProgress?>(null)
    val geocodingProgress: StateFlow<GeocodingProgress?> = _geocodingProgress.asStateFlow()

    internal val _geocodingFailedCount = MutableStateFlow(0)
    val geocodingFailedCount: StateFlow<Int> = _geocodingFailedCount.asStateFlow()
    fun clearGeocodingFailure() { _geocodingFailedCount.value = 0 }
    fun retryGeocoding() { startGeocoding(_currentGroupId.value) }

    internal val _areaHint = MutableStateFlow("")  // 値はinitで現在グループから読み込む
    val areaHint: StateFlow<String> = _areaHint.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()
    fun setSelectMode(enabled: Boolean) { _isSelectMode.value = enabled }

    internal val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    fun clearError() { _errorMessage.value = null }

    // 候補選択ダイアログでユーザーが選んだ結果を直接適用する
    fun applyCandidate(deliveryId: String, name: String, address: String, lat: Double, lng: Double) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == deliveryId) d.copy(
                name = name.ifBlank { d.name },
                address = address,
                lat = lat,
                lng = lng,
                isGeocoded = true,
                geocodedAddress = null
            ) else d
        }
        commitDeliveries(groupId, updated)
    }

    data class PinLocation(val lat: Double, val lng: Double)
    internal val _pinAddedFromMap = MutableSharedFlow<PinLocation>(extraBufferCapacity = 1)
    val pinAddedFromMap: SharedFlow<PinLocation> = _pinAddedFromMap.asSharedFlow()

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
        com.rodgers.routist.ui.RouteJinWidget.saveStats(
            getApplication(), groupName,
            list.count { it.isCompleted }, list.size
        )
        // 未完了配達先のジオフェンスを再登録（全グループ対象）
        val allUncompleted = _allDeliveries.value.values.flatten()
            .filter { it.hasLocation && !it.isCompleted }
        geofenceManager.register(allUncompleted)
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

    fun linkPatternToGroup(groupId: String, patternId: Int) {
        val updated = _groups.value.map {
            if (it.id == groupId) it.copy(patternId = patternId) else it
        }
        _groups.value = updated
        saveGroups()
    }

    data class GeocodingProgress(val current: Int, val total: Int, val isRunning: Boolean, val successCount: Int = 0)

    init {
        // ユーザーが設定したAPIキーのみ使用（フォールバックなし）
        // 未設定の場合は空文字のまま → ジオコーディングは REQUEST_DENIED になりUIで案内
        val userKey = try { AppSettings.getUserApiKey(getApplication()) } catch (_: Exception) { "" }
        geocodingApi.configure(userKey)
        viewModelScope.launch {
            loadAll()
            val groupId = _currentGroupId.value
            repo.migrateGlobalAreaHint(groupId)
            applyAreaHintForGroup(groupId)
        }
        // 既存配達先を住所履歴に一括登録（v2: DBスキーマ修正後に再実行されるよう番号管理）
        viewModelScope.launch(Dispatchers.IO) {
            if (!prefs.getBoolean("known_addr_backfill_v2", false)) {
                try {
                    repo.getAllDeliveries()
                        .filter { it.address.isNotBlank() }
                        .distinctBy { it.address.trim() }
                        .forEach { d ->
                            knownAddressDao.insertIfNew(KnownAddressEntity(
                                address         = d.address.trim(),
                                name            = d.name,
                                deliveryCount   = 1,
                                lastDeliveredAt = System.currentTimeMillis()
                            ))
                        }
                    prefs.edit().putBoolean("known_addr_backfill_v2", true).apply()
                } catch (_: Exception) { }
            }
        }
    }

    internal fun applyAreaHintForGroup(groupId: String) {
        val hint = repo.getAreaHint(groupId)
        _areaHint.value = hint
        geocodingApi.setAreaHint(hint)
        // 配達地域の中心座標を取得して位置バイアスをセット
        if (hint.isNotBlank()) {
            val keyword = hint.split(Regex("[,，、]")).first().trim()
            viewModelScope.launch {
                val geo = geocodingApi.geocodeExact(keyword)
                if (geo != null) geocodingApi.setBias(geo.lat, geo.lng)
            }
        } else {
            geocodingApi.setBias(0.0, 0.0)
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

        // APIキー未設定の場合はジオコーディングをスキップして警告
        val hasKey = com.rodgers.routist.util.AppSettings.hasUserApiKey(getApplication())
        if (!hasKey) {
            _errorMessage.value = "Google APIキーが未設定のため地図に表示できません。\n設定タブからAPIキーを登録してください。"
            return
        }
        startGeocoding(groupId)
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
        repo.normalizeAllAddressesToFullWidth()
        withContext(Dispatchers.Main) { loadAll() }
    }

    /** ふりがなのみ更新（ジオコーディング不要） */
    fun updateNameKana(id: String, kana: String?) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) d.copy(nameKana = kana) else d
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

    fun optimizeRoute(currentLat: Double, currentLng: Double) {
        val groupId = _currentGroupId.value
        val list = _deliveries.value
        val optimized = RouteOptimizer.optimize(list, currentLat, currentLng)
            .mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, optimized)
    }

    // 逆ジオコーディング: 地図長押しで座標からピンを追加
    fun addPinFromLocation(lat: Double, lng: Double) {
        // グループが存在しない場合はデフォルトグループを作成してから追加
        if (_currentGroupId.value.isBlank() || _groups.value.none { it.id == _currentGroupId.value }) {
            val newGroup = createGroup("ルート 1")
            switchGroup(newGroup.id)
        }
        val groupId = _currentGroupId.value
        _mapFilter.value = null

        // ピンを即座に追加（座標を仮住所として使用）
        val tempAddress = "${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}"
        val newDelivery = Delivery(
            order = (_deliveries.value.size) + 1,
            address = tempAddress,
            lat = lat,
            lng = lng,
            isGeocoded = true
        )
        val newList = _deliveries.value + newDelivery
        commitDeliveries(groupId, newList)
        _pinAddedFromMap.tryEmit(PinLocation(lat, lng))

        // 住所を非同期で取得して更新（_deliveries.value で最新状態を参照）
        viewModelScope.launch {
            val result = geocodingApi.reverseGeocode(lat, lng) ?: return@launch
            val address = result.formattedAddress.ifBlank { return@launch }
            if (_currentGroupId.value != groupId) return@launch  // グループ切替後はスキップ
            val updated = _deliveries.value.map { d ->
                if (d.id == newDelivery.id) d.copy(address = address) else d
            }
            commitDeliveries(groupId, updated)
        }
    }

    // アイテム編集: 店名・住所を修正して再ジオコーディング
    fun editDelivery(id: String, newName: String, newAddress: String) {
        val groupId = _currentGroupId.value
        viewModelScope.launch {
            var result = geocodingManager.geocodeWithFallback(newAddress, newAddress)
            // 位置バイアス未設定の場合は補完
            val aHint = _areaHint.value
            if (aHint.isNotBlank() && !geocodingApi.hasBias()) {
                val keyword = aHint.split(Regex("[,，、]")).first().trim()
                val areaGeo = geocodingApi.geocodeExact(keyword)
                if (areaGeo != null) geocodingApi.setBias(areaGeo.lat, areaGeo.lng)
            }
            // エリア外ヒット時は配達地域キーワード＋ローカル部分で再試行
            if (result != null && aHint.isNotBlank() && !isInArea(result.formattedAddress)) {
                val localPart = extractLocalPart(newAddress)
                val keywords = aHint.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
                for (keyword in keywords) {
                    val fixed = geocodingApi.geocodeExact("$keyword $localPart")
                    if (fixed != null && isInArea(fixed.formattedAddress)) { result = fixed; break }
                    delay(100)
                }
                // geocodeExact で解決しなかった場合は Places API（位置バイアスあり）で再試行
                if (!isInArea(result?.formattedAddress ?: "")) {
                    val fixedPlace = geocodingApi.searchPlaces(localPart).firstOrNull()?.let {
                        GeocodingClient.GeoResult(it.lat, it.lng, it.address)
                    }
                    if (fixedPlace != null && isInArea(fixedPlace.formattedAddress)) result = fixedPlace
                }
            }
            if (result == null) {
                _errorMessage.value = if (geocodingApi.isRequestDenied) {
                    "APIキーが使用できません。\nGoogle Cloud Console で Geocoding API の課金設定を確認してください。"
                } else {
                    "住所を検索できませんでした。\nネットワーク接続を確認してください。"
                }
            }
            val officialName = result?.formattedAddress?.ifBlank { newAddress } ?: newAddress
            val updated = _deliveries.value.map { d ->
                if (d.id == id) d.copy(
                    name = newName.ifBlank { null },
                    address = officialName,
                    geocodedAddress = null,
                    lat = result?.lat ?: d.lat,
                    lng = result?.lng ?: d.lng,
                    isGeocoded = result != null
                ) else d
            }
            commitDeliveries(groupId, updated)
            if (result == null) startGeocoding(groupId)
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
        startGeocoding(groupId)
        saveKnownAddresses(newItems)
    }

    fun replaceDeliveries(newItems: List<Delivery>) {
        val groupId = _currentGroupId.value
        val reordered = newItems.mapIndexed { i, d -> d.copy(order = i + 1) }
        commitDeliveries(groupId, reordered)
        startGeocoding(groupId)
        saveKnownAddresses(newItems)
    }

    /** 追加・置換時に住所をknown_addressesに自動保存する */
    private fun saveKnownAddresses(items: List<Delivery>) {
        viewModelScope.launch(Dispatchers.IO) {
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
        }
    }

    /** 住所履歴から1件削除 */
    fun deleteKnownAddress(entity: com.rodgers.routist.db.KnownAddressEntity) =
        viewModelScope.launch(Dispatchers.IO) { knownAddressDao.delete(entity) }

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
            if (d.id == deliveryId) d.copy(photoUris = emptyList(), photoUri = null) else d
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
            if (d.id == id) d.copy(photoUris = d.allPhotoUris + photoUri, photoUri = null) else d
        }
        commitDeliveries(groupId, updated)
    }

    fun removePhoto(id: String, index: Int) {
        val groupId = _currentGroupId.value
        val updated = _deliveries.value.map { d ->
            if (d.id == id) {
                val newUris = d.allPhotoUris.toMutableList().also { if (index in it.indices) it.removeAt(index) }
                d.copy(photoUris = newUris, photoUri = null)
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

    fun clearCurrentGroup() {
        val groupId = _currentGroupId.value
        commitDeliveries(groupId, emptyList())
        repo.clearFileUri(groupId)
    }

    fun setAreaHint(area: String) {
        val groupId = _currentGroupId.value
        // 複数エリア指定時は最初のエリアをジオコーディングのバイアスに使用
        geocodingApi.setAreaHint(area.split(Regex("[,，、]")).first().trim())
        _areaHint.value = area
        repo.saveAreaHint(groupId, area)
        // 未ジオコーディング件の再試行＋エリア外住所の修正
        if (area.isNotBlank()) {
            startGeocoding(groupId)
        }
    }

    fun setLocationBias(lat: Double, lng: Double) {
        geocodingApi.setBias(lat, lng)
    }

    // ---- 内部処理 ----

    // 配達地域のいずれかのキーワードを含む住所かどうか判定
    private fun isInArea(address: String): Boolean {
        val hint = _areaHint.value
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
        val keywords = _areaHint.value.split(Regex("[,，、]")).map { it.trim() }.filter { it.isNotBlank() }
        for (kw in keywords) {
            if (s.startsWith(kw)) return s.removePrefix(kw).trim()
        }
        // その他の市・郡を除去
        s = s.replace(Regex("^.{1,8}[市郡]"), "")
        return s.trim().ifBlank { address }
    }

    internal fun startGeocoding(groupId: String) {
        geocodingJobs[groupId]?.cancel()
        _geocodingProgress.value = null
        val originalList = _deliveries.value.toList()
        if (originalList.isEmpty()) return

        val job = viewModelScope.launch {
            val failedCount = geocodingManager.batchGeocode(
                deliveries = originalList,
                areaHint = _areaHint.value,
                isInArea = ::isInArea,
                extractLocalPart = ::extractLocalPart,
                isGroupActive = { _currentGroupId.value == groupId },
                onProgress = { current, total ->
                    _geocodingProgress.value = GeocodingProgress(current, total, true)
                },
                onResult = { result ->
                    if (_currentGroupId.value == groupId) {
                        val current = _deliveries.value
                        if (_currentGroupId.value != groupId) return@batchGeocode  // 二重チェック
                        val updated = current.map { d ->
                            if (d.id == result.deliveryId) d.copy(
                                name = when {
                                    !d.name.isNullOrBlank() -> d.name
                                    result.suggestedName != null -> result.suggestedName
                                    else -> null
                                },
                                address = result.officialAddress,
                                lat = result.lat,
                                lng = result.lng,
                                isGeocoded = true,
                                geocodedAddress = null
                            ) else d
                        }
                        commitDeliveries(groupId, updated)
                    }
                }
            )

            if (_currentGroupId.value == groupId) {
                if (failedCount > 0) {
                    _geocodingFailedCount.value = failedCount
                }
                val finalList = _deliveries.value
                exportToDownloads(groupId, finalList)
                _deliveries.value = finalList
                val successCount = originalList.size - failedCount
                _geocodingProgress.value = GeocodingProgress(originalList.size, originalList.size, false, successCount)
            }
        }
        geocodingJobs[groupId] = job
    }

    internal fun updateAllDeliveries(groupId: String, list: List<Delivery>) {
        val map = _allDeliveries.value.toMutableMap()
        map[groupId] = list
        _allDeliveries.value = map
    }

    internal fun saveGroups() {
        val groups = _groups.value
        viewModelScope.launch(Dispatchers.IO) { repo.saveGroups(groups) }
    }

    internal fun saveGroupDeliveries(groupId: String, list: List<Delivery>) {
        viewModelScope.launch(Dispatchers.IO) { repo.saveDeliveries(groupId, list) }
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
                _deliveries.value = data.allDeliveries[targetId] ?: emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "初期データ読み込み失敗", e)
            _errorMessage.value = "データの読み込みに失敗しました"
        }
    }

    override fun onCleared() {
        super.onCleared()
        geocodingJobs.values.forEach { it.cancel() }
        geocodingJobs.clear()
    }
}
