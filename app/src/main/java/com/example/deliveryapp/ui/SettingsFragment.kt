package com.rodgers.routist.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rodgers.routist.R
import com.rodgers.routist.util.AppSettings
import com.rodgers.routist.util.themeColor

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        val WRAP  = LinearLayout.LayoutParams.WRAP_CONTENT

        val colorOnSurface        = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val colorOutlineVariant   = ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant)
        val surfaceColor          = ctx.themeColor(com.google.android.material.R.attr.colorSurface)
        val redColor              = ContextCompat.getColor(ctx, R.color.colorActionRed)

        val ripple = android.util.TypedValue().also {
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surfaceColor)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
        }

        // ヘッダー
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(MATCH, (56 * dp).toInt())
            elevation = 2 * dp
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
            addView(TextView(ctx).apply {
                text = "設定"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(colorOnSurface)
            })
        })

        fun divider() = View(ctx).apply {
            setBackgroundColor(colorOutlineVariant)
            layoutParams = LinearLayout.LayoutParams(MATCH, (1 * dp).toInt())
                .also { it.setMargins((84 * dp).toInt(), (4 * dp).toInt(), 0, (4 * dp).toInt()) }
        }

        fun row(emoji: String, title: String, sub: String, color: Int = colorOnSurface, action: () -> Unit) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(ripple)
                setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            }
            row.addView(TextView(ctx).apply {
                text = emoji; textSize = 28f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams((52 * dp).toInt(), WRAP)
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    .also { it.marginStart = (14 * dp).toInt() }
            }
            col.addView(TextView(ctx).apply {
                text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(color)
            })
            if (sub.isNotBlank()) col.addView(TextView(ctx).apply {
                text = sub; textSize = 14f; setTextColor(colorOnSurfaceVariant)
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
                    .also { it.topMargin = (2 * dp).toInt() }
            })
            row.addView(col)
            row.setOnClickListener { action() }
            root.addView(row)
        }

        row("⚙️", "アプリ設定", "表示・雇用形態・報酬・セキュリティなど") { showAppSettingsDialog() }
        root.addView(divider())
        row("ℹ️", "アプリについて", "バージョン情報・開発者") {
            (activity as? com.rodgers.routist.MainActivity)?.showAboutDialog()
        }
        root.addView(divider())
        row("🚪", "アプリを終了", "アプリを完全に終了する", redColor) { activity?.finishAffinity() }

        return root
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
        val darkGroup    = android.widget.RadioGroup(ctx)
        val rbDarkSystem = android.widget.RadioButton(ctx).apply { text = "システム設定に従う"; id = View.generateViewId() }
        val rbDarkLight  = android.widget.RadioButton(ctx).apply { text = "ライトモード"; id = View.generateViewId() }
        val rbDarkDark   = android.widget.RadioButton(ctx).apply { text = "ダークモード"; id = View.generateViewId() }
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
            setText(vehicles[0])
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle1)
        root.addView(field("車番２"))
        val etVehicle2 = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[1])
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle2)
        root.addView(field("車番３"))
        val etVehicle3 = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(vehicles[2])
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
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
                Toast.makeText(ctx, "設定を保存しました", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }
}
