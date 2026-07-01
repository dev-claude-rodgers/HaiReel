package com.rodgers.haireel.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.rodgers.haireel.MainActivity
import com.rodgers.haireel.R
import com.rodgers.haireel.util.AppSettings
import com.rodgers.haireel.util.themeColor
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class OnboardingActivity : AppCompatActivity() {

    private data class Page(
        val emoji: String,
        val title: String,
        val body: String
    )

    private val pages = listOf(
        Page(
            "🚚",
            "HaiReelへようこそ",
            "宅配ドライバーの業務をスマホ一台で完結。\n配達・日報・点呼・収支・荷室をまとめて管理できます。\n\n📦 7日間フル機能を無料体験\n💳 その後は月額¥300 または 年額¥2,980\n⚡ Google Playで安全に決済"
        ),
        Page(
            "📥",
            "まず住所を追加しましょう",
            "配達リストの右上メニューから\n「名前・住所を追加」をタップ。\n\nテキストを貼り付けるか\nファイルから読み込めます。\n\n左上のトグルで\n「すべて / 未完了のみ / 完了のみ」を切り替えられます。"
        ),
        Page(
            "🗺",
            "地図でルートを確認",
            "住所を追加したら地図ボタンをタップ。\n\nピンが表示されたら\n「ルート最適化」で\n現在地から最短順に自動整列できます。"
        ),
        Page(
            "🚛",
            "荷室レイアウトで積載管理",
            "配達タブのトグル（リスト→地図→荷室）で\n荷室画面に切り替えられます。\n\n車内の写真を撮ってピンで\n荷物の位置や状態をメモできます。"
        ),
        Page(
            "🔑",
            "地図にはAPIキーが必要です",
            "設定 → Google APIキー設定から登録できます。\n\nGoogle Cloudで無料取得でき、\n個人利用は無料枠内に収まります。\n\nキーなしでも住所管理・日報・\n点呼・荷室管理は使えます。"
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // オンボーディング済みならMainActivityに直接遷移
        if (AppSettings.isOnboardingDone(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val dp = resources.displayMetrics.density
        val ctx = this

        // ルートレイアウト
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.colorBackground))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ViewPager2
        val pager = ViewPager2(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(pager)

        // インジケーター
        val indicatorRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * dp).toInt()
            )
        }
        root.addView(indicatorRow)

        // ボタンエリア
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), (32 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(btnRow)

        setContentView(root)

        // システムバーのinsets対応
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        // インジケータードット生成
        val dotSize = (8 * dp).toInt()
        val dotMargin = (4 * dp).toInt()
        val dots = (0 until pages.size).map { i ->
            View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).also {
                    it.marginStart = dotMargin; it.marginEnd = dotMargin
                }
                background = ContextCompat.getDrawable(ctx, android.R.drawable.btn_radio)
                alpha = if (i == 0) 1f else 0.3f
            }
        }
        dots.forEach { indicatorRow.addView(it) }

        fun updateDots(pos: Int) {
            dots.forEachIndexed { i, dot -> dot.alpha = if (i == pos) 1f else 0.3f }
        }

        // スキップボタン
        val btnSkip = com.google.android.material.button.MaterialButton(ctx,
            null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "スキップ"
            setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isAllCaps = false
        }
        btnRow.addView(btnSkip)

        // 次へ/始めるボタン
        val btnNext = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "次へ"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isAllCaps = false
        }
        btnRow.addView(btnNext)

        // ViewPagerアダプター
        pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class PageHolder(val v: View) : RecyclerView.ViewHolder(v)

            override fun getItemCount() = pages.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val pageRoot = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding((40 * dp).toInt(), (30 * dp).toInt(), (40 * dp).toInt(), (20 * dp).toInt())
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                return PageHolder(pageRoot)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val p = pages[position]
                val pageRoot = holder.itemView as LinearLayout
                pageRoot.removeAllViews()

                // 絵文字
                pageRoot.addView(TextView(ctx).apply {
                    text = p.emoji; textSize = 72f
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = (32 * dp).toInt() }
                })
                // タイトル
                pageRoot.addView(TextView(ctx).apply {
                    text = p.title; textSize = 24f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurface))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.bottomMargin = (20 * dp).toInt() }
                })
                // 本文
                pageRoot.addView(TextView(ctx).apply {
                    text = p.body; textSize = 16f
                    gravity = android.view.Gravity.CENTER
                    setLineSpacing(0f, 1.6f)
                    setTextColor(ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                btnNext.text = if (position == pages.size - 1) "始める" else "次へ"
            }
        })

        fun completeOnboarding() {
            AppSettings.setOnboardingDone(this@OnboardingActivity)
            AppSettings.setTermsAgreed(this@OnboardingActivity)
            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            this@OnboardingActivity.finish()
        }

        fun showTermsAndStart() {
            val onSurface = ctx.themeColor(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVar = ctx.themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

            val termsText = android.widget.TextView(ctx).apply {
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
                setTextColor(onSurface)
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            }

            val divider = android.view.View(ctx).apply {
                setBackgroundColor(ctx.themeColor(com.google.android.material.R.attr.colorOutlineVariant))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ).also { it.topMargin = (8 * dp).toInt() }
            }

            val cb = android.widget.CheckBox(ctx).apply {
                text = "上記の利用規約に同意します"
                textSize = 14f
                setTextColor(onSurface)
                setPadding((4 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            }

            val scrollContent = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(termsText)
                addView(divider)
                addView(cb)
            }
            val scrollView = android.widget.ScrollView(ctx).apply {
                addView(scrollContent)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (400 * dp).toInt()
                )
            }

            val btnCancel = com.google.android.material.button.MaterialButton(
                ctx, null, com.google.android.material.R.attr.borderlessButtonStyle
            ).apply {
                text = "キャンセル"
                setOnClickListener { /* dismiss後に設定 */ }
            }
            val btnAgree = com.google.android.material.button.MaterialButton(ctx).apply {
                text = "同意して始める"
            }
            val btnRow = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.END
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                addView(btnCancel)
                addView(btnAgree)
            }

            val container = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(scrollView)
                addView(btnRow)
            }

            val dlg = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("利用規約")
                .setView(container)
                .create()
            dlg.show()
            btnCancel.setOnClickListener { dlg.dismiss() }
            btnAgree.setOnClickListener {
                if (!cb.isChecked) {
                    android.widget.Toast.makeText(ctx, "利用規約に同意してください", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dlg.dismiss()
                completeOnboarding()
            }
        }

        btnNext.setOnClickListener {
            if (pager.currentItem < pages.size - 1) {
                pager.currentItem++
            } else {
                showTermsAndStart()
            }
        }
        btnSkip.setOnClickListener { showTermsAndStart() }
    }
}
