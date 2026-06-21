package com.rodgers.routist.util

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import android.net.Uri
import com.rodgers.routist.db.AppDatabase
import com.rodgers.routist.model.WorkRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {

    private const val FORMAT_VERSION = "1"

    // 暗号化ヘッダー (4 bytes)
    private val ENC_HEADER = byteArrayOf(0x52, 0x53, 0x54, 0x42) // "RSTB"
    private const val PBKDF2_ITER = 100_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    internal fun isEncryptedData(data: ByteArray): Boolean =
        data.size > ENC_HEADER.size && data.copyOfRange(0, ENC_HEADER.size).contentEquals(ENC_HEADER)

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITER, 256)
        val raw  = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun encryptBytes(data: ByteArray, password: String): ByteArray {
        val salt   = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv     = ByteArray(IV_LEN).also   { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(data)
        return ENC_HEADER + salt + iv + enc
    }

    private fun decryptBytes(data: ByteArray, password: String): ByteArray {
        val off    = ENC_HEADER.size
        val salt   = data.copyOfRange(off, off + SALT_LEN)
        val iv     = data.copyOfRange(off + SALT_LEN, off + SALT_LEN + IV_LEN)
        val enc    = data.copyOfRange(off + SALT_LEN + IV_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }

    suspend fun createBackup(context: Context): File {
        val db         = AppDatabase.getInstance(context)
        val records    = db.workRecordDao().getAll()
        val tenkoList  = db.tenkoDao().getAll()
        val patterns   = PatternStorage.getAll(context)
        val activeId   = PatternStorage.getActiveId(context)
        val groups     = db.deliveryGroupDao().getAll()
        val deliveries = db.deliveryDao().getAll()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.JAPANESE).format(Date())
        val zipFile = File(context.cacheDir, "RouteJin_backup_$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.utf8Entry("version.txt", FORMAT_VERSION)
            zos.utf8Entry("records.json",    recordsToJson(records).toString())
            zos.utf8Entry("tenko.json",      tenkoToJson(tenkoList).toString())
            zos.utf8Entry("patterns.json",   patternsToJson(patterns, activeId).toString())
            zos.utf8Entry("groups.json",     groupsToJson(groups).toString())
            zos.utf8Entry("deliveries.json", deliveriesToJson(deliveries).toString())
            zos.utf8Entry("settings.json",   settingsToJson(context).toString())

            for (type in listOf(SignatureStorage.TYPE_DRIVER, SignatureStorage.TYPE_CLIENT)) {
                val sig = SignatureStorage.fileFor(context, type)
                if (sig.exists()) {
                    zos.putNextEntry(ZipEntry("sig_$type.png"))
                    sig.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }

        val pw = AppSettings.getBackupPassword(context)
        if (pw.isBlank()) return zipFile

        val encFile = File(context.cacheDir, "RouteJin_backup_${timestamp}.rbe")
        encFile.writeBytes(encryptBytes(zipFile.readBytes(), pw))
        zipFile.delete()
        return encFile
    }

    suspend fun restoreBackup(context: Context, uri: Uri, password: String? = null) {
        val rawBytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: error("ファイルを開けませんでした")
        val zipBytes = if (isEncryptedData(rawBytes)) {
            val pw = password?.takeIf { it.isNotBlank() }
                ?: AppSettings.getBackupPassword(context).takeIf { it.isNotBlank() }
                ?: error("このバックアップはパスワードで暗号化されています。パスワードを入力してください。")
            try { decryptBytes(rawBytes, pw) }
            catch (e: Exception) { error("パスワードが違います、またはファイルが破損しています。") }
        } else {
            rawBytes
        }
        restoreFromStream(context, zipBytes.inputStream())
    }

    private suspend fun restoreFromStream(context: Context, input: InputStream) {
        val db  = AppDatabase.getInstance(context)
        val dao = db.workRecordDao()

        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val bytes = zis.readBytes()
                when (entry.name) {
                    "records.json" -> {
                        val arr = JSONArray(bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
                        dao.deleteAll()
                        for (i in 0 until arr.length()) {
                            try { dao.upsert(recordFromJson(arr.getJSONObject(i))) } catch (e: Exception) { Log.w("BackupManager", "記録の復元失敗: item $i", e) }
                        }
                    }
                    "settings.json" -> {
                        restoreSettings(context, JSONObject(bytes.toString(Charsets.UTF_8).removePrefix("﻿")))
                    }
                    "tenko.json" -> {
                        val arr = JSONArray(bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
                        db.tenkoDao().deleteAll()
                        for (i in 0 until arr.length()) {
                            try {
                                db.tenkoDao().insert(tenkoFromJson(arr.getJSONObject(i)))
                            } catch (e: Exception) { Log.w("BackupManager", "点呼記録の復元失敗: item $i", e) }
                        }
                    }
                    "patterns.json" -> {
                        restorePatterns(context, JSONObject(bytes.toString(Charsets.UTF_8).removePrefix("﻿")))
                    }
                    "groups.json" -> {
                        val arr = JSONArray(bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
                        db.deliveryGroupDao().deleteAll()
                        for (i in 0 until arr.length()) {
                            try {
                                val o = arr.getJSONObject(i)
                                db.deliveryGroupDao().upsert(com.rodgers.routist.db.DeliveryGroupEntity(
                                    id        = o.getString("id"),
                                    name      = o.optString("name", "ルート"),
                                    colorHex  = o.optString("colorHex", "#F44336"),
                                    patternId = o.optInt("patternId", -1),
                                    sortOrder = o.optInt("sortOrder", i)
                                ))
                            } catch (e: Exception) { Log.w("BackupManager", "ルートの復元失敗: item $i", e) }
                        }
                    }
                    "deliveries.json" -> {
                        val arr = JSONArray(bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
                        db.deliveryDao().deleteAll()
                        for (i in 0 until arr.length()) {
                            try {
                                val o = arr.getJSONObject(i)
                                db.deliveryDao().upsert(com.rodgers.routist.db.DeliveryEntity(
                                    id              = o.getString("id"),
                                    groupId         = o.getString("groupId"),
                                    order           = o.optInt("order", i),
                                    name            = o.optString("name").ifBlank { null },
                                    address         = o.optString("address", ""),
                                    geocodedAddress = o.optString("geocodedAddress").ifBlank { null },
                                    note            = o.optString("note").ifBlank { null },
                                    photoUri        = o.optString("photoUri").ifBlank { null },
                                    photoUrisJson   = o.optString("photoUrisJson").ifBlank { null },
                                    roomsJson       = o.optString("roomsJson").ifBlank { null },
                                    timeSlot        = o.optString("timeSlot").ifBlank { null },
                                    packageCount    = o.optInt("packageCount", 0),
                                    lat             = o.optDouble("lat", 0.0),
                                    lng             = o.optDouble("lng", 0.0),
                                    isCompleted     = o.optBoolean("isCompleted", false),
                                    isGeocoded      = o.optBoolean("isGeocoded", false)
                                ))
                            } catch (e: Exception) { Log.w("BackupManager", "配達先の復元失敗: item $i", e) }
                        }
                    }
                    "sig_driver.png" -> {
                        try {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                                SignatureStorage.save(context, SignatureStorage.TYPE_DRIVER, it)
                            }
                        } catch (_: OutOfMemoryError) {}
                    }
                    "sig_client.png" -> {
                        try {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let {
                                SignatureStorage.save(context, SignatureStorage.TYPE_CLIENT, it)
                            }
                        } catch (_: OutOfMemoryError) {}
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun ZipOutputStream.utf8Entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun recordsToJson(records: List<WorkRecord>): JSONArray {
        val arr = JSONArray()
        for (r in records) {
            arr.put(JSONObject().apply {
                put("id",            r.id)
                put("date",          r.date)
                put("startTime",     r.startTime)
                put("endTime",       r.endTime)
                put("deliveryCount", r.deliveryCount)
                put("packageCount",  r.packageCount)
                put("distanceKm",    r.distanceKm.toDouble())
                put("area",          r.area)
                put("alcCheck",      r.alcCheck)
                put("remarks",       r.remarks)
                put("startMeter",    r.startMeter)
                put("endMeter",      r.endMeter)
                put("endDateOffset", r.endDateOffset)
            })
        }
        return arr
    }

    private fun recordFromJson(j: JSONObject) = WorkRecord(
        id            = j.getLong("id"),
        date          = j.getString("date"),
        startTime     = j.optString("startTime", ""),
        endTime       = j.optString("endTime", ""),
        deliveryCount = j.optInt("deliveryCount", 0),
        packageCount  = j.optInt("packageCount", 0),
        distanceKm    = j.optDouble("distanceKm", 0.0).toFloat(),
        area          = j.optString("area", ""),
        alcCheck      = j.optString("alcCheck", ""),
        remarks       = j.optString("remarks", ""),
        startMeter    = j.optInt("startMeter", 0),
        endMeter      = j.optInt("endMeter", 0),
        endDateOffset = j.optInt("endDateOffset", 0)
    )

    private fun patternsToJson(patterns: List<com.rodgers.routist.model.ReportPattern>, activeId: Int): JSONObject {
        val arr = JSONArray()
        for (p in patterns) {
            arr.put(JSONObject().apply {
                put("id",            p.id)
                put("title",         p.title)
                put("clientName",    p.clientName)
                put("driverName",    p.driverName)
                put("closingDay",    p.closingDay)
                put("deliveryLabel", p.deliveryLabel)
                put("packageLabel",  p.packageLabel)
                put("showStartEndTime", p.showStartEndTime)
                put("showWorkingHours", p.showWorkingHours)
                put("showDelivery",  p.showDelivery)
                put("showPackage",   p.showPackage)
                put("showDistance",  p.showDistance)
                put("showFuel",      p.showFuel)
                put("showArea",      p.showArea)
                put("showRemarks",   p.showRemarks)
            })
        }
        return JSONObject().apply {
            put("activeId",  activeId)
            put("patterns",  arr)
        }
    }

    private fun settingsToJson(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE)
        val json = JSONObject()
        prefs.all.forEach { (k, v) ->
            when (v) {
                is String  -> json.put(k, v)
                is Int     -> json.put(k, v)
                is Boolean -> json.put(k, v)
                is Float   -> json.put(k, v)
                is Long    -> json.put(k, v)
                is Set<*>  -> json.put(k, JSONArray(v.toList()))
            }
        }
        // APIキーも保存（空でなければ）
        val apiKey = try { AppSettings.getUserApiKey(context) } catch (_: Exception) { "" }
        if (apiKey.isNotBlank()) json.put("_user_api_key", apiKey)
        return json
    }

    private fun restoreSettings(context: Context, json: JSONObject) {
        val prefs = context.getSharedPreferences(AppSettings.PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        json.keys().forEach { key ->
            if (key == "_user_api_key") return@forEach
            when (val v = json.get(key)) {
                is String  -> editor.putString(key, v)
                is Int     -> editor.putInt(key, v)
                is Boolean -> editor.putBoolean(key, v)
                is Double  -> editor.putFloat(key, v.toFloat())
                is Long    -> editor.putLong(key, v)
                is JSONArray -> {
                    val set = (0 until v.length()).map { v.getString(it) }.toSet()
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()
        // APIキーを復元（暗号化保存）
        val apiKey = json.optString("_user_api_key").ifBlank { null }
        if (apiKey != null) {
            try { AppSettings.setUserApiKey(context, apiKey) } catch (_: Exception) { }
        }
    }

    private fun tenkoToJson(list: List<com.rodgers.routist.model.TenkoRecord>): JSONArray {
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().apply {
                put("id",                r.id)
                put("date",              r.date)
                put("assignmentId",      r.assignmentId)
                put("beforeMethod",      r.beforeMethod ?: "")
                put("beforeTime",        r.beforeTime ?: "")
                put("beforeHealth",      r.beforeHealth)
                put("beforeFatigue",     r.beforeFatigue)
                put("beforeAlcohol",     r.beforeAlcohol)
                put("beforeInspection",  r.beforeInspection)
                put("beforeInstruction", r.beforeInstruction ?: "")
                put("beforeChecker",     r.beforeChecker ?: "")
                put("afterMethod",       r.afterMethod ?: "")
                put("afterTime",         r.afterTime ?: "")
                put("afterHealth",       r.afterHealth)
                put("afterFatigue",      r.afterFatigue)
                put("afterAlcohol",      r.afterAlcohol)
                put("afterAccident",     r.afterAccident)
                put("afterVehicle",      r.afterVehicle)
                put("afterInstruction",  r.afterInstruction ?: "")
                put("afterChecker",      r.afterChecker ?: "")
                put("note",              r.note ?: "")
                put("vehicleNumber",     r.vehicleNumber ?: "")
            })
        }
        return arr
    }

    private fun tenkoFromJson(j: JSONObject) = com.rodgers.routist.model.TenkoRecord(
        date              = j.getString("date"),
        assignmentId      = j.optString("assignmentId", ""),
        beforeMethod      = j.optString("beforeMethod").ifBlank { null },
        beforeTime        = j.optString("beforeTime").ifBlank { null },
        beforeHealth      = if (j.isNull("beforeHealth")) null else j.optBoolean("beforeHealth"),
        beforeFatigue     = if (j.isNull("beforeFatigue")) null else j.optBoolean("beforeFatigue"),
        beforeAlcohol     = if (j.isNull("beforeAlcohol")) null else j.optDouble("beforeAlcohol"),
        beforeInspection  = if (j.isNull("beforeInspection")) null else j.optBoolean("beforeInspection"),
        beforeInstruction = j.optString("beforeInstruction").ifBlank { null },
        beforeChecker     = j.optString("beforeChecker").ifBlank { null },
        afterMethod       = j.optString("afterMethod").ifBlank { null },
        afterTime         = j.optString("afterTime").ifBlank { null },
        afterHealth       = if (j.isNull("afterHealth")) null else j.optBoolean("afterHealth"),
        afterFatigue      = if (j.isNull("afterFatigue")) null else j.optBoolean("afterFatigue"),
        afterAlcohol      = if (j.isNull("afterAlcohol")) null else j.optDouble("afterAlcohol"),
        afterAccident     = if (j.isNull("afterAccident")) null else j.optBoolean("afterAccident"),
        afterVehicle      = if (j.isNull("afterVehicle")) null else j.optBoolean("afterVehicle"),
        afterInstruction  = j.optString("afterInstruction").ifBlank { null },
        afterChecker      = j.optString("afterChecker").ifBlank { null },
        note              = j.optString("note").ifBlank { null },
        vehicleNumber     = j.optString("vehicleNumber").ifBlank { null }
    )

    private fun groupsToJson(groups: List<com.rodgers.routist.db.DeliveryGroupEntity>): JSONArray {
        val arr = JSONArray()
        for (g in groups) {
            arr.put(JSONObject().apply {
                put("id",        g.id)
                put("name",      g.name)
                put("colorHex",  g.colorHex)
                put("patternId", g.patternId)
                put("sortOrder", g.sortOrder)
            })
        }
        return arr
    }

    private fun deliveriesToJson(deliveries: List<com.rodgers.routist.db.DeliveryEntity>): JSONArray {
        val arr = JSONArray()
        for (d in deliveries) {
            arr.put(JSONObject().apply {
                put("id",              d.id)
                put("groupId",         d.groupId)
                put("order",           d.order)
                put("name",            d.name ?: "")
                put("address",         d.address)
                put("geocodedAddress", d.geocodedAddress ?: "")
                put("note",            d.note ?: "")
                put("photoUri",        d.photoUri ?: "")
                put("photoUrisJson",   d.photoUrisJson ?: "")
                put("roomsJson",       d.roomsJson ?: "")
                put("timeSlot",        d.timeSlot ?: "")
                put("packageCount",    d.packageCount)
                put("lat",             d.lat)
                put("lng",             d.lng)
                put("isCompleted",     d.isCompleted)
                put("isGeocoded",      d.isGeocoded)
            })
        }
        return arr
    }

    private fun restorePatterns(context: Context, json: JSONObject) {
        val activeId = json.optInt("activeId", -1)
        val arr      = json.optJSONArray("patterns") ?: JSONArray()

        for (id in PatternStorage.getIds(context).toList()) PatternStorage.delete(context, id)

        var maxId = -1
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            val pattern = com.rodgers.routist.model.ReportPattern(
                id            = j.getInt("id"),
                title         = j.optString("title", "稼働報告書"),
                clientName    = j.optString("clientName", ""),
                driverName    = j.optString("driverName", ""),
                closingDay    = j.optInt("closingDay", 25),
                deliveryLabel = j.optString("deliveryLabel", "配達件数"),
                packageLabel  = j.optString("packageLabel", "個数"),
                showStartEndTime = j.optBoolean("showStartEndTime", true),
                showWorkingHours = j.optBoolean("showWorkingHours", true),
                showDelivery  = j.optBoolean("showDelivery", true),
                showPackage   = j.optBoolean("showPackage", true),
                showDistance  = j.optBoolean("showDistance", true),
                showFuel      = j.optBoolean("showFuel", true),
                showArea      = j.optBoolean("showArea", true),
                showRemarks   = j.optBoolean("showRemarks", true)
            )
            PatternStorage.save(context, pattern)
            if (pattern.id > maxId) maxId = pattern.id
        }

        PatternStorage.setActiveId(context, activeId)
        PatternStorage.setNextId(context, maxId + 1)
    }
}
