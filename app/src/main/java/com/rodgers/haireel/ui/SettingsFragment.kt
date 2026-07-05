package com.rodgers.haireel.ui

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
import com.rodgers.haireel.R
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


    private fun doRestore(ctx: android.content.Context, uri: android.net.Uri, password: String? = null) {
        val appCtx = ctx.applicationContext  // Fragmentのライフサイクルに依存しないApplicationContextを使用
        Toast.makeText(appCtx, "復元中...", Toast.LENGTH_SHORT).show()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            try {
                BackupManager.restoreBackup(appCtx, uri, password)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, "復元しました。アプリを再起動します。", Toast.LENGTH_LONG).show()
                }
                kotlinx.coroutines.delay(1500)
                val launchIntent = appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    // AlarmManagerで500ms後に起動予約してからkillProcessする。
                    // startActivity直後にkillすると起動前にプロセスが死ぬため。
                    val pi = android.app.PendingIntent.getActivity(
                        appCtx, 0, launchIntent,
                        android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    val alarmManager = appCtx.getSystemService(android.app.AlarmManager::class.java)
                    alarmManager.set(
                        android.app.AlarmManager.RTC,
                        System.currentTimeMillis() + 500L,
                        pi
                    )
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appCtx, "アプリをランチャーから開いてください", Toast.LENGTH_LONG).show()
                    }
                    kotlinx.coroutines.delay(2000)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    appCtx.showErrorDialog("復元エラー", e.localizedMessage ?: "不明なエラーが発生しました。\nバックアップファイルを確認してください。")
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
            text = "住所を地図座標に変換するには「Google APIキー」が必要です。\nGoogleアカウントがあれば無料で取得でき、個人利用の範囲では料金はかかりません。"
            textSize = 14f; setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })
        root.addView(android.widget.TextView(ctx).apply {
            text = "※ APIキーを設定しなくても、住所管理・日報・点呼は使えます。後からでも変更可能です。"
            textSize = 12f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (16*dp).toInt() }
        })

        fun openUrl(url: String) {
            try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(ctx, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
            }
        }

        fun addLinkButton(label: String, url: String, bottomMargin: Int = 8) {
            root.addView(android.widget.Button(ctx).apply {
                text = label
                isAllCaps = false; textSize = 13f
                setTextColor(primary)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke((1*dp).toInt(), primary)
                    cornerRadius = 8*dp
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (bottomMargin*dp).toInt() }
                setOnClickListener { openUrl(url) }
            })
        }

        // ── STEP 1 ──────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "① Google Cloud プロジェクトを作成する"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (4*dp).toInt() }
        })
        root.addView(android.widget.TextView(ctx).apply {
            text = "Googleアカウントでログイン後、利用規約・プライバシーポリシーへの同意を求められたら「同意する」を選んでください。その後「新しいプロジェクト」を作成してください。（すでにプロジェクトがある場合はスキップ）"
            textSize = 12f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })
        addLinkButton("プロジェクトを作成する →",
            "https://console.cloud.google.com/projectcreate", 20)

        // ── STEP 2 ──────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "② 課金設定をする（必須）"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (4*dp).toInt() }
        })
        root.addView(android.widget.TextView(ctx).apply {
            text = "Google Maps APIを利用するには課金アカウントの登録が必要です。クレジットカードの登録が求められますが、毎月200ドル分の無料枠があるため、個人利用であれば通常は無料で使えます。"
            textSize = 12f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })
        addLinkButton("課金アカウントを設定する →",
            "https://console.cloud.google.com/billing/create", 20)

        // ── STEP 3 ──────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "③ 下の3つのAPIをそれぞれ「有効にする」"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (4*dp).toInt() }
        })
        root.addView(android.widget.TextView(ctx).apply {
            text = "タップするとGoogle Cloudが開きます。「有効にする」ボタンを押してください。"
            textSize = 12f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })

        addLinkButton("Maps SDK for Android を有効にする →",
            "https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com")
        addLinkButton("Geocoding API を有効にする →",
            "https://console.cloud.google.com/apis/library/geocoding-backend.googleapis.com")
        addLinkButton("Places API を有効にする →",
            "https://console.cloud.google.com/apis/library/places-backend.googleapis.com", 20)

        // ── STEP 4 ──────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "④ APIキーを作成してコピーする"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (4*dp).toInt() }
        })
        root.addView(android.widget.TextView(ctx).apply {
            text = "1. 「＋認証情報を作成」→「APIキー」をタップ\n" +
                   "2. プライバシーポリシーへの同意を求められたら「同意する」\n" +
                   "3. API制限の画面で、③で有効にした3つのAPIにそれぞれチェックを入れる\n" +
                   "4. 「鍵を表示します」をタップ\n" +
                   "5. 表示された「AIza...」の文字列をコピー"
            textSize = 12f; setTextColor(onSurfaceVar)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })

        root.addView(android.widget.Button(ctx).apply {
            text = "認証情報ページを開く →"
            isAllCaps = false; textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(primary); cornerRadius = 8*dp
            }
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (20*dp).toInt() }
            setOnClickListener {
                openUrl("https://console.cloud.google.com/apis/credentials")
            }
        })

        // ── STEP 5 ──────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "⑤ コピーしたAPIキーを下に貼り付けて保存"
            textSize = 14f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(onSurface)
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                .also { it.bottomMargin = (8*dp).toInt() }
        })

        val inputField = android.widget.EditText(ctx).apply {
            hint = "④ のAPIキーをここに貼り付け（例: AIza...）"
            setText(AppSettings.getUserApiKey(ctx))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(inputField)

        // クリップボードに AIza... があれば自動貼り付けを提案
        val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
        if (clipText.trim().startsWith("AIza") && inputField.text.isBlank()) {
            root.addView(com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = "📋 クリップボードから貼り付け"
                isAllCaps = false
                setTextColor(primary)
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                setOnClickListener {
                    inputField.setText(clipText.trim())
                    Toast.makeText(ctx, "APIキーを貼り付けました", Toast.LENGTH_SHORT).show()
                }
            })
        }

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
            com.rodgers.haireel.util.GeocodingClient.configure(
                key.ifBlank { com.rodgers.haireel.BuildConfig.GEOCODING_API_KEY }
            )
            binding.tvApiKeyStatus.text = if (key.isNotBlank())
                "設定済み（自分のAPIキーを使用中）"
            else "未設定（住所変換・地図機能が使えません）"
            dlg.dismiss()
            if (key.isNotBlank()) testApiKey(ctx)
            else Toast.makeText(ctx, "APIキーを削除しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showThemePickerDialog() {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val currentKey = AppSettings.getThemeKey(ctx)

        data class ThemeOption(val key: String, val nameJa: String, val colorHex: String)
        val themes = listOf(
            ThemeOption("blue",   "ブルー",    "#1565C0"),
            ThemeOption("teal",   "ティール",  "#006A6A"),
            ThemeOption("green",  "グリーン",  "#2E7D32"),
            ThemeOption("orange", "オレンジ",  "#C84B00"),
            ThemeOption("purple", "パープル",  "#6750A4"),
            ThemeOption("red",    "レッド",    "#BA1A1A"),
            ThemeOption("indigo", "インディゴ","#3949AB"),
            ThemeOption("brown",  "アース",    "#795548"),
        )

        val onSurface = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        }

        for (row in 0..1) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .also { it.bottomMargin = (12 * dp).toInt() }
            }
            for (col in 0..3) {
                val t = themes[row * 4 + col]
                val color = Color.parseColor(t.colorHex)
                val isSelected = t.key == currentKey

                val cell = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    isClickable = true; isFocusable = true
                    val ripple = android.util.TypedValue().also {
                        ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
                    }.resourceId
                    setBackgroundResource(ripple)
                }

                // 色丸（選択中はチェックマーク付き）
                cell.addView(TextView(ctx).apply {
                    text = if (isSelected) "✓" else ""
                    textSize = 22f; setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                        if (isSelected) setStroke((3 * dp).toInt(), Color.WHITE)
                    }
                    layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), (52 * dp).toInt())
                        .also { it.gravity = Gravity.CENTER }
                })

                cell.addView(TextView(ctx).apply {
                    text = t.nameJa; textSize = 11f; gravity = Gravity.CENTER
                    setTextColor(if (isSelected) color else onSurface)
                    if (isSelected) setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .also { it.topMargin = (4 * dp).toInt() }
                })

                cell.setOnClickListener {
                    AppSettings.setThemeKey(ctx, t.key)
                    requireActivity().recreate()
                }
                rowLayout.addView(cell)
            }
            root.addView(rowLayout)
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("テーマカラーを選択")
            .setView(root)
            .setNegativeButton("キャンセル", null)
            .show()
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
        if (AppSettings.getReminderBeforeEnabled(ctx)) items.add("乗務前リマインダー ON")
        if (AppSettings.getReminderAfterEnabled(ctx)) items.add("乗務後リマインダー ON")
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
本アプリは宅配ドライバーおよびその業務に関わる方を対象とした業務管理ツールです。

