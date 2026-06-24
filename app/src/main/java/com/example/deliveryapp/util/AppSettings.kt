package com.rodgers.routist.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
object AppSettings {
    const val PREFS = "kado_settings"
    const val ENCRYPTED_PREFS = "kado_secure"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun encryptedPrefs(ctx: Context): android.content.SharedPreferences = try {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, ENCRYPTED_PREFS, master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        ctx.getSharedPreferences(ENCRYPTED_PREFS + "_fallback", Context.MODE_PRIVATE)
    }

    fun getDriverName(ctx: Context): String = prefs(ctx).getString("driver_name", "") ?: ""
    fun setDriverName(ctx: Context, v: String) = prefs(ctx).edit().putString("driver_name", v).apply()

    fun getClientName(ctx: Context): String = prefs(ctx).getString("client_name", "") ?: ""
    fun setClientName(ctx: Context, v: String) = prefs(ctx).edit().putString("client_name", v).apply()

    fun getClosingDay(ctx: Context): Int = prefs(ctx).getInt("closing_day", 25)
    fun setClosingDay(ctx: Context, v: Int) = prefs(ctx).edit().putInt("closing_day", v).apply()

    fun getDeliveryLabel(ctx: Context): String = prefs(ctx).getString("delivery_label", "配達件数") ?: "配達件数"
    fun setDeliveryLabel(ctx: Context, v: String) = prefs(ctx).edit().putString("delivery_label", v).apply()

    fun getPackageLabel(ctx: Context): String = prefs(ctx).getString("package_label", "個数") ?: "個数"
    fun setPackageLabel(ctx: Context, v: String) = prefs(ctx).edit().putString("package_label", v).apply()

