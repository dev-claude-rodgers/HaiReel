package com.rodgers.haireel.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.rodgers.haireel.model.Delivery
import com.rodgers.haireel.model.DeliveryGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DTAG = "DeliveryDownloads"
private fun String.toSafeFileName() = replace(Regex("[/\\\\:*?\"<>|]"), "_")

// Downloads 操作: DeliveryViewModel の extension functions
// ─────────────────────────────────────────────────

internal fun DeliveryViewModel.createMissingDownloadFiles(activeGroups: List<DeliveryGroup>) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                activeGroups.forEach { group ->
                    val list = repo.loadDeliveries(group.id)
                    if (list.isEmpty()) return@forEach
                    val safeName = group.name.toSafeFileName()
                    val fileName = "HaiReel_$safeName.txt"
                    val exists = resolver.query(
                        baseUri, arrayOf(MediaStore.Downloads._ID),
                        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                        arrayOf(fileName), null
                    )?.use { it.count > 0 } ?: false
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
                            repo.saveDownloadFileUri(group.id, uri.toString())
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.w(DTAG, "Downloadsファイル作成失敗", e) }
    }
}

internal fun DeliveryViewModel.cleanupOrphanedDownloadFiles(activeGroups: List<DeliveryGroup>) {
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
                        if (!fileName.startsWith("HaiReel_") || !fileName.endsWith(".txt")) continue
                        val hasGroup = activeGroups.any { g ->
                            val safe = g.name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                            "HaiReel_$safe.txt" == fileName
                        }
                        if (!hasGroup) {
                            resolver.delete(ContentUris.withAppendedId(baseUri, fileId), null, null)
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.w(DTAG, "不要Downloadsファイル削除失敗", e) }
    }
}

internal fun DeliveryViewModel.deleteDownloadsFile(groupId: String, groupName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val savedUri = repo.getDownloadFileUri(groupId)
                if (savedUri != null) {
                    try { resolver.delete(android.net.Uri.parse(savedUri), null, null) } catch (_: Exception) {}
                    repo.clearDownloadFileUri(groupId)
                }
                val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val safeName = groupName.toSafeFileName()
                val fileName = "HaiReel_$safeName.txt"
                resolver.query(
                    baseUri, arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName), null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        resolver.delete(ContentUris.withAppendedId(baseUri, cursor.getLong(0)), null, null)
                    }
                }
            }
        } catch (e: Exception) { Log.w(DTAG, "Downloadsファイル削除失敗", e) }
    }
}

internal fun DeliveryViewModel.importFileMediaStoreId(groupId: String): Long? {
    val uriStr = repo.getFileUri(groupId) ?: return null
    val uri = android.net.Uri.parse(uriStr)
    return when (uri.authority) {
        "com.android.providers.media.documents" ->
            DocumentsContract.getDocumentId(uri).split(":").lastOrNull()?.toLongOrNull()
        else -> try { ContentUris.parseId(uri) } catch (_: Exception) { null }
    }
}

internal fun DeliveryViewModel.exportToDownloads(groupId: String, list: List<Delivery>) {
    if (list.isEmpty()) return
    val group = _groups.value.find { it.id == groupId } ?: return
    val content = list.mapIndexed { index, d ->
        val label = if (!d.name.isNullOrBlank()) d.name else d.address
        "${index + 1}. $label"
    }.joinToString("\n")
    val safeName = group.name.toSafeFileName()
    val fileName = "HaiReel_$safeName.txt"
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val baseUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val existingCursor = resolver.query(baseUri, projection, selection, arrayOf(fileName), null)
                val fileUri = if (existingCursor != null && existingCursor.moveToFirst()) {
                    val id = existingCursor.getLong(0)
                    existingCursor.close()
                    val sameAsImport = importFileMediaStoreId(groupId) == id
                    val uri = ContentUris.withAppendedId(baseUri, id)
                    if (sameAsImport) {
                        val existing = try {
                            resolver.openInputStream(uri)?.bufferedReader()?.readText()
                        } catch (_: Exception) { null }
                        if (existing != null && existing.trim() != content.trim()) {
                            withContext(Dispatchers.Main) {
                                _pendingOverwrite.value = DeliveryViewModel.OverwriteConfirmation(uri, content)
                            }
                        }
                        return@launch
                    }
                    uri
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
                    repo.saveDownloadFileUri(groupId, uri.toString())
                }
            } else {
                val context2 = getApplication<Application>()
                java.io.File(context2.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                    .writeText(content)
            }
        } catch (e: Exception) {
            Log.w(DTAG, "エクスポート失敗", e)
            _errorMessage.value = "エクスポートに失敗しました"
        }
    }
}
