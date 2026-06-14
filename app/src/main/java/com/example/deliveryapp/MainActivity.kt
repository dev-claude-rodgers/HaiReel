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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rodgers.routist.databinding.ActivityMainBinding
import com.rodgers.routist.ui.DailyReportFragment
import com.rodgers.routist.ui.DeliveryListFragment
import com.rodgers.routist.ui.MapFragment
import com.rodgers.routist.ui.TenkoFragment
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.viewmodel.DeliveryViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: DeliveryViewModel by viewModels()
    private var suppressNavSync = false

    private val reportContainer = com.rodgers.routist.ui.ReportContainerFragment()

    private var isAuthenticated = false
    private var isPromptShowing = false
    private var backgroundedAtMs = 0L
    private val lockHandler = Handler(Looper.getMainLooper())
    private val lockRunnable = Runnable { triggerInactivityLock() }
    private lateinit var lockOverlay: View

    // タブ順序: リスト=0, 地図=1, 日報=2, 点呼=3
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppSettings.getDarkMode(this))
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lockOverlay = View(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        (binding.root as ViewGroup).addView(lockOverlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lockOverlay.visibility = if (AppSettings.isAppLockEnabled(this)) View.VISIBLE else View.GONE

        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        binding.toolbar.setOnClickListener { showGroupDropdown() }

        val fragments = listOf(DeliveryListFragment(), MapFragment(), reportContainer, com.rodgers.routist.ui.SettingsFragment())
        binding.viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]
        }
        binding.viewPager.isUserInputEnabled = false

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (!suppressNavSync) {
                val pos = when (item.itemId) {
                    R.id.nav_list     -> 0
                    R.id.nav_map      -> 1
                    R.id.nav_report   -> 2
                    R.id.nav_settings -> 3
                    else -> return@setOnItemSelectedListener false
                }
                binding.viewPager.setCurrentItem(pos, false)
            }
            true
        }

        updateToolbarForTab(binding.viewPager.currentItem)

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = when (position) {
                    0 -> R.id.nav_list
                    1 -> R.id.nav_map
                    2 -> R.id.nav_report
                    3 -> R.id.nav_settings
                    else -> return
                }
                suppressNavSync = true
                binding.bottomNavigation.selectedItemId = itemId
                suppressNavSync = false
                when (position) {
                    0 -> viewModel.setMapFilter(null)
                }
                updateToolbarForTab(position)
            }
        })

        viewModel.deliveries.observe(this) { _ ->
            val groupName = viewModel.currentGroup()?.name ?: "マップリスト"
            val pos = binding.viewPager.currentItem
            if (pos == 0 || pos == 1) {
                supportActionBar?.title = "$groupName ▼"
            }
            binding.progressBar.visibility = View.GONE
        }

        viewModel.currentGroupId.observe(this) {
            val pos = binding.viewPager.currentItem
            if (pos == 0 || pos == 1) {
                val groupName = viewModel.currentGroup()?.name ?: "マップリスト"
                supportActionBar?.title = "$groupName ▼"
            }
        }

        viewModel.geocodingProgress.observe(this) { progress ->
            if (progress.isRunning) {
                supportActionBar?.subtitle = "処理中… ${progress.current}/${progress.total}"
            } else {
                supportActionBar?.subtitle = null
                if (progress.total > 0) {
                    android.widget.Toast.makeText(
                        this, "${progress.total}件の住所を地図に配置しました", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        viewModel.errorMessage.observe(this) { msg ->
            if (!msg.isNullOrBlank()) {
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.pendingOverwrite.observe(this) { confirmation ->
            if (confirmation == null) return@observe
            AlertDialog.Builder(this)
                .setTitle("元ファイルを上書きしますか？")
                .setMessage("インポートしたファイルの内容がリストと異なります。\n元のファイルを最新の状態に更新しますか？")
                .setPositiveButton("上書き") { _, _ -> viewModel.confirmOverwrite() }
                .setNegativeButton("キャンセル") { _, _ -> viewModel.cancelOverwrite() }
                .show()
        }
    }

    private fun showGroupDropdown() {
        val groups = viewModel.groups.value ?: return
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
        when (position) {
            0, 1 -> {
                binding.appBarLayout.visibility = View.VISIBLE
                val groupName = viewModel.currentGroup()?.name ?: "マップリスト"
                supportActionBar?.title = "$groupName ▼"
                supportActionBar?.subtitle = null
            }
            2, 3 -> {
                // 日報・点呼は独自ヘッダーを持つためツールバー不要
                binding.appBarLayout.visibility = View.GONE
            }
        }
    }

    fun switchToList()   { binding.viewPager.currentItem = 0 }
    fun switchToMap()    { binding.viewPager.currentItem = 1 }
    fun switchToReport() { binding.viewPager.currentItem = 2; binding.root.post { reportContainer.switchToReport() } }
    fun switchToTenko()  { binding.viewPager.currentItem = 2; binding.root.post { reportContainer.switchToTenko() } }

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
            .setTitle("Routist のロック解除")
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
                    finishAffinity()
                }
                override fun onAuthenticationFailed() {}
            }
        ).authenticate(info)
    }

    internal fun showAboutDialog() {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val version = packageInfo.versionName
        AlertDialog.Builder(this)
            .setTitle("Routist")
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