    fun isColAlc(ctx: Context): Boolean = prefs(ctx).getBoolean("col_alc", true)
    fun setColAlc(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("col_alc", v).apply()

    fun isColDelivery(ctx: Context): Boolean = prefs(ctx).getBoolean("col_delivery", true)
    fun setColDelivery(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("col_delivery", v).apply()

    fun isColPackage(ctx: Context): Boolean = prefs(ctx).getBoolean("col_package", true)
    fun setColPackage(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("col_package", v).apply()

    fun isColDistance(ctx: Context): Boolean = prefs(ctx).getBoolean("col_distance", true)
    fun setColDistance(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("col_distance", v).apply()

    fun isColArea(ctx: Context): Boolean = prefs(ctx).getBoolean("col_area", true)
    fun setColArea(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("col_area", v).apply()

    fun isColRemarks(ctx: Context): Boolean = prefs(ctx).getBoolean("col_remarks", true)
    fun setColRemarks(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("col_remarks", v).apply()

    // 報酬設定
    // 0 = 日当制, 1 = 件数単価制
    fun getPaymentType(ctx: Context): Int = prefs(ctx).getInt("payment_type", 0)
    fun setPaymentType(ctx: Context, v: Int) = prefs(ctx).edit().putInt("payment_type", v).apply()

    fun getUnitPrice(ctx: Context): Int = prefs(ctx).getInt("unit_price", 0)
    fun setUnitPrice(ctx: Context, v: Int) = prefs(ctx).edit().putInt("unit_price", v).apply()

    // 点呼設定
    fun getCheckerName(ctx: Context): String = prefs(ctx).getString("checker_name", "") ?: ""
    fun setCheckerName(ctx: Context, v: String) = prefs(ctx).edit().putString("checker_name", v).apply()

    // 事業者情報
    fun getCompanyName(ctx: Context): String = prefs(ctx).getString("company_name", "") ?: ""
    fun setCompanyName(ctx: Context, v: String) = prefs(ctx).edit().putString("company_name", v).apply()

    // 複数車両（最大3台）
    fun getVehicles(ctx: Context): List<String> {
        val p = prefs(ctx)
        val legacy = p.getString("vehicle_number", null)
        return listOf(
            p.getString("vehicle_number_1", legacy ?: "") ?: "",
            p.getString("vehicle_number_2", "") ?: "",
            p.getString("vehicle_number_3", "") ?: ""
        )
    }
    fun setVehicles(ctx: Context, list: List<String>) {
        val edit = prefs(ctx).edit()
        list.forEachIndexed { i, v -> edit.putString("vehicle_number_${i + 1}", v) }
        edit.apply()
    }
    fun getVehicleNumber(ctx: Context): String =
        getVehicles(ctx).firstOrNull { it.isNotBlank() }
            ?: prefs(ctx).getString("vehicle_number", "") ?: ""
    fun setVehicleNumber(ctx: Context, v: String) {
        val current = getVehicles(ctx)
        setVehicles(ctx, listOf(v, current[1], current[2]))
    }

    // 点呼リマインダー（乗務前）
    fun getReminderBeforeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("reminder_before_on", false)
    fun setReminderBeforeEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("reminder_before_on", v).apply()
    fun getReminderBeforeHour(ctx: Context): Int = prefs(ctx).getInt("reminder_before_h", 8)
    fun setReminderBeforeHour(ctx: Context, v: Int) = prefs(ctx).edit().putInt("reminder_before_h", v).apply()
    fun getReminderBeforeMinute(ctx: Context): Int = prefs(ctx).getInt("reminder_before_m", 0)
    fun setReminderBeforeMinute(ctx: Context, v: Int) = prefs(ctx).edit().putInt("reminder_before_m", v).apply()

    // 点呼リマインダー（乗務後）
    fun getReminderAfterEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("reminder_after_on", false)
    fun setReminderAfterEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("reminder_after_on", v).apply()
    fun getReminderAfterHour(ctx: Context): Int = prefs(ctx).getInt("reminder_after_h", 18)
    fun setReminderAfterHour(ctx: Context, v: Int) = prefs(ctx).edit().putInt("reminder_after_h", v).apply()
    fun getReminderAfterMinute(ctx: Context): Int = prefs(ctx).getInt("reminder_after_m", 0)
    fun setReminderAfterMinute(ctx: Context, v: Int) = prefs(ctx).edit().putInt("reminder_after_m", v).apply()

    // 休憩アラーム設定
    fun getBreakAlarmMinutes(ctx: Context): Int = prefs(ctx).getInt("break_alarm_minutes", 270)
    fun setBreakAlarmMinutes(ctx: Context, v: Int) = prefs(ctx).edit().putInt("break_alarm_minutes", v).apply()

    // 休憩アラームタイマー状態（state: "IDLE" | "DRIVING" | "ON_BREAK"）
    fun getDriveTimerState(ctx: Context): String = prefs(ctx).getString("drive_timer_state", "IDLE") ?: "IDLE"
    fun setDriveTimerState(ctx: Context, v: String) = prefs(ctx).edit().putString("drive_timer_state", v).apply()
    fun getDriveSegmentStartMs(ctx: Context): Long = prefs(ctx).getLong("drive_seg_start_ms", 0L)
    fun setDriveSegmentStartMs(ctx: Context, v: Long) = prefs(ctx).edit().putLong("drive_seg_start_ms", v).apply()
    fun getDriveAccumulatedMs(ctx: Context): Long = prefs(ctx).getLong("drive_accum_ms", 0L)
    fun setDriveAccumulatedMs(ctx: Context, v: Long) = prefs(ctx).edit().putLong("drive_accum_ms", v).apply()
    fun getBreakSegmentStartMs(ctx: Context): Long = prefs(ctx).getLong("break_seg_start_ms", 0L)
    fun setBreakSegmentStartMs(ctx: Context, v: Long) = prefs(ctx).edit().putLong("break_seg_start_ms", v).apply()
    fun getBreakAccumulatedMs(ctx: Context): Long = prefs(ctx).getLong("break_accum_ms", 0L)
    fun setBreakAccumulatedMs(ctx: Context, v: Long) = prefs(ctx).edit().putLong("break_accum_ms", v).apply()

    // 時間帯テンプレート（名前＋色）
    data class TimeSlotTemplate(val name: String, val colorHex: String)

    private val DEFAULT_TEMPLATES = listOf(
        TimeSlotTemplate("午前中",  "#1565C0"),
        TimeSlotTemplate("12-14時", "#00796B"),
        TimeSlotTemplate("14-16時", "#E65100"),
        TimeSlotTemplate("16-18時", "#6A1B9A"),
        TimeSlotTemplate("18-20時", "#B71C1C"),
        TimeSlotTemplate("19-21時", "#1A237E"),
    )

    fun getTimeSlotTemplatesWithColor(ctx: Context): List<TimeSlotTemplate> {
        // v2形式: "name\tcolorHex|name\tcolorHex|..."
        val rawV2 = prefs(ctx).getString("time_slot_templates_v2", null)
        if (rawV2 != null) {
            return rawV2.split("|").filter { it.isNotBlank() }.map {
                val tab = it.indexOf('\t')
                if (tab < 0) TimeSlotTemplate(it.trim(), "#888888")
                else TimeSlotTemplate(it.substring(0, tab).trim(), it.substring(tab + 1).trim())
            }
        }
        // v1形式からマイグレーション
        val rawV1 = prefs(ctx).getString("time_slot_templates", null)
        if (rawV1 != null) {
            val names = rawV1.split("|").filter { it.isNotBlank() }
            val colorMap = DEFAULT_TEMPLATES.associate { it.name to it.colorHex }
            return names.map { name ->
                TimeSlotTemplate(name, colorMap[name] ?: "#888888")
            }
        }
        return DEFAULT_TEMPLATES
    }

    fun saveTimeSlotTemplatesWithColor(ctx: Context, templates: List<TimeSlotTemplate>) =
        prefs(ctx).edit().putString("time_slot_templates_v2",
            templates.joinToString("|") { "${it.name}\t${it.colorHex}" }).apply()

    // 後方互換ラッパー
    fun getTimeSlotTemplates(ctx: Context): List<String> =
        getTimeSlotTemplatesWithColor(ctx).map { it.name }

    // 燃料費自動計算
    fun getFuelPricePerLiter(ctx: Context): Int = prefs(ctx).getInt("fuel_price_per_liter", 170)
    fun setFuelPricePerLiter(ctx: Context, v: Int) = prefs(ctx).edit().putInt("fuel_price_per_liter", v).apply()
    fun getFuelEfficiencyKmPerL(ctx: Context): Float {
        return try {
            prefs(ctx).getFloat("fuel_efficiency_km_per_l", 15f)
        } catch (_: ClassCastException) {
            // 旧バージョンで Integer として保存されていた場合の移行処理
            val v = prefs(ctx).getInt("fuel_efficiency_km_per_l", 15).toFloat()
            prefs(ctx).edit().putFloat("fuel_efficiency_km_per_l", v).apply()
            v
        }
    }
    fun setFuelEfficiencyKmPerL(ctx: Context, v: Float) = prefs(ctx).edit().putFloat("fuel_efficiency_km_per_l", v).apply()

    // インボイス登録
    fun isInvoiceRegistered(ctx: Context): Boolean = prefs(ctx).getBoolean("invoice_registered", false)
    fun setInvoiceRegistered(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("invoice_registered", v).apply()

    // 雇用形態 ("contractor" = 業務委託, "employee" = 正社員)
    fun getEmploymentType(ctx: Context): String = prefs(ctx).getString("employment_type", "contractor") ?: "contractor"
    fun setEmploymentType(ctx: Context, v: String) = prefs(ctx).edit().putString("employment_type", v).apply()

    // ── 案件（グループ）別 報酬・雇用形態設定（未設定時はグローバル値にフォールバック）
    fun getEmploymentType(ctx: Context, groupId: String): String {
        if (groupId.isBlank()) return getEmploymentType(ctx)
        val sp = prefs(ctx); val key = "employment_type_$groupId"
        return if (sp.contains(key)) sp.getString(key, "contractor") ?: "contractor" else getEmploymentType(ctx)
    }
    fun setEmploymentType(ctx: Context, groupId: String, v: String) =
        prefs(ctx).edit().putString("employment_type_$groupId", v).apply()

    fun getPaymentType(ctx: Context, groupId: String): Int {
        if (groupId.isBlank()) return getPaymentType(ctx)
        val sp = prefs(ctx); val key = "payment_type_$groupId"
        return if (sp.contains(key)) sp.getInt(key, 0) else getPaymentType(ctx)
    }
    fun setPaymentType(ctx: Context, groupId: String, v: Int) =
        prefs(ctx).edit().putInt("payment_type_$groupId", v).apply()

    fun getUnitPrice(ctx: Context, groupId: String): Int {
        if (groupId.isBlank()) return getUnitPrice(ctx)
        val sp = prefs(ctx); val key = "unit_price_$groupId"
        return if (sp.contains(key)) sp.getInt(key, 0) else getUnitPrice(ctx)
    }
    fun setUnitPrice(ctx: Context, groupId: String, v: Int) =
        prefs(ctx).edit().putInt("unit_price_$groupId", v).apply()

    fun hasGroupPaymentSettings(ctx: Context, groupId: String): Boolean =
        prefs(ctx).contains("payment_type_$groupId")

    // 点呼リスト右端表示 ("alcohol" | "time" | "none")
    fun getTenkoRightDisplay(ctx: Context): String = prefs(ctx).getString("tenko_right_display", "alcohol") ?: "alcohol"
    fun setTenkoRightDisplay(ctx: Context, v: String) = prefs(ctx).edit().putString("tenko_right_display", v).apply()

    // 削除取り消し時間（秒）
    fun getUndoSeconds(ctx: Context): Int = prefs(ctx).getInt("undo_seconds", 5)
    fun setUndoSeconds(ctx: Context, v: Int) = prefs(ctx).edit().putInt("undo_seconds", v).apply()

    // ダークモード (-1=システム, 1=ライト, 2=ダーク)
    fun getDarkMode(ctx: Context): Int = prefs(ctx).getInt("dark_mode", -1)
    fun setDarkMode(ctx: Context, v: Int) = prefs(ctx).edit().putInt("dark_mode", v).apply()

    // ユーザー独自のGoogle APIキー（EncryptedSharedPreferencesに保存）
    fun getUserApiKey(ctx: Context): String = encryptedPrefs(ctx).getString("user_api_key", "") ?: ""
    fun setUserApiKey(ctx: Context, key: String) { encryptedPrefs(ctx).edit().putString("user_api_key", key).commit() }
    fun hasUserApiKey(ctx: Context): Boolean = getUserApiKey(ctx).isNotBlank()



    // セキュリティ
    fun isAppLockEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("app_lock_enabled", false)
    fun setAppLockEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("app_lock_enabled", v).apply()

    fun getLockTimeoutMinutes(ctx: Context): Int = prefs(ctx).getInt("lock_timeout_min", 30)
    fun setLockTimeoutMinutes(ctx: Context, v: Int) = prefs(ctx).edit().putInt("lock_timeout_min", v).apply()

    fun getBackupPassword(ctx: Context): String {
        // 初回移行: 平文に旧値があれば暗号化版に移してから削除
        val plain = prefs(ctx)
        val legacy = plain.getString("backup_password", null)
        if (legacy != null) {
            encryptedPrefs(ctx).edit().putString("backup_password", legacy).apply()
            plain.edit().remove("backup_password").apply()
        }
        return encryptedPrefs(ctx).getString("backup_password", "") ?: ""
    }
    fun setBackupPassword(ctx: Context, v: String) = encryptedPrefs(ctx).edit().putString("backup_password", v).apply()

    // ── オンボーディング ──────────────────────────────────────────
    fun isOnboardingDone(ctx: Context): Boolean = prefs(ctx).getBoolean("onboarding_done", false)
    fun setOnboardingDone(ctx: Context) = prefs(ctx).edit().putBoolean("onboarding_done", true).apply()

    // ── 利用規約同意 ──────────────────────────────────────────────
    fun isTermsAgreed(ctx: Context): Boolean = prefs(ctx).getBoolean("terms_agreed", false)
    fun setTermsAgreed(ctx: Context) = prefs(ctx).edit().putBoolean("terms_agreed", true).apply()

    // ── ライセンス管理 ─────────────────────────────────────────

    private const val TRIAL_DAYS = 7L
    private const val KEY_INSTALL_DATE = "install_date"
    private const val KEY_LICENSE_KEY  = "license_key"
    private const val KEY_LICENSE_EXPIRY = "license_expiry"

    // 初回起動日を記録（一度セットしたら変わらない）
    fun ensureInstallDate(ctx: Context) {
        val p = prefs(ctx)
        if (!p.contains(KEY_INSTALL_DATE)) {
            p.edit().putLong(KEY_INSTALL_DATE, System.currentTimeMillis()).apply()
        }
    }

    fun getInstallDate(ctx: Context): Long = prefs(ctx).getLong(KEY_INSTALL_DATE, System.currentTimeMillis())

    // 試用期間内かどうか
    fun isInTrial(ctx: Context): Boolean {
        val elapsed = System.currentTimeMillis() - getInstallDate(ctx)
        return elapsed < TRIAL_DAYS * 24 * 60 * 60 * 1000L
    }

    // 試用残り日数
    fun trialDaysLeft(ctx: Context): Int {
        val elapsed = System.currentTimeMillis() - getInstallDate(ctx)
        val remaining = TRIAL_DAYS * 24 * 60 * 60 * 1000L - elapsed
        return maxOf(0, (remaining / (24 * 60 * 60 * 1000L)).toInt())
    }

    // ライセンスキー（EncryptedSharedPreferencesに保存）
    fun getLicenseKey(ctx: Context): String = try { encryptedPrefs(ctx).getString(KEY_LICENSE_KEY, "") ?: "" } catch (_: Exception) { "" }
    fun setLicenseKey(ctx: Context, key: String) { try { encryptedPrefs(ctx).edit().putString(KEY_LICENSE_KEY, key).apply() } catch (_: Exception) {} }

    // ライセンス有効期限（エポック秒）
    fun getLicenseExpiry(ctx: Context): Long = try { encryptedPrefs(ctx).getLong(KEY_LICENSE_EXPIRY, 0L) } catch (_: Exception) { 0L }
    fun setLicenseExpiry(ctx: Context, expiry: Long) { try { encryptedPrefs(ctx).edit().putLong(KEY_LICENSE_EXPIRY, expiry).apply() } catch (_: Exception) {} }

    // ライセンスが有効かどうか（期限内）
    fun isLicenseValid(ctx: Context): Boolean {
        val expiry = getLicenseExpiry(ctx)
        return expiry > 0L && System.currentTimeMillis() < expiry
    }

    // ─── Google Play IAP サブスクリプション ──────────────────────
    // Play Billing で購入確認済みのとき true を保存する
    // ネットワーク不要でオフラインでも機能するようにローカルにキャッシュする
    private const val KEY_SUBSCRIPTION_ACTIVE = "iap_subscription_active"
    private const val KEY_SUBSCRIPTION_CHECKED_AT = "iap_subscription_checked_at"

    fun isSubscriptionActive(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SUBSCRIPTION_ACTIVE, false)

    fun setSubscriptionActive(ctx: Context, active: Boolean) {
        prefs(ctx).edit()
            .putBoolean(KEY_SUBSCRIPTION_ACTIVE, active)
            .putLong(KEY_SUBSCRIPTION_CHECKED_AT, System.currentTimeMillis())
            .apply()
    }

    // サブスク確認が古すぎる場合は再確認を促す（7日以内なら信頼）
    fun isSubscriptionCheckStale(ctx: Context): Boolean {
        val checkedAt = prefs(ctx).getLong(KEY_SUBSCRIPTION_CHECKED_AT, 0L)
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - checkedAt > sevenDays
    }

    // アプリを使えるかどうか（試用中 or ライセンス有効 or IAPサブスク有効）
    fun canUseApp(ctx: Context): Boolean =
        isInTrial(ctx) || isLicenseValid(ctx) || isSubscriptionActive(ctx)

    // 暗号化設定の個別削除（clear()はKeyStore破壊の既知バグがあるため使わない）
    fun clearSensitiveData(ctx: Context) {
        try {
            encryptedPrefs(ctx).edit()
                .remove("user_api_key")
                .remove("backup_password")
                .remove(KEY_LICENSE_KEY)
                .remove(KEY_LICENSE_EXPIRY)
                .apply()
        } catch (_: Exception) {}
        // フォールバック側も念のためクリア
        try {
            ctx.getSharedPreferences(ENCRYPTED_PREFS + "_fallback", Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) {}
    }
}
