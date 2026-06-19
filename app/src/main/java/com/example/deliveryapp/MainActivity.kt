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
import com.rodgers.routist.util.themeColor
import com.rodgers.routist.viewmodel.DeliveryViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: DeliveryViewModel by viewModels()
    private var suppressNavSync = false
    private var isDriverMode = true

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
        if (!AppSettings.isAppModeSet(this) || AppSettings.isShowModeOnLaunch(this)) {
            showModeSelectionDialog()
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
                        "住所を特定できなかった件数: ${count}件",
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
                    .setMessage("インポートしたファイルの内容がリストと異なります。\n元のファイルを最新の状態に更新しますか？")
                    .setPositiveButton("上書き") { _, _ -> viewModel.confirmOverwrite() }
                    .setNegativeButton("キャンセル") { _, _ -> viewModel.cancelOverwrite() }
                    .show()
            }
        }
    }

    private fun showModeSelectionDialog() {
        val isFirstTime = !AppSettings.isAppModeSet(this)
        val dp = resources.displayMetrics.density
        val ctx = this

        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((24*dp).toInt(), (8*dp).toInt(), (24*dp).toInt(), (16*dp).toInt())
        }

        var selectedMode = AppSettings.isDriverMode(ctx)

        fun makeCard(emoji: String, title: String, sub: String, isDriver: Boolean): android.widget.LinearLayout {
            val surfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val primary        = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
            val onSurface      = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVar   = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            val MATCH = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            val WRAP  = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isDriver == selectedMode) primary else surfaceVariant)
                cornerRadius = 12 * dp
            }
            val card = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                background = bg
                setPadding((16*dp).toInt(), (14*dp).toInt(), (16*dp).toInt(), (14*dp).toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.bottomMargin = (10*dp).toInt() }
            }
            card.addView(android.widget.TextView(ctx).apply {
                text = "$emoji  $title"
                textSize = 16f
                android.graphics.Typeface.DEFAULT_BOLD.also { typeface = it }
                setTextColor(if (isDriver == selectedMode) android.graphics.Color.WHITE else onSurface)
            })
            card.addView(android.widget.TextView(ctx).apply {
                text = sub
                textSize = 13f
                setTextColor(if (isDriver == selectedMode) android.graphics.Color.argb(200, 255, 255, 255) else onSurfaceVar)
                layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (2*dp).toInt() }
            })
            return card
        }

        val driverCard  = makeCard("🚚", "配達・ドライバー向け", "点呼記録・稼働報告書つき", true)
        val generalCard = makeCard("📍", "一般利用", "目的地管理・ルート計画", false)

        root.addView(driverCard)
        root.addView(generalCard)

        val divider = android.view.View(ctx).apply {
            setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1*dp).toInt()
            ).also { it.topMargin = (4*dp).toInt(); it.bottomMargin = (12*dp).toInt() }
        }
        root.addView(divider)

        val checkBox = android.widget.CheckBox(ctx).apply {
            text = "次回起動時もこの画面を表示する"
            isChecked = AppSettings.isShowModeOnLaunch(ctx)
            textSize = 14f
        }
        root.addView(checkBox)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("利用モードを選択")
            .setView(root)
            .setCancelable(!isFirstTime)
            .setPositiveButton("決定") { _, _ ->
                AppSettings.setDriverMode(ctx, selectedMode)
                AppSettings.setShowModeOnLaunch(ctx, checkBox.isChecked)
                setupTabs()
            }
            .apply {
                if (!isFirstTime) setNegativeButton("キャンセル", null)
            }
            .create()

        driverCard.setOnClickListener {
            selectedMode = true
            val primary      = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
            val surfaceVar   = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            (driverCard.background  as android.graphics.drawable.GradientDrawable).setColor(primary)
            (generalCard.background as android.graphics.drawable.GradientDrawable).setColor(surfaceVar)
            (driverCard.getChildAt(0)  as android.widget.TextView).setTextColor(android.graphics.Color.WHITE)
            (driverCard.getChildAt(1)  as android.widget.TextView).setTextColor(android.graphics.Color.argb(200,255,255,255))
            (generalCard.getChildAt(0) as android.widget.TextView).setTextColor(onSurface)
            (generalCard.getChildAt(1) as android.widget.TextView).setTextColor(onSurfaceVar)
        }
        generalCard.setOnClickListener {
            selectedMode = false
            val primary      = ctx.themeColor(com.google.android.material.R.attr.colorPrimary)
            val surfaceVar   = ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant)
            val onSurface    = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
            (generalCard.background as android.graphics.drawable.GradientDrawable).setColor(primary)
            (driverCard.background  as android.graphics.drawable.GradientDrawable).setColor(surfaceVar)
            (generalCard.getChildAt(0) as android.widget.TextView).setTextColor(android.graphics.Color.WHITE)
            (generalCard.getChildAt(1) as android.widget.TextView).setTextColor(android.graphics.Color.argb(200,255,255,255))
            (driverCard.getChildAt(0)  as android.widget.TextView).setTextColor(onSurface)
            (driverCard.getChildAt(1)  as android.widget.TextView).setTextColor(onSurfaceVar)
        }

        dialog.show()
    }

    private fun setupTabs() {
        isDriverMode = AppSettings.isDriverMode(this)

        val fragments = if (isDriverMode) {
            listOf(
                TenkoFragment(),
                DeliveryListFragment(),
                com.rodgers.routist.ui.ReportContainerFragment(),
                com.rodgers.routist.ui.SettingsFragment()
            )
        } else {
            listOf(
                DeliveryListFragment(),
                com.rodgers.routist.ui.SettingsFragment()
            )
        }

        binding.viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        binding.viewPager.isUserInputEnabled = false

        // モード別タブ表示切替・ラベル変更
        binding.bottomNavigation.menu.findItem(R.id.nav_tenko).isVisible = isDriverMode
        binding.bottomNavigation.menu.findItem(R.id.nav_report).isVisible = isDriverMode
        if (!isDriverMode) {
            binding.bottomNavigation.menu.findItem(R.id.nav_list).title = "目的地"
        }

        // 初期タブをリスト画面に
        val listPos = if (isDriverMode) 1 else 0
        binding.viewPager.setCurrentItem(listPos, false)
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
                if (position == if (isDriverMode) 1 else 0) viewModel.setMapFilter(null)
                updateToolbarForTab(position)
            }
        })

        updateToolbarForTab(listPos)
    }

    private fun navItemToPos(itemId: Int): Int? = when (itemId) {
        R.id.nav_tenko    -> if (isDriverMode) 0 else null
        R.id.nav_list     -> if (isDriverMode) 1 else 0
        R.id.nav_report   -> if (isDriverMode) 2 else null
        R.id.nav_settings -> if (isDriverMode) 3 else 1
        else -> null
    }

    private fun posToNavItemId(pos: Int): Int? = when (pos) {
        0 -> if (isDriverMode) R.id.nav_tenko    else R.id.nav_list
        1 -> if (isDriverMode) R.id.nav_list     else R.id.nav_settings
        2 -> if (isDriverMode) R.id.nav_report   else null
        3 -> if (isDriverMode) R.id.nav_settings else null
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
        val listPos = if (isDriverMode) 1 else 0
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

    fun switchToList()   { binding.viewPager.currentItem = if (isDriverMode) 1 else 0 }
    fun switchToTenko()  { if (isDriverMode) binding.viewPager.currentItem = 0 }
    fun switchToReport() { if (isDriverMode) binding.viewPager.currentItem = 2 }

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
            .setTitle("RouteJin のロック解除")
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
            .setTitle("RouteJin")
            .setMessage(
                "バージョン: $version\n\n" +
                "開発者: imai kenichi\n\n" +
                "© 2026 RODGERS\n" +
                "All rights reserved.\n\n" +
                "地図データ: © Google Maps\n" +
                "ジオコーディング: Google Geocoding API"
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
