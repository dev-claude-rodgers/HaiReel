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
        row("📋", "プライバシーポリシー", "個人情報の取り扱いについて") { showPrivacyPolicyDialog() }
        root.addView(divider())
        row("🚪", "アプリを終了", "アプリを完全に終了する", redColor) { activity?.finishAffinity() }

        return root
    }

    private fun showPrivacyPolicyDialog() {
        if (!isAdded) return
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val tv = TextView(ctx).apply {
            text = """
プライバシーポリシー
最終更新：2026年6月

1. 収集する情報
本アプリ（Routist）は、以下の情報をお客様の端末内にのみ保存します。
・入力された配達先住所・メモ
・日報（稼働日・件数・報酬・走行距離）
・アプリ設定（氏名・単価・事業者情報）

2. 情報の利用目的
収集した情報は、ルート管理・日報作成・Excel出力のためにのみ使用します。

3. 外部サービスの利用
住所の地図表示・ルート案内のため、Google Maps API を使用しています。
入力した住所はGoogle のサーバーに送信される場合があります。
Google のプライバシーポリシーは https://policies.google.com/privacy をご参照ください。

4. 第三者への提供
お客様の情報を開発者または第三者に送信・販売・共有することはありません。
すべてのデータはお客様の端末内にのみ保存されます。

5. データの削除
アプリをアンインストールすることで、端末に保存されたすべてのデータが削除されます。

6. お問い合わせ
本ポリシーに関するご質問は、アプリ内の「アプリについて」よりご連絡ください。
            """.trimIndent()
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            setLineSpacing(0f, 1.4f)
        }
        android.app.AlertDialog.Builder(ctx)
            .setTitle("プライバシーポリシー")
            .setView(ScrollView(ctx).apply {
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                addView(tv)
            })
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

        // ── 雇用形態
        root.addView(section("── 雇用形態"))
        val empGroup     = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.HORIZONTAL }
        val rbContractor = android.widget.RadioButton(ctx).apply { text = "業務委託"; id = View.generateViewId() }
        val rbEmployee   = android.widget.RadioButton(ctx).apply { text = "正社員・パート"; id = View.generateViewId() }
        empGroup.addView(rbContractor); empGroup.addView(rbEmployee)
        if (AppSettings.getEmploymentType(ctx) == "employee") rbEmployee.isChecked = true
        else rbContractor.isChecked = true
        root.addView(empGroup)

        // ── 報酬設定
        root.addView(section("── 報酬設定"))
        root.addView(field("報酬タイプ"))
        val payGroup = android.widget.RadioGroup(ctx).apply { orientation = android.widget.RadioGroup.VERTICAL }
        val rbDaily  = android.widget.RadioButton(ctx).apply { text = "日当制"; id = View.generateViewId() }
        val rbUnit   = android.widget.RadioButton(ctx).apply { text = "件数単価制"; id = View.generateViewId() }
        val rbNone   = android.widget.RadioButton(ctx).apply { text = "なし"; id = View.generateViewId() }
        payGroup.addView(rbDaily); payGroup.addView(rbUnit); payGroup.addView(rbNone)
        when (AppSettings.getPaymentType(ctx)) {
            1    -> rbUnit.isChecked = true
            2    -> rbNone.isChecked = true
            else -> rbDaily.isChecked = true
        }
        root.addView(payGroup)

        val isInvoicedNow = AppSettings.isInvoiceRegistered(ctx)
        val tvUnitLabel   = field(if (isInvoicedNow) "件数単価（税抜）" else "件数単価（円）")
        root.addView(tvUnitLabel)
        val etUnit = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "0"
            val v = AppSettings.getUnitPrice(ctx)
            if (v > 0) setText(v.toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etUnit)
        val tvUnitTax = TextView(ctx).apply {
            textSize = 12f; setTextColor(colorOutline)
            visibility = if (isInvoicedNow) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (2 * dp).toInt() }
        }
        root.addView(tvUnitTax)

        // ── 事業者情報
        root.addView(section("── 事業者情報"))
        root.addView(field("事業者名"))
        val etCompany = EditText(ctx).apply {
            hint = "例: 〇〇運送株式会社"; inputType = InputType.TYPE_CLASS_TEXT
            setText(AppSettings.getCompanyName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etCompany)
        root.addView(field("車番（ナンバー）"))
        val etVehicle = EditText(ctx).apply {
            hint = "例: 品川 100 あ 1234"; inputType = InputType.TYPE_CLASS_TEXT
            setText(AppSettings.getVehicleNumber(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etVehicle)

        // ── 燃料費設定
        root.addView(section("── 燃料費設定"))
        root.addView(field("ガソリン単価（円/L）"))
        val etFuelPrice = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; hint = "170"
            setText(AppSettings.getFuelPricePerLiter(ctx).toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etFuelPrice)
        root.addView(field("燃費（km/L）"))
        val etFuelEff = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "15.0"
            setText(AppSettings.getFuelEfficiencyKmPerL(ctx).toString())
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etFuelEff)

        // ── インボイス設定
        root.addView(section("── インボイス設定"))
        val swInvoice = run {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).also { it.topMargin = (4 * dp).toInt() }
            }
            row.addView(TextView(ctx).apply {
                text = "インボイス（適格請求書）登録済み"; textSize = 15f
                setTextColor(colorOnSurface)
                layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            val sw = androidx.appcompat.widget.SwitchCompat(ctx).apply {
                isChecked = AppSettings.isInvoiceRegistered(ctx)
            }
            row.addView(sw); root.addView(row); sw
        }

        fun updateUnitLabel() {
            val price = etUnit.text.toString().toIntOrNull() ?: 0
            if (swInvoice.isChecked) {
                tvUnitLabel.text = "件数単価（税抜）"
                tvUnitTax.visibility = View.VISIBLE
                tvUnitTax.text = "消費税10%込: ${(price * 1.1).toInt()}円 / 件"
            } else {
                tvUnitLabel.text = "件数単価（円）"
                tvUnitTax.visibility = View.GONE
            }
        }
        swInvoice.setOnCheckedChangeListener { _, _ -> updateUnitLabel() }
        etUnit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = updateUnitLabel()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        updateUnitLabel()

        // ── 点呼設定
        root.addView(section("── 点呼設定"))
        root.addView(field("ドライバー名"))
        val etDriver = EditText(ctx).apply {
            hint = "氏名"; inputType = InputType.TYPE_CLASS_TEXT
            setText(AppSettings.getDriverName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etDriver)
        root.addView(field("確認者（運行管理者）名"))
        val etChecker = EditText(ctx).apply {
            hint = "自己点呼の場合は自分の名前"; inputType = InputType.TYPE_CLASS_TEXT
            setText(AppSettings.getCheckerName(ctx))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
        }
        root.addView(etChecker)

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
                AppSettings.setEmploymentType(ctx, if (rbEmployee.isChecked) "employee" else "contractor")
                AppSettings.setPaymentType(ctx, if (rbUnit.isChecked) 1 else if (rbNone.isChecked) 2 else 0)
                AppSettings.setUnitPrice(ctx, etUnit.text.toString().toIntOrNull() ?: 0)
                AppSettings.setFuelPricePerLiter(ctx, etFuelPrice.text.toString().toIntOrNull() ?: 170)
                AppSettings.setFuelEfficiencyKmPerL(ctx, etFuelEff.text.toString().toFloatOrNull() ?: 15f)
                AppSettings.setInvoiceRegistered(ctx, swInvoice.isChecked)
                AppSettings.setCompanyName(ctx, etCompany.text.toString().trim())
                AppSettings.setVehicleNumber(ctx, etVehicle.text.toString().trim())
                AppSettings.setDriverName(ctx, etDriver.text.toString().trim())
                AppSettings.setCheckerName(ctx, etChecker.text.toString().trim())
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
