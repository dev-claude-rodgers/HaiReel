package com.rodgers.routist

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.BillingManager
import com.rodgers.routist.util.TtsManager
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
    private val bgLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted/denied は自動で GeofenceManager が判定 */ }

    private val fineLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            == PackageManager.PERMISSION_GRANTED) return

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        AlertDialog.Builder(this)
            .setTitle("📦 到着通知を有効にする")
            .setMessage(
                "配達先に近づくと自動で通知が届き、\n" +
                "「✅ 配達完了」をタップするだけで記録できます。\n\n" +
                "この機能を使うには「常に許可」の位置情報権限が必要です。\n" +
                "次の画面で「常に許可」を選択してください。"
            )
            .setPositiveButton("設定する") { _, _ ->
                if (fineGranted) {
                    bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
            .setNegativeButton("後で", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.getDarkMode(this))
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.25f, 0.8f, 0f)
            val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.25f, 0.8f, 0f)
            val fade  = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 1f, 1f, 0f)
            AnimatorSet().also { set ->
                set.playTogether(scaleX, scaleY, fade)
                set.duration = 500L
                set.interpolator = DecelerateInterpolator()
                set.doOnEnd { provider.remove() }
                set.start()
            }
        }
        super.onCreate(savedInstanceState)
        // 初回起動日を記録
        AppSettings.ensureInstallDate(this)
        // TTS初期化
        TtsManager.init(this)
        // TTS読み上げイベントを監視
        lifecycleScope.launch {
            viewModel.ttsNextAddress.collect { text ->
                if (AppSettings.isTtsEnabled(this@MainActivity)) {
                    TtsManager.speak(text, this@MainActivity)
                } else {
                    val uri = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_NOTIFICATION)
                    android.media.RingtoneManager.getRingtone(this@MainActivity, uri)?.play()
                }
            }
        }
        // 到着通知用バックグラウンド位置情報権限をリクエスト
        Handler(Looper.getMainLooper()).postDelayed({ requestBackgroundLocationIfNeeded() }, 2000)
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
                "開発者: RODGERS\n" +
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
                "・バックアップ（AES暗号化）\n" +
                "・7日間無料体験 → 月額¥300 / 年額¥2,980"
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
        buildSubscriptionDialog(this,
            onYearly  = { BillingManager.launchSubscription(this, BillingManager.PRODUCT_YEARLY) },
            onMonthly = { BillingManager.launchSubscription(this, BillingManager.PRODUCT_MONTHLY) },
            onClose   = { finishAffinity() },
            cancelable = false
        ).show()
    }

    /** サブスク購入ダイアログを構築（SettingsFragment からも共有）*/
    internal fun buildSubscriptionDialog(
        ctx: android.content.Context,
        onYearly: () -> Unit,
        onMonthly: () -> Unit,
        onClose: (() -> Unit)? = null,
        cancelable: Boolean = true
    ): AlertDialog {
        val dp = resources.displayMetrics.density

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }

        // ── アプリ名 ──────────────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "🚚  HaiReel プレミアム"
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (4 * dp).toInt() }
        })

        // ── 機能一覧 ──────────────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "配達管理 · 地図ルート · 日報 · 点呼 · Excel出力"
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#757575"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (16 * dp).toInt() }
        })

        // ── 年額ボタン（おすすめ・filled） ───────────────
        val dlgRef = arrayOf<AlertDialog?>(null)
        val btnYear = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "★  年額プラン  ¥2,980 / 年\n月換算 ¥248 · 月額より ¥624 お得"
            textSize = 15f
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10 * dp).toInt() }
            setOnClickListener {
                dlgRef[0]?.dismiss()
                onYearly()
            }
        }
        root.addView(btnYear)

        // ── 月額ボタン（outlined） ────────────────────────
        val btnMonth = com.google.android.material.button.MaterialButton(
            ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "月額プラン  ¥300 / 月"
            textSize = 15f
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (14 * dp).toInt() }
            setOnClickListener {
                dlgRef[0]?.dismiss()
                onMonthly()
            }
        }
        root.addView(btnMonth)

        // ── 注意書き ──────────────────────────────────────
        root.addView(android.widget.TextView(ctx).apply {
            text = "🔒 Google Play で安全に決済　|　いつでも解約できます"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.parseColor("#9E9E9E"))
        })

        val dlg = AlertDialog.Builder(ctx)
            .setView(root)
            .setNegativeButton("閉じる") { _, _ -> onClose?.invoke() }
            .setCancelable(cancelable)
            .create()

        dlgRef[0] = dlg
        return dlg
    }

    // 旧メソッド: 後方互換のため残す
    internal fun showLicenseExpiredDialog() = showSubscriptionDialog()

    /** 設定タブに移動 */
    fun navigateToSettings() {
        binding.viewPager.setCurrentItem(3, true)
    }

}
