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
            com.rodgers.haireel.util.BillingManager.subscriptionState.collect {
                updateLicenseStatus()
            }
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
                        startRestore(uri, pw)
                    }
                    .setNegativeButton("キャンセル", null)
                    .show()
            } else {
                startRestore(uri)
            }
        }
    }

    private fun startRestore(uri: android.net.Uri, password: String? = null) {
        backupHandler.doRestore(
            uri, password,
            onRestored = { failedCount ->
                if (isAdded) {
                    val message = if (failedCount > 0)
                        "復元しました。ただし${failedCount}件のデータは復元できませんでした。\nOKを押すとアプリを再起動します。"
                    else
                        "復元しました。OKを押すとアプリを再起動します。"
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("復元完了")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK") { _, _ -> backupHandler.restartApp() }
                        .show()
                } else {
                    backupHandler.restartApp()
                }
            },
            onError = { msg -> if (isAdded) requireContext().showErrorDialog("復元エラー", msg) }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun createBackup() {
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = backupHandler.createBackup()
                val mime = if (file.name.endsWith(".rbe")) "application/octet-stream" else "application/zip"
                shareFile(file, mime, "バックアップを保存")
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
            s.isSubscriptionActive(ctx) ->
                if (s.getSubscriptionSource(ctx) == "web") "プレミアム会員（HP）" else "プレミアム会員（Google Play）"
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
                resetHandler.resetAllData(
                    onReset = {
                        if (isAdded) {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("初期化完了")
                                .setMessage("初期化が完了しました。OKを押すとアプリを再起動します。")
                                .setCancelable(false)
                                .setPositiveButton("OK") { _, _ -> resetHandler.restartApp() }
                                .show()
                        } else {
                            resetHandler.restartApp()
                        }
                    },
                    onError = { msg -> if (isAdded) ctx.showErrorDialog("初期化エラー", msg) }
                )
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