第3条（知的財産権）
本アプリのデザイン・UI・機能・画面構成・ロゴ・文言・アイコン等に関する知的財産権は、すべて開発者（RODGERS）に帰属します。
本アプリを参考・模倣した類似アプリの作成・配布・販売を禁止します。

第4条（禁止事項）
・逆コンパイル・逆アセンブル・リバースエンジニアリング
・本アプリのデザインや機能を模倣した類似アプリの制作・配布
・サブスクリプションの第三者への譲渡・アカウント共有
・違法な目的での使用
・本アプリを利用した迷惑行為

第5条（免責事項）
・本アプリはあくまで補助ツールです。点呼記録・日報の法的効力を保証しません。
・ルート最適化の結果は参考情報であり、精度を保証しません。
・データの消失・破損に関して開発者は責任を負いません。
・本アプリの利用により生じた損害について、開発者の故意または重大な過失による場合を除き、責任を負いません。
・運転中の本アプリの操作は道路交通法に違反する場合があります。走行中は必ず安全な場所に停車してからご使用ください。

第6条（サービスの変更・停止）
開発者は事前の通知なくサービスの内容変更・停止を行う場合があります。

第7条（料金）
試用期間（7日間）は無料でご利用いただけます。
継続利用には Google Play のサブスクリプション登録が必要です。
月額プラン（¥300/月）または年額プラン（¥2,980/年）をお選びください。
返金についてはGoogle Playの返金ポリシーに準じます。

