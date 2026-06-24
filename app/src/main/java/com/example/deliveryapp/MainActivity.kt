package com.rodgers.routist

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.rodgers.routist.databinding.ActivityMainBinding
import com.rodgers.routist.ui.DailyReportFragment
import com.rodgers.routist.ui.DeliveryListFragment
import com.rodgers.routist.ui.TenkoFragment
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.BillingManager
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
import com.rodgers.routist.viewmodel.*
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: DeliveryViewModel by viewModels()
    private var suppressNavSync = false

    // バックアップ復元ファイルピッカー（Hilt Fragment内では動かないためActivity側で管理）
    private var restoreCallback: ((android.net.Uri?) -> Unit)? = null
    private val restoreFilePicker = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> restoreCallback?.invoke(uri); restoreCallback = null }

    fun launchRestoreFilePicker(callback: (android.net.Uri?) -> Unit) {
        restoreCallback = callback
        restoreFilePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    private var statusBarHeight = 0
    private var isAuthenticated = false
    private var isPromptShowing = false
    private var backgroundedAtMs = 0L
    private val lockHandler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable { triggerInactivityLock() }
    private lateinit var lockOverlay: View

    // タブ順序: 点呼=0, 配達=1, 記録=2, 設定=3
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.getDarkMode(this))
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 初回起動日を記録
        AppSettings.ensureInstallDate(this)
        // Google Play サブスク状態をバックグラウンドで確認・更新
        BillingManager.init(this)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        isAuthenticated = savedInstanceState?.getBoolean("isAuthenticated") ?: false
        backgroundedAtMs = savedInstanceState?.getLong("backgroundedAtMs") ?: 0L
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = bars.top
            binding.appBarLayout.updatePadding(top = bars.top)
            binding.bottomNavigation.updatePadding(bottom = bars.bottom)
            updateToolbarForTab(binding.viewPager.currentItem)
            WindowInsetsCompat.CONSUMED
        }

        lockOverlay = View(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        (binding.root as ViewGroup).addView(lockOverlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lockOverlay.visibility = if (AppSettings.isAppLockEnabled(this)) View.VISIBLE else View.GONE

        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.toolbar.setOnClickListener { showGroupDropdown() }

        setupTabs()

        // ライセンス期限通知チェック（デバッグビルドはスキップ）
        if (!com.rodgers.routist.BuildConfig.DEBUG) {
            com.rodgers.routist.util.LicenseNotificationHelper.checkAndNotify(this)
        }

        // 利用規約未同意の場合は同意を求める（デバッグビルドはスキップ）
        if (!com.rodgers.routist.BuildConfig.DEBUG && !AppSettings.isTermsAgreed(this)) {
            showTermsAgreementDialog()
        }

        // ライセンス・試用期間チェック（デバッグビルドはスキップ）
        if (!com.rodgers.routist.BuildConfig.DEBUG) {
            if (!AppSettings.canUseApp(this)) {
                showSubscriptionDialog()
            } else if (AppSettings.isInTrial(this)) {
                val days = AppSettings.trialDaysLeft(this)
                if (days <= 2) {
                    com.google.android.material.snackbar.Snackbar
                        .make(binding.root, "無料試用期間 残り${days}日です", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction("プランを選ぶ") { showSubscriptionDialog() }
                        .show()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.deliveries.collectLatest { _ ->
                val groupName = viewModel.currentGroup()?.name ?: "マップリスト"
                if (binding.viewPager.currentItem == 1) {
                    supportActionBar?.title = "$groupName ▼"
                }
                binding.progressBar.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.currentGroupId.collectLatest {
                if (binding.viewPager.currentItem == 1) {
                    val groupName = viewModel.currentGroup()?.name ?: "マップリスト"
                    supportActionBar?.title = "$groupName ▼"
                }
            }
        }

        lifecycleScope.launch {
            viewModel.geocodingProgress.collectLatest { progress ->
                if (progress == null) return@collectLatest
                if (progress.isRunning) {
                    supportActionBar?.subtitle = "処理中… ${progress.current}/${progress.total}"
                } else {
                    supportActionBar?.subtitle = null
                    if (progress.successCount > 0) {
                        val msg = if (progress.successCount == progress.total) {
                            "${progress.total}件の住所を地図に配置しました"
                        } else {
                            "${progress.total}件中${progress.successCount}件を地図に配置しました"
                        }
                        android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { msg ->
                if (!msg.isNullOrBlank()) {
                    android.widget.Toast.makeText(this@MainActivity, msg, android.widget.Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.geocodingFailedCount.collectLatest { count ->
                if (count > 0) {
                    Snackbar.make(
                        binding.root,
                        "${count}件の住所を特定できませんでした",
                        Snackbar.LENGTH_LONG
                    )
                        .setAction("再試行") { viewModel.retryGeocoding() }
                        .addCallback(object : Snackbar.Callback() {
                            override fun onDismissed(snackbar: Snackbar?, event: Int) {
                                viewModel.clearGeocodingFailure()
                            }
                        })
                        .show()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.pendingOverwrite.collectLatest { confirmation ->
                if (confirmation == null) return@collectLatest
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("元ファイルを上書きしますか？")
                    .setMessage("リストを編集しました。インポート元のファイルにも変更を反映しますか？")
                    .setPositiveButton("上書き") { _, _ -> viewModel.confirmOverwrite() }
                    .setNegativeButton("キャンセル") { _, _ -> viewModel.cancelOverwrite() }
                    .show()
            }
        }
    }

    private fun setupTabs() {
        val fragments = listOf(
            TenkoFragment(),
            DeliveryListFragment(),
            com.rodgers.routist.ui.ReportContainerFragment(),
            com.rodgers.routist.ui.SettingsFragment()
        )

        binding.viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.setCurrentItem(1, false)
        binding.bottomNavigation.selectedItemId = R.id.nav_list

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (!suppressNavSync) {
                val pos = navItemToPos(item.itemId) ?: return@setOnItemSelectedListener false
                binding.viewPager.setCurrentItem(pos, true)
            }
            true
        }

        binding.viewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = posToNavItemId(position) ?: return
                suppressNavSync = true
                binding.bottomNavigation.selectedItemId = itemId
                suppressNavSync = false
                if (position == 1) viewModel.setMapFilter(null)
                updateToolbarForTab(position)
            }
        })

        updateToolbarForTab(1)
    }

    private fun navItemToPos(itemId: Int): Int? = when (itemId) {
        R.id.nav_tenko    -> 0
        R.id.nav_list     -> 1
        R.id.nav_report   -> 2
        R.id.nav_settings -> 3
        else -> null
    }

    private fun posToNavItemId(pos: Int): Int? = when (pos) {
        0 -> R.id.nav_tenko
        1 -> R.id.nav_list
        2 -> R.id.nav_report
        3 -> R.id.nav_settings
        else -> null
    }

    private fun showGroupDropdown() {
        val groups = viewModel.groups.value
        val currentId = viewModel.currentGroupId.value

        val items = groups.map { group ->
            if (group.id == currentId) "✓  ${group.name}" else "　  ${group.name}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("ルートを切り替え")
            .setItems(items) { _, which ->
                val group = groups[which]
                if (group.id != currentId) viewModel.switchGroup(group.id)
            }
            .setNegativeButton("閉じる", null)
            .show()
    }

    private fun updateToolbarForTab(position: Int) {
        val listPos = 1
        if (position == listPos) {
            binding.appBarLayout.visibility = View.VISIBLE
            binding.contentFrame.updatePadding(top = 0)
            val groupName = viewModel.currentGroup()?.name ?: "マップリスト"
            supportActionBar?.title = "$groupName ▼"
            supportActionBar?.subtitle = null
        } else {
            binding.appBarLayout.visibility = View.GONE
            binding.contentFrame.updatePadding(top = statusBarHeight)
        }
    }

    fun switchToList()   { binding.viewPager.currentItem = 1 }
    fun switchToTenko()  { binding.viewPager.currentItem = 0 }
    fun switchToReport() { binding.viewPager.currentItem = 2 }

    override fun onResume() {
        super.onResume()
        if (!AppSettings.isAppLockEnabled(this)) {
            resetLockTimer()
            return
        }
        val elapsed   = if (backgroundedAtMs > 0L) System.currentTimeMillis() - backgroundedAtMs else Long.MAX_VALUE
        val timeoutMs = AppSettings.getLockTimeoutMinutes(this) * 60_000L
        backgroundedAtMs = 0L
        if (!isAuthenticated || elapsed >= timeoutMs) {
            isAuthenticated = false
            lockOverlay.visibility = View.VISIBLE
            showBiometricPrompt()
        } else {
            resetLockTimer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isAuthenticated", isAuthenticated)
        outState.putLong("backgroundedAtMs", backgroundedAtMs)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAtMs = System.currentTimeMillis()
        lockHandler.removeCallbacks(lockRunnable)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        resetLockTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun resetLockTimer() {
        if (!AppSettings.isAppLockEnabled(this) || !isAuthenticated) return
        lockHandler.removeCallbacks(lockRunnable)
        val ms = AppSettings.getLockTimeoutMinutes(this) * 60_000L
        lockHandler.postDelayed(lockRunnable, ms)
    }

    private fun triggerInactivityLock() {
        if (!AppSettings.isAppLockEnabled(this)) return
        isAuthenticated = false
        lockOverlay.visibility = View.VISIBLE
        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        if (isPromptShowing) return
        val can = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            isAuthenticated = true
            lockOverlay.visibility = View.GONE
            return
        }
        isPromptShowing = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("HaiReel のロック解除")
            .setSubtitle("指紋・顔・PINで認証してください")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isPromptShowing = false
                    isAuthenticated = true
                    lockOverlay.visibility = View.GONE
                    resetLockTimer()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isPromptShowing = false
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> finishAffinity()
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "認証試行回数が上限に達しました。しばらく待ってから再試行してください。",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            finishAffinity()
                        }
                        else -> {
                            // ハードウェアの一時エラーなどは再試行する
                            lockHandler.postDelayed({ showBiometricPrompt() }, 1000)
                        }
                    }
                }
                override fun onAuthenticationFailed() {}
            }
        ).authenticate(info)
    }

    internal fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "不明"
        } catch (_: Exception) { "不明" }
        AlertDialog.Builder(this)
            .setTitle("HaiReel")
            .setMessage(
                "バージョン: $version\n" +
                "開発者: imai kenichi\n" +
                "お問い合わせ: proxyroutine777@gmail.com\n\n" +
                "© 2026 RODGERS  All rights reserved.\n\n" +
                "── 使用技術 ──\n" +
                "地図: Google Maps SDK for Android\n" +
                "住所変換: Google Geocoding API\n" +
                "文字認識: Google ML Kit\n" +
                "クラッシュ監視: Firebase Crashlytics\n\n" +
                "── 更新履歴 ──\n" +
                "v1.0.0\n" +
                "・初回リリース\n" +
                "・配達先管理・地図・ルート最適化\n" +
                "・点呼記録・日報・帳票Excel/PDF出力\n" +
                "・バックアップ・ライセンス認証\n" +
                "・試用期間7日間"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ── 利用規約同意 ─────────────────────────────────────────────

    private fun showTermsAgreementDialog() {
        val dp = resources.displayMetrics.density
        val cb = android.widget.CheckBox(this).apply {
            text = "利用規約・プライバシーポリシーに同意します"
            textSize = 14f
            setPadding((8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }
        val note = android.widget.TextView(this).apply {
            text = "設定 → 利用規約 で内容を確認できます。\n同意しない場合はアプリを利用できません。"
            textSize = 12f
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), (8 * dp).toInt())
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            addView(cb); addView(note)
        }
        val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("利用規約への同意")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("同意して利用する", null)
            .setNegativeButton("同意しない") { _, _ -> finishAffinity() }
            .create()
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!cb.isChecked) {
                android.widget.Toast.makeText(this, "チェックを入れてください", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppSettings.setTermsAgreed(this)
            dlg.dismiss()
        }
    }

    // ── ライセンス関連 ────────────────────────────────────────────

    internal fun showSubscriptionDialog() {
        val dp = resources.displayMetrics.density

        // プラン選択ダイアログ（一般アプリ水準のUI）
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        }

        val msg = android.widget.TextView(this).apply {
            text = "HaiReel を続けてご利用いただくには\nプランのご登録が必要です。"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#555555"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        container.addView(msg)

        // 年額プラン（おすすめ）
        val cardYear = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#E8F4FD"))
                cornerRadius = 12 * dp
                setStroke((2 * dp).toInt(), android.graphics.Color.parseColor("#1565C0"))
            }
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (10 * dp).toInt()
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener {
                (it.context as? android.app.AlertDialog)?.dismiss()
                BillingManager.launchSubscription(this@MainActivity, BillingManager.PRODUCT_YEARLY)
            }
        }
        cardYear.addView(android.widget.TextView(this).apply {
            text = "★ おすすめ  年額プラン"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#1565C0"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        cardYear.addView(android.widget.TextView(this).apply {
            text = "¥2,980 / 年"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#1A237E"))
        })
        cardYear.addView(android.widget.TextView(this).apply {
            text = "月換算 ¥248 · 月額より ¥52 お得"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#1565C0"))
        })
        container.addView(cardYear)

        // 月額プラン
        val cardMonth = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F5F5F5"))
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt(), android.graphics.Color.parseColor("#BDBDBD"))
            }
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener {
                (it.context as? android.app.AlertDialog)?.dismiss()
                BillingManager.launchSubscription(this@MainActivity, BillingManager.PRODUCT_MONTHLY)
            }
        }
        cardMonth.addView(android.widget.TextView(this).apply {
            text = "月額プラン"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#424242"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        cardMonth.addView(android.widget.TextView(this).apply {
            text = "¥300 / 月"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.parseColor("#212121"))
        })
        container.addView(cardMonth)

        // 特典リスト
        val features = android.widget.TextView(this).apply {
            text = "✓ 配達先リスト管理（Google マップ連携）\n" +
                   "✓ 日報・収支の自動計算\n" +
                   "✓ 点呼記録のデジタル化\n" +
                   "✓ Excel / PDF 出力\n" +
                   "✓ AES暗号化バックアップ"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#424242"))
            setLineSpacing(0f, 1.5f)
        }
        container.addView(features)

        val dlg = AlertDialog.Builder(this)
            .setTitle("HaiReel プレミアム")
            .setView(container)
            .setNegativeButton("閉じる") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .create()

        // カードのクリックにダイアログ参照を渡す
        cardYear.setOnClickListener {
            dlg.dismiss()
            BillingManager.launchSubscription(this, BillingManager.PRODUCT_YEARLY)
        }
        cardMonth.setOnClickListener {
            dlg.dismiss()
            BillingManager.launchSubscription(this, BillingManager.PRODUCT_MONTHLY)
        }
        dlg.show()
    }

    // 旧メソッド: 後方互換のため残す
    internal fun showLicenseExpiredDialog() = showSubscriptionDialog()

    internal fun showLicenseInputDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "RJ-YYYY-XXXXXXXX-XXXXXXXX"
            textSize = 13f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(60, 32, 60, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("🔑 ライセンスキーを入力")
            .setMessage("購入後にメールでお送りしたライセンスキーを入力してください。")
            .setView(input)
            .setPositiveButton("認証する") { _, _ ->
                val key = input.text.toString().trim()
                if (com.rodgers.routist.util.LicenseManager.activate(this, key)) {
                    val expiry = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.JAPAN)
                        .format(java.util.Date(AppSettings.getLicenseExpiry(this)))
                    AlertDialog.Builder(this)
                        .setTitle("✅ 認証完了")
                        .setMessage("ライセンスが有効化されました。\n有効期限: $expiry")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("❌ 認証失敗")
                        .setMessage("ライセンスキーが正しくありません。\nご購入メールをご確認ください。")
                        .setPositiveButton("再入力") { _, _ -> showLicenseInputDialog() }
                        .setNegativeButton("閉じる", null)
                        .show()
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
}
