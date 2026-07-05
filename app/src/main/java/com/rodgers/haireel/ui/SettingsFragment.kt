package com.rodgers.haireel.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.haireel.databinding.FragmentSettingsBinding
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.BackupManager
import com.rodgers.haireel.util.themeColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val backupHandler by lazy { SettingsBackupHandler(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rowAppSettings.setOnClickListener { showAppSettingsDialog() }

        val ctx = requireContext()

        // テーマカラー
        val themeNames = mapOf(
            "blue"   to "ブルー（デフォルト）",
            "teal"   to "ティール",
            "green"  to "グリーン",
            "orange" to "オレンジ",
            "purple" to "パープル",
            "red"    to "レッド",
            "indigo" to "インディゴ",
            "brown"  to "アース",
        )
        binding.tvThemeName.text = themeNames[AppSettings.getThemeKey(ctx)] ?: "ブルー（デフォルト）"
        binding.rowTheme.setOnClickListener { showThemePickerDialog() }

        binding.tvApiKeyStatus.text = if (AppSettings.hasUserApiKey(ctx))
            "設定済み（自分のAPIキーを使用中）" else "未設定（住所変換・地図機能が使えません）"
        binding.rowApiKey.setOnClickListener {
            if (AppSettings.hasUserApiKey(ctx)) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("🔑 Google APIキー")
                    .setItems(arrayOf("動作確認する", "キーを変更する", "キャンセル")) { _, which ->
                        when (which) {
                            0 -> testApiKey(ctx)
                            1 -> showApiKeyWizard()
                        }
                    }
                    .show()
            } else {
                showApiKeyWizard()
            }
        }

        addBackgroundRow()

        binding.rowBackupCreate.setOnClickListener { createBackup() }
        binding.rowBackupRestore.setOnClickListener {
            (activity as? com.rodgers.haireel.MainActivity)?.launchRestoreFilePicker { uri ->
                if (uri == null) return@launchRestoreFilePicker
                handleRestoreUri(uri)
            }
        }
        // ライセンス状態を表示
        updateLicenseStatus()
        binding.rowLicense.setOnClickListener { showLicensePurchaseDialog() }
        // サブスク状態が変化したら表示を更新
        viewLifecycleOwner.lifecycleScope.launch {
            com.rodgers.haireel.util.BillingManager.subscriptionState.collect { updateLicenseStatus() }
        }
        binding.rowResetData.setOnClickListener { showResetDataDialog() }
        binding.rowContact.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:dev.claude.rodgers@gmail.com")
                putExtra(android.content.Intent.EXTRA_SUBJECT, "[HaiReel] お問い合わせ")
                putExtra(android.content.Intent.EXTRA_TEXT,
                    "アプリバージョン: ${requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName}\n\n")
            }
            try { startActivity(intent) }
            catch (_: Exception) { android.widget.Toast.makeText(requireContext(), "メールアプリが見つかりません", android.widget.Toast.LENGTH_SHORT).show() }
        }
        binding.rowTerms.setOnClickListener { showTermsDialog() }
        binding.rowSct.setOnClickListener { showSctDialog() }
        binding.rowHelp.setOnClickListener { showHelpDialog() }
        binding.rowAbout.setOnClickListener {
            (activity as? com.rodgers.haireel.MainActivity)?.showAboutDialog()
        }
        binding.rowPrivacy.setOnClickListener { showPrivacyPolicyDialog() }
        binding.rowExit.setOnClickListener { activity?.finishAffinity() }
    }

    private fun handleRestoreUri(uri: android.net.Uri) {
        if (!isAdded) return
        val ctx = requireContext()
        lifecycleScope.launch {
            val rawBytes = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.readBytes()
            }
            if (rawBytes == null) {
                Toast.makeText(ctx, "ファイルを開けませんでした", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (com.rodgers.haireel.util.BackupManager.isEncryptedData(rawBytes)) {
                val input = android.widget.EditText(ctx).apply {
                    hint = "バックアップパスワード"
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("パスワードを入力")
                    .setMessage("このバックアップはパスワードで暗号化されています。\n作成時に設定したパスワードを入力してください。")
                    .setView(input)
                    .setPositiveButton("復元") { _, _ ->
                        val pw = input.text.toString()
                        if (pw.isBlank()) {
                            Toast.makeText(ctx, "パスワードを入力してください", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        backupHandler.doRestore(uri, pw)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            } else {
                backupHandler.doRestore(uri)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun testApiKey(ctx: android.content.Context) {
        val loadingDlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("🔍 APIキーを確認中...")
            .setMessage("東京都千代田区への接続テストを実行しています。")
            .setCancelable(false)
            .create()
        loadingDlg.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.rodgers.haireel.util.GeocodingClient.geocode("東京都千代田区")
                }
            } catch (_: Exception) { null }

            loadingDlg.dismiss()

            if (!isAdded) return@launch

            if (result != null) {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("✅ APIキーは正常に動作しています")
                    .setMessage("住所検索・地図機能がご利用いただけます。\n\nテスト結果: ${result.formattedAddress}")
                    .setPositiveButton("OK", null)
                    .show()
                binding.tvApiKeyStatus.text = "設定済み・動作確認済み"
            } else {
                MaterialAlertDialogBuilder(ctx)
                    .setTitle("❌ APIキーが機能していません")
                    .setMessage(
                        "以下を確認してください。\n\n" +
                        "• APIキーが正しくコピーされているか\n" +
                        "• Geocoding APIが有効化されているか\n" +
                        "• Google Cloudの課金設定が完了しているか\n\n" +
                        "設定を修正してから再度お試しください。"
                    )
                    .setPositiveButton("再設定する") { _, _ -> showApiKeyWizard() }
                    .setNegativeButton("閉じる", null)
                    .show()
                binding.tvApiKeyStatus.text = "設定済み・動作確認NG（要確認）"
            }
        }
    }

    private fun showApiKeyWizard() {
        val ctx = requireContext()
        showApiKeyWizardDialog(
            ctx           = ctx,
            onLaunchIntent = { startActivity(it) },
            onTestApiKey   = { testApiKey(ctx) },
            onStatusChanged = { binding.tvApiKeyStatus.text = it }
        )
    }

    private fun showThemePickerDialog() {
        showThemePickerDialog(requireContext()) { requireActivity().recreate() }
    }

    private fun addBackgroundRow() {
        val ctx          = requireContext()
        val dp           = ctx.resources.displayMetrics.density
        val MATCH        = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP         = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVar   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val ripple       = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        binding.settingsRoot.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
        }, 2)

        val subTv = android.widget.TextView(ctx).apply {
            textSize = 14f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (2 * dp).toInt() }
            text = buildBackgroundSummary(ctx)
        }
        val col = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(0, WRAP, 1f)
                .also { it.marginStart = (14 * dp).toInt() }
            addView(android.widget.TextView(ctx).apply {
                text = "バックグラウンド処理"; textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(onSurface)
            })
            addView(subTv)
        }
        val row = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(ripple)
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            addView(android.widget.TextView(ctx).apply {
                text = "📲"; textSize = 28f; gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams((52 * dp).toInt(), WRAP)
            })
            addView(col)
            setOnClickListener {
                showBackgroundSettingsSheet(ctx) { subTv.text = buildBackgroundSummary(ctx) }
            }
        }
        binding.settingsRoot.addView(row, 3)
    }

    private fun createBackup() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = backupHandler.createBackup()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val mime = if (file.name.endsWith(".rbe")) "application/octet-stream" else "application/zip"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "バックアップを保存"))
            } catch (e: Exception) {
                ctx.showErrorDialog("バックアップエラー", e.localizedMessage ?: "バックアップの作成に失敗しました。\nストレージの空き容量を確認してください。")
            }
        }
    }

    private fun updateLicenseStatus() {
        if (!isAdded) return
        val ctx = requireContext()
        val s = com.rodgers.haireel.util.AppSettings
        binding.tvLicenseStatus.text = when {
            s.isSubscriptionActive(ctx) -> "プレミアム会員（Google Play）"
            s.isInTrial(ctx) -> {
                val days = s.trialDaysLeft(ctx)
                "無料体験中（残り${days}日）"
            }
            else -> "未登録 → タップしてプランを選ぶ"
        }
    }

    private fun showLicensePurchaseDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val act = activity ?: return
        val s = com.rodgers.haireel.util.AppSettings

        if (s.isSubscriptionActive(ctx)) {
            // 有効なサブスク → 管理画面へ
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("プレミアム会員")
                .setMessage("現在プレミアムプランをご利用中です。\n\nプランの変更・解約は Google Play → 定期購入 から行えます。")
                .setPositiveButton("Google Play で管理") { _, _ ->
                    try {
                        startActivity(android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                        ))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("閉じる", null)
                .show()
            return
        }

        // 未登録 / 試用中 → MainActivity の共通購入ダイアログを使用
        val bm = com.rodgers.haireel.util.BillingManager
        (act as? com.rodgers.haireel.MainActivity)
            ?.buildSubscriptionDialog(
                ctx,
                onYearly  = { bm.launchSubscription(act, bm.PRODUCT_YEARLY) },
                onMonthly = { bm.launchSubscription(act, bm.PRODUCT_MONTHLY) }
            )
            ?.show()
    }

    private fun showResetDataDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("⚠️ データをすべて初期化")
            .setMessage("日報・配達先・ルート・帳票パターン・署名を含むすべてのデータを削除します。\n\nこの操作は元に戻せません。\n\n先にバックアップを作成することをおすすめします。")
            .setPositiveButton("初期化する") { _, _ ->
                val appCtx2 = ctx.applicationContext
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val db = com.rodgers.haireel.db.AppDatabase.getInstance(appCtx2)
                        db.workRecordDao().deleteAll()
                        db.deliveryDao().deleteAll()
                        db.deliveryGroupDao().deleteAll()
                        db.tenkoDao().deleteAll()
                        db.geocodingCacheDao().deleteAll()
                        // SharedPreferences をクリア
                        appCtx2.getSharedPreferences(com.rodgers.haireel.util.AppSettings.PREFS, android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        appCtx2.getSharedPreferences("delivery_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        appCtx2.getSharedPreferences("report_patterns", android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        // 暗号化設定をクリア（APIキー・ライセンスキー・バックアップパスワード）
                        com.rodgers.haireel.util.AppSettings.clearSensitiveData(appCtx2)
                        // 署名を削除
                        for (type in listOf(com.rodgers.haireel.util.SignatureStorage.TYPE_DRIVER, com.rodgers.haireel.util.SignatureStorage.TYPE_CLIENT)) {
                            com.rodgers.haireel.util.SignatureStorage.fileFor(appCtx2, type).delete()
                        }
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(appCtx2, "初期化が完了しました。アプリを再起動します。", android.widget.Toast.LENGTH_LONG).show()
                        }
                        kotlinx.coroutines.delay(1500)
                        val intent2 = appCtx2.packageManager.getLaunchIntentForPackage(appCtx2.packageName)
                        if (intent2 != null) {
                            intent2.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            appCtx2.startActivity(intent2)
                        }
                        android.os.Process.killProcess(android.os.Process.myPid())
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            appCtx2.showErrorDialog("初期化エラー", e.localizedMessage ?: "データの初期化に失敗しました。")
                        }
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showTermsDialog()         { if (!isAdded) return; showTermsDialog(requireContext()) }
    private fun showSctDialog()           { if (!isAdded) return; showSctDialog(requireContext()) }
    private fun showHelpDialog()          { if (!isAdded) return; showHelpDialog(requireContext()) }
    private fun showPrivacyPolicyDialog() { if (!isAdded) return; showPrivacyPolicyDialog(requireContext()) }

    private fun showAppSettingsDialog() {
        if (!isAdded) return
        showAppSettingsDialog(requireContext())
    }
}
