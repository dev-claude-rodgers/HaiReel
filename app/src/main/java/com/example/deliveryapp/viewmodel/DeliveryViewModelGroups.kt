package com.rodgers.routist.viewmodel

import androidx.lifecycle.viewModelScope
import com.rodgers.routist.model.DeliveryGroup
import com.rodgers.routist.model.colorForIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// グループ操作: DeliveryViewModel の extension functions
// ─────────────────────────────────────────────────

internal fun DeliveryViewModel.normalizeGroupColors() {
    val current = _groups.value
    _groups.value = current.mapIndexed { i, g -> g.copy(colorHex = colorForIndex(i)) }
    saveGroups()
}

fun DeliveryViewModel.createGroup(name: String): DeliveryGroup {
    val index = _groups.value.size
    val group = DeliveryGroup(name = name, colorHex = colorForIndex(index))
    _groups.value = _groups.value + group
    normalizeGroupColors()
    // last() の代わりに id で検索して競合状態を防ぐ
    return _groups.value.find { it.id == group.id } ?: group
}

fun DeliveryViewModel.deleteGroup(groupId: String) {
    geocodingJobs[groupId]?.cancel()
    geocodingJobs.remove(groupId)
    val group = _groups.value.find { it.id == groupId }
    val updated = _groups.value.filter { it.id != groupId }
    _groups.value = updated
    normalizeGroupColors()
    repo.clearGroupPrefs(groupId)
    viewModelScope.launch(Dispatchers.IO) { repo.deleteGroup(groupId) }
    group?.let { deleteDownloadsFile(groupId, it.name) }

    val allMap = _allDeliveries.value.toMutableMap()
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

fun DeliveryViewModel.copyGroup(sourceGroupId: String) {
    val source = _groups.value.find { it.id == sourceGroupId } ?: return
    val baseName = source.name.replace(Regex(" \\d+$"), "")
    val existing = _groups.value
    val maxNum = existing.mapNotNull { g ->
        when {
            g.name == baseName -> 1
            g.name.matches(Regex("${Regex.escape(baseName)} \\d+")) ->
                g.name.removePrefix("$baseName ").toIntOrNull()
            else -> null
        }
    }.maxOrNull() ?: 1
    val newGroup = createGroup("$baseName ${maxNum + 1}")
    val copied = (_allDeliveries.value[sourceGroupId] ?: emptyList())
        .mapIndexed { i, d -> d.copy(id = java.util.UUID.randomUUID().toString(), order = i + 1) }
    if (copied.isNotEmpty()) {
        saveGroupDeliveries(newGroup.id, copied)
        val allMap = _allDeliveries.value.toMutableMap()
        allMap[newGroup.id] = copied
        _allDeliveries.value = allMap
    }
    switchGroup(newGroup.id)
}

fun DeliveryViewModel.renameGroup(groupId: String, newName: String) {
    val oldName = _groups.value.find { it.id == groupId }?.name
    val updated = _groups.value.map {
        if (it.id == groupId) it.copy(name = newName) else it
    }
    _groups.value = updated
    saveGroups()
    if (oldName != null && oldName != newName) {
        deleteDownloadsFile(groupId, oldName)
        val list = _allDeliveries.value[groupId] ?: emptyList()
        if (list.isNotEmpty()) exportToDownloads(groupId, list)
    }
}

fun DeliveryViewModel.switchGroup(groupId: String) {
    if (_currentGroupId.value == groupId) return
    geocodingJobs[_currentGroupId.value]?.cancel()
    _geocodingProgress.value = null
    _visibleGroupIds.value = null
    _currentGroupId.value = groupId
    repo.saveCurrentGroupId(groupId)
    applyAreaHintForGroup(groupId)
    val list = _allDeliveries.value[groupId] ?: emptyList()
    _deliveries.value = list
    updateAllDeliveries(groupId, list)
}

fun DeliveryViewModel.currentGroup(): DeliveryGroup? =
    _groups.value.find { it.id == _currentGroupId.value }
