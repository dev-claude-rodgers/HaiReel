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
    private val resetHandler  by lazy { SettingsResetHandler(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRows()
        setupBackup()
        observeFlows()
    }

    private fun setupRows() {
        val ctx = requireContext()
        binding.rowAppSettings.setOnClickListener { showAppSettingsDialog() }
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
        binding.tvGeminiKeyStatus.text = if (AppSettings.hasGeminiApiKey(ctx))
            "設定済み（AIアシスタント利用可能）" else "未設定（AIアシスタントを使うには登録が必要）"
        binding.rowGeminiKey.setOnClickListener { showGeminiKeyDialog() }

        addBackgroundRow()
        updateLicenseStatus()
        binding.rowLicense.setOnClickListener { showLicensePurchaseDialog() }
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

    private fun setupBackup() {
        binding.rowBackupCreate.setOnClickListener { createBackup() }
        binding.rowBackupRestore.setOnClickListener {
            (activity as? com.rodgers.haireel.MainActivity)?.launchRestoreFilePicker { uri ->
                if (uri == null) return@launchRestoreFilePicker
                handleRestoreUri(uri)
            }
        }
    }

    private fun observeFlows() {
        viewLifecycleOwner.lifecycleScope.launch {
            com.rodgers.haireel.util.BillingManager.subscriptionState.collect { updateLicenseStatus() }
        }
    }

    private fun handleRestoreUri(uri: android.net.Uri) {
        if (!isAdded) return
        val ctx = requireContext()
        lifecycleScope.launch {
            val rawBytes = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.readBytes()
            }
            if (!isAdded) return@launch
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
        testApiKey(
            ctx           = ctx,
            scope         = viewLifecycleOwner.lifecycleScope,
            onStatusChanged = { if (isAdded) binding.tvApiKeyStatus.text = it },
            onShowWizard  = { showApiKeyWizard() }
        )
    }

    private fun showApiKeyWizard() {
        val ctx = requireContext()
        showApiKeyWizardDialog(
            ctx           = ctx,
            onLaunchIntent = { startActivity(it) },
            onTestApiKey   = { testApiKey(ctx) },
            onStatusChanged = { if (isAdded) binding.tvApiKeyStatus.text = it }
        )
    }

    private fun showGeminiKeyDialog() {
        val ctx = requireContext()
        val et = android.widget.EditText(ctx).apply {
            hint = "AIzaSy…（Google AI Studio で取得）"
            setText(AppSettings.getGeminiApiKey(ctx))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val dp = resources.displayMetrics.density
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), 0)
            addView(android.widget.TextView(ctx).apply {
                text = "Google AI Studio (https://aistudio.google.com) で\n無料のAPIキーを取得してください。"
                textSize = 13f
                setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = (8 * dp).toInt()
                layoutParams = lp
            })
            addView(et)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle("🤖 Gemini AIキー")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val key = et.text.toString().trim()
                AppSettings.setGeminiApiKey(ctx, key)
                binding.tvGeminiKeyStatus.text = if (key.isNotBlank())
                    "設定済み（AIアシスタント利用可能）" else "未設定（AIアシスタントを使うには登録が必要）"
                android.widget.Toast.makeText(ctx, if (key.isNotBlank()) "APIキーを保存しました" else "キーをクリアしました", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("クリア") { _, _ ->
                AppSettings.setGeminiApiKey(ctx, "")
                binding.tvGeminiKeyStatus.text = "未設定（AIアシスタントを使うには登録が必要）"
            }
            .setNegativeButton("キャンセル", null)
            .show()
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
        val act = activity as? androidx.fragment.app.FragmentActivity ?: return
        showLicensePurchaseDialog(requireContext(), act)
    }

    private fun showResetDataDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle("⚠️ データをすべて初期化")
            .setMessage("日報・配達先・ルート・帳票パターン・署名を含むすべてのデータを削除します。\n\nこの操作は元に戻せません。\n\n先にバックアップを作成することをおすすめします。")
            .setPositiveButton("初期化する") { _, _ ->
                resetHandler.resetAllData { msg ->
                    ctx.showErrorDialog("初期化エラー", msg)
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