第8条（準拠法・管轄）
本規約は日本法に準拠し、紛争は開発者所在地の裁判所を第一審管轄とします。

第9条（規約の変更）
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
RODGERS

■ 所在地
消費者からの請求があり次第、遅滞なく開示いたします。
※ 特定商取引法施行規則第11条の2の規定に基づきます。

■ 電話番号
消費者からの請求があり次第、遅滞なく開示いたします。
お問い合わせは下記メールにて承ります。

■ お問い合わせ先
dev.claude.rodgers@gmail.com

■ 販売価格
月額プラン：¥300（税込）/月
年額プラン：¥2,980（税込）/年

■ 支払方法
Google Play を通じた決済（クレジットカード等）

■ 支払時期
サブスクリプション登録時および各更新時に自動決済されます。

■ 提供時期
決済完了後、即時ご利用いただけます。

■ 解約・返金ポリシー
Google Play の定期購入管理からいつでも解約できます。
解約後は次回更新日まで引き続きご利用いただけます。
既払い分の返金は Google Play の規約に準じます。

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
        item("① 配達タブの右上メニューから「名前・住所を追加」で住所を入力")
        item("② トグルで地図に切り替え → メニューの「ルート最適化」で現在地から最短順に自動整列")
        item("③ 各行をタップ →「完了にする」で完了マークをつける")
        note("→ チップで「すべて / 未完了のみ / 完了のみ」を切り替えられます")
        item("④ 点呼タブで乗務前後の点呼を記録する")
        item("⑤ 報告タブで日報（収入・走行距離）を記録してExcel出力")

        section("🔑 Google APIキーについて")
        item("住所検索・地図機能にGoogle APIキーが必要です。")
        item("設定 →「Google APIキー設定」をタップするとウィザードが開きます。")
        note("Google Cloudプロジェクト作成・課金設定・API有効化の手順をウィザードで案内します。")
        note("毎月200ドル分の無料枠があり、個人利用は通常無料枠内に収まります。")

        section("💡 便利な使い方")
        item("帳票パターン: 取引先ごとに帳票の設定（社名・単価など）を切り替えられます")
        item("ウィジェット: ホーム画面にウィジェットを追加して今日の配達件数を確認")
        item("バックアップ: 定期的に設定 → バックアップを作成してください")
        item("プラン: 設定 → プランから月額・年額プランに登録できます")

        section("❓ よくある質問")
        item("Q. 地図が白くなる")
        note("→ Google APIキーが設定されていないか無効です。設定 →「Google APIキー設定」を確認してください。")
        item("Q. ルート最適化ができない")
        note("→ 配達リストで「⏳ 検索中」が消えているか確認してください。住所が地図に配置されるまで少し待ってから実行してください。APIキーの設定も確認してください。")
        item("Q. 住所変換に失敗する")
        note("→ 設定 →「Google APIキー設定」でAPIキーを確認・再入力してください。")
        item("Q. 収入が表示されない")
        note("→ 日報の「収入（円）」欄に金額を入力して保存してください。")
        item("Q. データが消えた")
        note("→ 設定 → バックアップから復元できます。定期的なバックアップをおすすめします。")
        item("Q. サブスクが有効にならない")
        note("→ 設定 → プランをタップし Google Play での購入状況を確認してください。解決しない場合は dev.claude.rodgers@gmail.com にお問い合わせください。")

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
最終更新日：2026年7月

