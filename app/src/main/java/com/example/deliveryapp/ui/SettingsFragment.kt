package com.rodgers.routist.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.R
import com.rodgers.routist.databinding.FragmentSettingsBinding
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.BackupManager
import com.rodgers.routist.util.themeColor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!


    private fun doRestore(ctx: android.content.Context, uri: android.net.Uri, password: String? = null) {
        val appCtx = ctx.applicationContext  // Fragmentのライフサイクルに依存しないApplicationContextを使用
        Toast.makeText(appCtx, "復元中...", Toast.LENGTH_SHORT).show()
        // lifecycleScope ではなく GlobalScope を使い、Fragmentデタッチでキャンセルされないようにする
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                BackupManager.restoreBackup(appCtx, uri, password)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "復元しました。アプリを再起動します。", Toast.LENGTH_LONG).show()
                }
                kotlinx.coroutines.delay(1500)
                val intent = appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    appCtx.startActivity(intent)
                } else {
                    // フォールバック: インテントが取れなくてもプロセスは終了（ランチャーから再起動）
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appCtx, "アプリをランチャーから開いてください", Toast.LENGTH_LONG).show()
                    }
                    kotlinx.coroutines.delay(2000)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "復元に失敗しました: ${e.localizedMessage ?: "不明なエラー"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
        binding.tvApiKeyStatus.text = if (AppSettings.hasUserApiKey(ctx))
            "設定済み（自分のAPIキーを使用中）" else "未設定（ビルド埋め込みキーを使用中）"
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
            (activity as? com.rodgers.routist.MainActivity)?.launchRestoreFilePicker { uri ->
                if (uri == null) return@launchRestoreFilePicker
                handleRestoreUri(uri)
            }
        }
        // ライセンス状態を表示
        updateLicenseStatus()
        binding.rowLicense.setOnClickListener { showLicensePurchaseDialog() }
        // サブスク状態が変化したら表示を更新
        viewLifecycleOwner.lifecycleScope.launch {
            com.rodgers.routist.util.BillingManager.subscriptionState.collect { updateLicenseStatus() }
        }
        binding.rowResetData.setOnClickListener { showResetDataDialog() }
        binding.rowContact.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:proxyroutine777@gmail.com")
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
            (activity as? com.rodgers.routist.MainActivity)?.showAboutDialog()
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
            if (com.rodgers.routist.util.BackupManager.isEncryptedData(rawBytes)) {
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
                        doRestore(ctx, uri, pw)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            } else {
                doRestore(ctx, uri)
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
                    com.rodgers.routist.util.GeocodingClient.geocode("東京都千代田区")
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
        val dp   = ctx.resources.displayMetrics.density
        val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        val primary      = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
        val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24*dp).toInt(), (16*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
        }

        root.addView(android.widget.TextView(ctx).apply {
            text = "住所検索・地図機能を使うには「APIキー」が必要です。\n取得は無料で、個人利用では料金はほぼかかりません。"
            textSize = 14f; setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (12*dp).toInt() }
        })

        root.addView(android.widget.TextView(ctx).apply {
            text = "① 下のボタンを押してブラウザを開く"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (4*dp).toInt() }
        })
        root.addView(android.widget.TextView(ctx).apply {
            text = "Googleアカウントでログインし、画面の指示に従ってAPIキーを作成してください。\n（初回のみ：プロジェクト作成 → API有効化 → キー発行の順に進みます）"
            textSize = 12f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })

        root.addView(android.widget.Button(ctx).apply {
            text = "Google CloudでAPIキーを取得する →"
            isAllCaps = false; textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(primary); cornerRadius = 8*dp
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (20*dp).toInt() }
            setOnClickListener {
                try {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://console.cloud.google.com/apis/credentials")))
                } catch (_: Exception) {
                    Toast.makeText(ctx, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(android.widget.TextView(ctx).apply {
            text = "② 表示されたAPIキー（「AIza...」で始まる文字列）をコピーして下に貼り付ける"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })

        val inputField = android.widget.EditText(ctx).apply {
            hint = "AIza... を貼り付け"
            setText(AppSettings.getUserApiKey(ctx))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(inputField)

        val dlg = MaterialAlertDialogBuilder(ctx)
            .setTitle("🔑 Google APIキー設定")
            .setView(android.widget.ScrollView(ctx).apply { addView(root) })
            .setPositiveButton("✅ 保存する", null)
            .setNegativeButton("キャンセル", null)
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val key = inputField.text.toString().trim()
            AppSettings.setUserApiKey(ctx, key)
            com.rodgers.routist.util.GeocodingClient.configure(
                key.ifBlank { com.rodgers.routist.BuildConfig.GEOCODING_API_KEY }
            )
            binding.tvApiKeyStatus.text = if (key.isNotBlank())
                "設定済み（自分のAPIキーを使用中）"
            else "未設定（ビルド埋め込みキーを使用中）"
            dlg.dismiss()
            if (key.isNotBlank()) testApiKey(ctx)
            else Toast.makeText(ctx, "APIキーを削除しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addBackgroundRow() {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVar   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        val divider = android.view.View(ctx).apply {
            setBackgroundColor(outlineVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, (1*dp).toInt())
                .also { it.setMargins((84*dp).toInt(), (4*dp).toInt(), 0, (4*dp).toInt()) }
        }
        binding.settingsRoot.addView(divider, 2)

        val row = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(ripple)
            setPadding((20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt(), (20*dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
        }
        row.addView(android.widget.TextView(ctx).apply {
            text = "📲"; textSize = 28f; gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams((52*dp).toInt(), WRAP)
        })
        val col = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(0, WRAP, 1f)
                .also { it.marginStart = (14*dp).toInt() }
        }
        val titleTv = android.widget.TextView(ctx)
        titleTv.text = "バックグラウンド処理"
        titleTv.textSize = 17f
        titleTv.typeface = android.graphics.Typeface.DEFAULT_BOLD
        titleTv.setTextColor(onSurface)

        val subTv = android.widget.TextView(ctx)
        subTv.textSize = 14f
        subTv.setTextColor(onSurfaceVar)
        subTv.layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (2*dp).toInt() }
        subTv.text = buildBackgroundSummary(ctx)

        col.addView(titleTv)
        col.addView(subTv)
        row.addView(col)
        row.setOnClickListener { showBackgroundSettingsSheet { subTv.text = buildBackgroundSummary(ctx) } }
        binding.settingsRoot.addView(row, 3)
    }

    private fun buildBackgroundSummary(ctx: android.content.Context): String {
        val items = mutableListOf<String>()
        if (AppSettings.getReminderBeforeEnabled(ctx)) items.add("乗務前通知 ON")
        if (AppSettings.getReminderAfterEnabled(ctx)) items.add("乗務後通知 ON")
        return if (items.isEmpty()) "すべてOFF" else items.joinToString("・")
    }

    private fun showBackgroundSettingsSheet(onDismiss: () -> Unit) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val surfaceBg    = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outlineVar   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(surfaceBg)
            setPadding((0*dp).toInt(), (8*dp).toInt(), (0*dp).toInt(), (32*dp).toInt())
        }

        // タイトル
        root.addView(android.widget.TextView(ctx).apply {
            text = "バックグラウンド処理"
            textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            setPadding((20*dp).toInt(), (16*dp).toInt(), (20*dp).toInt(), (12*dp).toInt())
        })
        root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, (1*dp).toInt())
        })

        fun toggleRow(emoji: String, title: String, isOn: Boolean,
                      onToggle: (Boolean, android.widget.TextView) -> Unit) {
            val ripple = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
            }.resourceId
            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(ripple)
                setPadding((20*dp).toInt(), (18*dp).toInt(), (20*dp).toInt(), (18*dp).toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            }
            row.addView(android.widget.TextView(ctx).apply {
                text = emoji; textSize = 26f; gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams((52*dp).toInt(), WRAP)
            })
            val col = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(0, WRAP, 1f)
                    .also { it.marginStart = (14*dp).toInt() }
            }
            val titleV = android.widget.TextView(ctx)
            titleV.text = title; titleV.textSize = 16f
            titleV.typeface = android.graphics.Typeface.DEFAULT_BOLD
            titleV.setTextColor(onSurface)

            val stateV = android.widget.TextView(ctx)
            stateV.textSize = 13f; stateV.setTextColor(onSurfaceVar)
            stateV.layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (2*dp).toInt() }
            stateV.text = if (isOn) "ON" else "OFF"

            col.addView(titleV); col.addView(stateV)
            row.addView(col)
            row.addView(android.widget.TextView(ctx).apply {
                text = if (isOn) "✅" else "⬜"; textSize = 22f
                gravity = android.view.Gravity.CENTER
                layoutParams = android.widget.LinearLayout.LayoutParams((44*dp).toInt(), WRAP)
                tag = if (isOn) "on" else "off"
            })
            val indicator = row.getChildAt(2) as android.widget.TextView
            row.setOnClickListener {
                val nowOn = indicator.tag == "on"
                onToggle(!nowOn, stateV)
                indicator.text = if (!nowOn) "✅" else "⬜"
                indicator.tag = if (!nowOn) "on" else "off"
                stateV.text = if (!nowOn) "ON" else "OFF"
            }
            root.addView(row)
        }

        fun divider() = root.addView(android.view.View(ctx).apply {
            setBackgroundColor(outlineVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, (1*dp).toInt())
                .also { it.setMargins((84*dp).toInt(), 0, 0, 0) }
        })
        toggleRow("🔔", "乗務前リマインダー", AppSettings.getReminderBeforeEnabled(ctx)) { on, _ ->
            AppSettings.setReminderBeforeEnabled(ctx, on)
        }
        divider()
        toggleRow("🔔", "乗務後リマインダー", AppSettings.getReminderAfterEnabled(ctx)) { on, _ ->
            AppSettings.setReminderAfterEnabled(ctx, on)
        }

        val scrollView = android.widget.ScrollView(ctx).apply { addView(root) }
        sheet.setContentView(scrollView)
        sheet.setOnDismissListener { onDismiss() }
        sheet.show()
    }

    private fun createBackup() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { BackupManager.createBackup(ctx) }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val mime = if (file.name.endsWith(".rbe")) "application/octet-stream" else "application/zip"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "バックアップを保存"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "バックアップに失敗しました", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateLicenseStatus() {
        if (!isAdded) return
        val ctx = requireContext()
        val appSettings = com.rodgers.routist.util.AppSettings
        binding.tvLicenseStatus.text = when {
            appSettings.isSubscriptionActive(ctx) ->
                "サブスク有効（Google Play）"
            appSettings.isLicenseValid(ctx) -> {
                val expiry = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.JAPAN)
                    .format(java.util.Date(appSettings.getLicenseExpiry(ctx)))
                "ライセンス有効（期限: $expiry）"
            }
            appSettings.isInTrial(ctx) -> {
                val days = appSettings.trialDaysLeft(ctx)
                "試用期間中（残り${days}日）→ タップして購入"
            }
            else -> "試用期間終了 → タップして購入"
        }
    }

    private fun showLicensePurchaseDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val appSettings = com.rodgers.routist.util.AppSettings
        // すでに有効な場合は状態を表示するだけ
        if (appSettings.isSubscriptionActive(ctx)) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Google Play サブスク")
                .setMessage("サブスクリプションは有効です。\n\n解約する場合は Google Play → 定期購入 から操作してください。")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        if (appSettings.isLicenseValid(ctx)) {
            val expiry = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.JAPAN)
                .format(java.util.Date(appSettings.getLicenseExpiry(ctx)))
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("ライセンス有効")
                .setMessage("ライセンス有効期限: $expiry\n\nGoogle Play サブスクに切り替えることもできます（自動更新）")
                .setPositiveButton("サブスクに切り替える") { _, _ -> showSubscriptionOptions() }
                .setNegativeButton("このままでOK", null)
                .show()
            return
        }
        // 試用中 or 期限切れ → 購入オプション表示
        showSubscriptionOptions()
    }

    private fun showSubscriptionOptions() {
        if (!isAdded) return
        val ctx = requireContext()
        val act = activity ?: return

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("HaiReel プレミアム")
            .setMessage("7日間の無料試用後は、以下のプランをお選びください。")
            .setItems(arrayOf(
                "月額プラン（¥300/月）",
                "年額プラン（¥2,980/年）← おすすめ・月換算¥248",
                "ライセンスキーを入力（BOOTH等で購入済み）"
            )) { _, which ->
                when (which) {
                    0 -> com.rodgers.routist.util.BillingManager
                            .launchSubscription(act, com.rodgers.routist.util.BillingManager.PRODUCT_MONTHLY)
                    1 -> com.rodgers.routist.util.BillingManager
                            .launchSubscription(act, com.rodgers.routist.util.BillingManager.PRODUCT_YEARLY)
                    2 -> {
                        (act as? com.rodgers.routist.MainActivity)?.showLicenseInputDialog()
                        updateLicenseStatus()
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showResetDataDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("⚠️ データをすべて初期化")
            .setMessage("日報・配達先・ルート・帳票パターン・署名を含むすべてのデータを削除します。\n\nこの操作は元に戻せません。\n\n先にバックアップを作成することをおすすめします。")
            .setPositiveButton("初期化する") { _, _ ->
                val appCtx2 = ctx.applicationContext
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val db = com.rodgers.routist.db.AppDatabase.getInstance(appCtx2)
                        db.workRecordDao().deleteAll()
                        db.deliveryDao().deleteAll()
                        db.deliveryGroupDao().deleteAll()
                        db.tenkoDao().deleteAll()
                        db.geocodingCacheDao().deleteAll()
                        // SharedPreferences をクリア
                        appCtx2.getSharedPreferences(com.rodgers.routist.util.AppSettings.PREFS, android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        appCtx2.getSharedPreferences("delivery_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        appCtx2.getSharedPreferences("report_patterns", android.content.Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        // 暗号化設定をクリア（APIキー・ライセンスキー・バックアップパスワード）
                        com.rodgers.routist.util.AppSettings.clearSensitiveData(appCtx2)
                        // 署名を削除
                        for (type in listOf(com.rodgers.routist.util.SignatureStorage.TYPE_DRIVER, com.rodgers.routist.util.SignatureStorage.TYPE_CLIENT)) {
                            com.rodgers.routist.util.SignatureStorage.fileFor(appCtx2, type).delete()
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
                        android.widget.Toast.makeText(appCtx2, "初期化に失敗しました: ${e.localizedMessage ?: "不明なエラー"}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showTermsDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val tv = android.widget.TextView(ctx).apply {
            text = """利用規約

第1条（目的）
本規約は、HaiReel（以下「本アプリ」）の利用条件を定めるものです。
ご利用の前に必ずお読みください。

第2条（利用対象）
本アプリは軽貨物ドライバーおよびその業務に関わる方を対象とした業務管理ツールです。

第3条（禁止事項）
・逆コンパイル・リバースエンジニアリング
・ライセンスキーの第三者への譲渡・共有
・違法な目的での使用
・本アプリを利用した迷惑行為

第4条（免責事項）
・本アプリはあくまで補助ツールです。点呼記録・日報の法的効力を保証しません。
・ルート最適化の結果は参考情報であり、精度を保証しません。
・データの消失・破損に関して開発者は責任を負いません。
・本アプリの利用により生じた損害について、開発者は一切責任を負いません。

第5条（サービスの変更・停止）
開発者は事前の通知なくサービスの内容変更・停止を行う場合があります。

第6条（料金）
試用期間（7日間）は無料でご利用いただけます。
継続利用にはライセンスキーの購入が必要です。

第7条（準拠法・管轄）
本規約は日本法に準拠し、紛争は開発者所在地の裁判所を第一審管轄とします。

第8条（規約の変更）
本規約は事前の通知なく変更される場合があります。
変更後の規約はアプリ内に掲示された時点で効力を生じます。"""
            textSize = 13f
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            setTextColor(com.google.android.material.color.MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK))
        }
        val scroll = android.widget.ScrollView(ctx).apply { addView(tv) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("利用規約")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showSctDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val tv = android.widget.TextView(ctx).apply {
            text = """特定商取引法に基づく表記

■ 販売業者
imai kenichi

■ 所在地
個人情報保護のため非公開
（お問い合わせいただければ開示します）

■ 電話番号
個人情報保護のため非公開
（お問い合わせいただければ開示します）

■ メールアドレス
proxyroutine777@gmail.com

■ 販売価格
ライセンスキー（1年間有効）
詳細は販売ページをご確認ください。

■ 支払方法
クレジットカード・その他決済（Stripe経由）

■ 支払時期
ご注文と同時にお支払いいただきます。

■ 提供時期
お支払い確認後、メールにてライセンスキーをお送りします。

■ 返品・キャンセルポリシー
デジタルコンテンツ（ライセンスキー）の性質上、
発行後の返金・キャンセルは原則お受けできません。
ただし、キーが正常に機能しない場合はご対応いたします。

■ 動作環境
Android 8.0（API 26）以上"""
            textSize = 13f
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            setTextColor(com.google.android.material.color.MaterialColors.getColor(
                ctx, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK))
        }
        val scroll = android.widget.ScrollView(ctx).apply { addView(tv) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("特定商取引法に基づく表記")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showHelpDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val onSurface    = com.google.android.material.color.MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK)
        val onSurfaceVar = com.google.android.material.color.MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.DKGRAY)
        val primary      = com.google.android.material.color.MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE)
        val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((20*dp).toInt(), (12*dp).toInt(), (20*dp).toInt(), (16*dp).toInt())
        }

        fun section(title: String) {
            root.addView(android.widget.TextView(ctx).apply {
                text = title; textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(primary)
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (16*dp).toInt(); it.bottomMargin = (6*dp).toInt() }
            })
        }

        fun item(text: String) {
            root.addView(android.widget.TextView(ctx).apply {
                this.text = text; textSize = 14f; setTextColor(onSurface)
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (6*dp).toInt() }
            })
        }

        fun note(text: String) {
            root.addView(android.widget.TextView(ctx).apply {
                this.text = text; textSize = 12f; setTextColor(onSurfaceVar)
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (4*dp).toInt() }
            })
        }

        section("📦 基本的な使い方")
        item("① 配達リストメニュー →「名前・住所を追加」で住所を入力")
        item("② 地図ボタン → 「ルート最適化」で最短順に自動整列")
        item("③ 配達完了後に各行をタップして完了マークをつける")
        item("④ 点呼タブで乗務前後の点呼を記録する")
        item("⑤ 報告タブで日報（収入・走行距離）を記録してExcel出力")

        section("🔑 Google APIキーについて")
        item("住所検索・地図機能にGoogle APIキーが必要です。")
        item("設定 →「Google APIキー設定」から登録できます。")
        note("月額\$200相当まで無料枠があり、個人利用は通常無料枠内に収まります。")
        note("料金はGoogle側で発生します。利用前に料金体系をご確認ください。")

        section("💡 便利な使い方")
        item("伝票スキャン: カメラで伝票を撮影して住所を自動読み取り")
        item("帳票パターン: 取引先ごとに帳票の設定を切り替えられます")
        item("バックアップ: 定期的に設定 → バックアップを作成してください")
        item("ライセンス: 設定 → ライセンスから購入・認証できます")

        section("❓ よくある質問")
        item("Q. 地図が白くなる")
        note("→ Google APIキーが設定されていないか無効です。設定 → Google APIキー設定を確認してください。")
        item("Q. ルート最適化ができない")
        note("→ 住所のジオコーディング（緑マーク）が完了しているか確認してください。")
        item("Q. 収入が表示されない")
        note("→ 日報の「収入（円）」欄に金額を入力して保存してください。")
        item("Q. データが消えた")
        note("→ 設定 → バックアップから復元できます。定期的なバックアップをおすすめします。")
        item("Q. ライセンスキーを紛失した")
        note("→ ご購入時のメールをご確認ください。見つからない場合は proxyroutine777@gmail.com にお問い合わせください。")

        val scroll = android.widget.ScrollView(ctx).apply { addView(root) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("❓ 使い方・ヘルプ")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val tv  = TextView(ctx).apply {
            text = """プライバシーポリシー
最終更新日：2026年6月

HaiReel（以下「本アプリ」）は、ユーザーのプライバシーを尊重し、個人情報の保護に努めます。

■ 収集する情報
・配達先情報（氏名・住所・備考など）
・日報・点呼記録・収支データ
・位置情報（地図・ルート最適化機能を使用する場合のみ・端末外に送信しない）
・Google APIキー（端末内に暗号化して保存）

■ 利用目的
収集した情報は本アプリの機能提供のみに使用します。
外部サーバーへの送信は行いません。

■ データの保存場所
すべてのデータは端末内にのみ保存されます。
バックアップファイルをエクスポートした場合の管理はユーザー自身の責任となります。

■ 第三者への提供
ユーザーのデータを第三者に提供・販売することは一切ありません。

■ Google APIの利用
住所検索・地図機能においてGoogle Geocoding API・Places API・Maps SDK for Androidを使用します。
これらのAPIはGoogleのプライバシーポリシーに従います。

■ Firebase Crashlytics
アプリ安定性向上のためクラッシュレポートを収集します。
このレポートには個人を特定できる情報は含まれません。

■ データの削除
アプリを削除するとすべてのデータが端末から削除されます。
ただし端末のバックアップ機能により復元される場合があります。

■ お問い合わせ
proxyroutine777@gmail.com"""
            textSize = 14f
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            setTextColor(
                MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
            )
        }
        val scroll = ScrollView(ctx).apply { addView(tv) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("プライバシーポリシー")
            .setView(scroll)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun showAppSettingsDialog() {
        if (!isAdded) return
        val ctx   = requireContext()
        val dp    = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val scroll = ScrollView(ctx)
        val root   = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(root)

        val colorOnSurface        = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val colorOnSurfaceVariant = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
        val colorOutline          = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline, Color.GRAY)

        fun section(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 14f; setTextColor(colorOnSurface)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (18 * dp).toInt(); it.bottomMargin = (6 * dp).toInt() }
        }
        fun field(text: String) = TextView(ctx).apply {
            this.text = text; textSize = 13f; setTextColor(colorOnSurfaceVariant)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.topMargin = (12 * dp).toInt(); it.bottomMargin = (4 * dp).toInt() }
        }

        // ── 表示設定
        root.addView(section("── 表示設定"))
        val darkGroup    = RadioGroup(ctx)
        val rbDarkSystem = RadioButton(ctx).apply { text = "システム設定に従う"; id = View.generateViewId() }
        val rbDarkLight  = RadioButton(ctx).apply { text = "ライトモード"; id = View.generateViewId() }
        val rbDarkDark   = RadioButton(ctx).apply { text = "ダークモード"; id = View.generateViewId() }
        darkGroup.addView(rbDarkSystem); darkGroup.addView(rbDarkLight); darkGroup.addView(rbDarkDark)
        when (AppSettings.getDarkMode(ctx)) {
            AppCompatDelegate.MODE_NIGHT_NO  -> rbDarkLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> rbDarkDark.isChecked = true
            else -> rbDarkSystem.isChecked = true
        }
        root.addView(darkGroup)

        // ── 事業者情報
        root.addView(section("── 事業者情報"))
        root.addView(field("事業者名"))
        val etCompany = EditText(ctx).apply {
            hint = "例: 〇〇運送株式会社"; inputType = InputType.TYPE_CLASS_TEXT
            setText(AppSettings.getCompanyName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etCompany)
        val vehicles = AppSettings.getVehicles(ctx)
        root.addView(field("車番１"))
        val etVehicle1 = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[0]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle1)
        root.addView(field("車番２"))
        val etVehicle2 = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[1]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle2)
        root.addView(field("車番３"))
        val etVehicle3 = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[2]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle3)

        // ── セキュリティ設定
        root.addView(section("── セキュリティ設定"))
        val swAppLock = run {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (4 * dp).toInt() }
            }
            row.addView(TextView(ctx).apply {
                text = "アプリロック（指紋・顔・PIN）"; textSize = 15f
                setTextColor(colorOnSurface)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            val sw = androidx.appcompat.widget.SwitchCompat(ctx).apply {
                isChecked = AppSettings.isAppLockEnabled(ctx)
            }
            row.addView(sw); root.addView(row); sw
        }
        root.addView(field("ロックまでの時間（分）"))
        val etLockTimeout = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "30"
            setText(AppSettings.getLockTimeoutMinutes(ctx).toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etLockTimeout)
        root.addView(TextView(ctx).apply {
            text = "バックグラウンドや操作なしでこの時間が経過するとロックされます"
            textSize = 12f; setTextColor(colorOutline)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        })
        root.addView(field("バックアップパスワード（空欄で暗号化なし）"))
        val etBackupPw = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "パスワードを設定"
            setText(AppSettings.getBackupPassword(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etBackupPw)

        // ── 操作設定
        root.addView(section("── 操作設定"))
        root.addView(field("削除後の取り消し可能時間（秒）"))
        val undoRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        val npUndo = android.widget.NumberPicker(ctx).apply {
            minValue = 3; maxValue = 30
            value = AppSettings.getUndoSeconds(ctx).coerceIn(3, 30)
        }
        undoRow.addView(npUndo)
        undoRow.addView(TextView(ctx).apply {
            text = " 秒（配達先・点呼・日報など削除全般に適用）"
            textSize = 12f; setTextColor(colorOnSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP)
        })
        root.addView(undoRow)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("アプリ設定")
            .setView(scroll)
            .setPositiveButton("保存") { _, _ ->
                AppSettings.setCompanyName(ctx, etCompany.text.toString().trim())
                AppSettings.setVehicles(ctx, listOf(
                    etVehicle1.text.toString().trim(),
                    etVehicle2.text.toString().trim(),
                    etVehicle3.text.toString().trim()
                ))
                val darkMode = when {
                    rbDarkDark.isChecked  -> AppCompatDelegate.MODE_NIGHT_YES
                    rbDarkLight.isChecked -> AppCompatDelegate.MODE_NIGHT_NO
                    else                  -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppSettings.setDarkMode(ctx, darkMode)
                AppCompatDelegate.setDefaultNightMode(darkMode)
                AppSettings.setAppLockEnabled(ctx, swAppLock.isChecked)
                AppSettings.setLockTimeoutMinutes(ctx, etLockTimeout.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 30)
                AppSettings.setBackupPassword(ctx, etBackupPw.text.toString())
                AppSettings.setUndoSeconds(ctx, npUndo.value)
                Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
}
