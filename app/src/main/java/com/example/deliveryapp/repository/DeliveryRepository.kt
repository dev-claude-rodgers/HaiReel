package com.rodgers.routist.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rodgers.routist.db.AppDatabase
import com.rodgers.routist.db.toDelivery
import com.rodgers.routist.db.toEntity
import com.rodgers.routist.db.toGroup
import com.rodgers.routist.model.Delivery
import com.rodgers.routist.model.DeliveryGroup
import com.rodgers.routist.model.colorForIndex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveryRepository @Inject constructor(
    private val db: AppDatabase,
    private val prefs: SharedPreferences,
    private val gson: Gson = Gson()
) {

    // ---- Delivery CRUD ----

    suspend fun saveDeliveries(groupId: String, list: List<Delivery>) {
        db.deliveryDao().deleteByGroup(groupId)
        if (list.isNotEmpty()) db.deliveryDao().upsertAll(list.map { it.toEntity(groupId) })
    }

    suspend fun loadDeliveries(groupId: String): List<Delivery> =
        db.deliveryDao().getByGroup(groupId).map { it.toDelivery() }

    /** 全グループの全配達先を返す（住所履歴バックフィル用） */
    suspend fun getAllDeliveries(): List<Delivery> =
        db.deliveryDao().getAll().map { it.toDelivery() }

    /** 全配達先の住所・名前を全角に一括変換して保存 */
    suspend fun normalizeAllAddressesToFullWidth() {
        val fw = com.rodgers.routist.util.AddressParser::toFullWidth
        val all = db.deliveryDao().getAll()
        val changed = all.filter { e ->
            fw(e.address) != e.address || (e.name != null && fw(e.name) != e.name)
        }
        if (changed.isEmpty()) return
        db.deliveryDao().upsertAll(changed.map { e ->
            e.copy(address = fw(e.address), name = e.name?.let { fw(it) })
        })
    }

    // ---- Group CRUD ----

    suspend fun saveGroups(groups: List<DeliveryGroup>) {
        db.deliveryGroupDao().upsertAll(groups.mapIndexed { i, g -> g.toEntity(i) })
    }

    suspend fun deleteGroup(groupId: String) {
        db.deliveryGroupDao().deleteById(groupId)
        db.deliveryDao().deleteByGroup(groupId)
    }

    // ---- SharedPreferences: current group ----

    fun getCurrentGroupId(): String? = prefs.getString("current_group_id", null)

    fun saveCurrentGroupId(groupId: String) {
        prefs.edit().putString("current_group_id", groupId).apply()
    }

    // ---- SharedPreferences: area hint ----

    fun getAreaHint(groupId: String): String =
        if (groupId.isNotBlank()) prefs.getString("area_hint_$groupId", "") ?: "" else ""

    fun saveAreaHint(groupId: String, hint: String) {
        prefs.edit().putString("area_hint_$groupId", hint).apply()
    }

    /** グローバル "area_hint" キーが残っていれば現在グループに移行して削除する。戻り値は移行した値（なければ null）。 */
    fun migrateGlobalAreaHint(groupId: String): String? {
        val legacy = prefs.getString("area_hint", null) ?: return null
        if (groupId.isNotBlank()) {
            prefs.edit().putString("area_hint_$groupId", legacy).remove("area_hint").apply()
        }
        return legacy
    }

    // ---- SharedPreferences: file URI ----

    fun getFileUri(groupId: String): String? = prefs.getString("file_uri_$groupId", null)

    fun saveFileUri(groupId: String, uri: String) {
        prefs.edit().putString("file_uri_$groupId", uri).apply()
    }

    fun clearFileUri(groupId: String) {
        prefs.edit().remove("file_uri_$groupId").apply()
    }

    // ---- SharedPreferences: download file URI ----

    fun getDownloadFileUri(groupId: String): String? =
        prefs.getString("download_file_uri_$groupId", null)

    fun saveDownloadFileUri(groupId: String, uri: String) {
        prefs.edit().putString("download_file_uri_$groupId", uri).apply()
    }

    fun clearDownloadFileUri(groupId: String) {
        prefs.edit().remove("download_file_uri_$groupId").apply()
    }

    /** グループ削除時: SharedPreferences の残存キーを一括削除 */
    fun clearGroupPrefs(groupId: String) {
        prefs.edit()
            .remove("group_$groupId")
            .remove("file_uri_$groupId")
            .remove("download_file_uri_$groupId")
            .remove("area_hint_$groupId")
            .apply()
    }

    // ---- 初回起動 / マイグレーション ----

    data class InitialData(
        val groups: List<DeliveryGroup>,
        val allDeliveries: Map<String, List<Delivery>>
    )

    suspend fun loadInitialData(): InitialData {
        val groups = loadOrMigrateGroups()

        val allEntities = db.deliveryDao().getAll()
        val allDeliveries = mutableMapOf<String, List<Delivery>>()
        groups.forEach { group ->
            allDeliveries[group.id] = allEntities
                .filter { it.groupId == group.id }
                .sortedBy { it.order }
                .map { it.toDelivery() }
        }

        return InitialData(groups, allDeliveries)
    }

    private suspend fun loadOrMigrateGroups(): List<DeliveryGroup> {
        if (db.deliveryGroupDao().count() > 0) {
            return db.deliveryGroupDao().getAll().map { it.toGroup() }
        }

        // Room が空 → SharedPreferences から移行
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

        if (db.deliveryDao().count() == 0) {
            migrated.forEach { group ->
                val list = loadGroupDeliveriesFromPrefs(group.id)
                if (list.isNotEmpty()) db.deliveryDao().upsertAll(list.map { it.toEntity(group.id) })
            }
        }

        return migrated
    }

    private fun loadGroupsFromPrefs(): List<DeliveryGroup> {
        val json = prefs.getString("groups", null) ?: run {
            val legacy = prefs.getString("deliveries", null) ?: return emptyList()
            val defaultGroup = DeliveryGroup(name = "訪問先リスト", colorHex = colorForIndex(0))
            val type = object : TypeToken<List<Delivery>>() {}.type
            val legacyList: List<Delivery> = try {
                gson.fromJson(legacy, type) ?: emptyList()
            } catch (_: Exception) { emptyList() }
            prefs.edit()
                .putString("group_${defaultGroup.id}", gson.toJson(legacyList))
                .remove("deliveries")
                .apply()
            return listOf(defaultGroup)
        }
        val type = object : TypeToken<List<DeliveryGroup>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }

    private fun loadGroupDeliveriesFromPrefs(groupId: String): List<Delivery> {
        val json = prefs.getString("group_$groupId", null) ?: return emptyList()
        val type = object : TypeToken<List<Delivery>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (_: Exception) { emptyList() }
    }
}