HaiReel（以下「本アプリ」）は、ユーザーのプライバシーを尊重し、個人情報の保護に努めます。

■ 収集する情報
・配達先情報（氏名・住所・備考など）
・日報・点呼記録・収支データ
・位置情報（地図・ルート最適化機能を使用する場合のみ）
・Google APIキー（端末内に暗号化して保存）

■ 利用目的
収集した情報は本アプリの機能提供のみに使用します。

■ データの保存場所
配達先・日報・点呼・収支データはすべて端末内にのみ保存されます。
バックアップファイルをエクスポートした場合の管理はユーザー自身の責任となります。

■ 第三者への提供
ユーザーの業務データを第三者に提供・販売することは一切ありません。

■ Google APIの利用
住所検索・地図機能においてGoogle Geocoding API・Places API・Maps SDK for Androidを使用します。
住所検索時に入力された住所データはGoogleのサーバーに送信されます。
位置情報は地図・ルート最適化機能のためGoogleのサービスに送信される場合があります。
これらのAPIはGoogleのプライバシーポリシー（https://policies.google.com/privacy）に従います。

■ Firebase Crashlytics
アプリ安定性向上のためクラッシュレポートをGoogleのサーバーに送信します。
このレポートには個人を特定できる情報は含まれません。

■ Firebase Analytics
アプリ改善のため画面遷移・起動回数などの匿名使用状況をGoogleのサーバーに送信します。
個人を特定できる情報は収集しません。

■ データの削除
アプリを削除するとすべてのデータが端末から削除されます。
ただし端末のバックアップ機能により復元される場合があります。

■ お問い合わせ
dev.claude.rodgers@gmail.com"""
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
            hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[0]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle1)
        root.addView(field("車番２"))
        val etVehicle2 = EditText(ctx).apply {
            hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[1]); layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle2)
        root.addView(field("車番３"))
        val etVehicle3 = EditText(ctx).apply {
            hint = "例: 〇〇 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
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
