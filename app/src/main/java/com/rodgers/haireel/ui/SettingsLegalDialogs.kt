package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Color
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showTermsDialog(ctx: Context) {
    textScrollDialog(ctx, "利用規約", """利用規約

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
変更後の規約はアプリ内に掲示された時点で効力を生じます。""")
}

fun showSctDialog(ctx: Context) {
    textScrollDialog(ctx, "特定商取引法に基づく表記", """特定商取引法に基づく表記

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
Android 8.0（API 26）以上""")
}

fun showPrivacyPolicyDialog(ctx: Context) {
    textScrollDialog(ctx, "プライバシーポリシー", """プライバシーポリシー
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
dev.claude.rodgers@gmail.com""")
}

fun showHelpDialog(ctx: Context) {
    val dp           = ctx.resources.displayMetrics.density
    val onSurface    = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    val onSurfaceVar = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.DKGRAY)
    val primary      = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
    val MATCH        = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
    val WRAP         = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT

    val root = android.widget.LinearLayout(ctx).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
    }

    fun section(title: String) = root.addView(TextView(ctx).apply {
        text = title; textSize = 15f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(primary)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = (16 * dp).toInt(); it.bottomMargin = (6 * dp).toInt() }
    })

    fun item(text: String) = root.addView(TextView(ctx).apply {
        this.text = text; textSize = 14f; setTextColor(onSurface)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (6 * dp).toInt() }
    })

    fun note(text: String) = root.addView(TextView(ctx).apply {
        this.text = text; textSize = 12f; setTextColor(onSurfaceVar)
        layoutParams = android.widget.LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.bottomMargin = (4 * dp).toInt() }
    })

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

    MaterialAlertDialogBuilder(ctx)
        .setTitle("❓ 使い方・ヘルプ")
        .setView(ScrollView(ctx).apply { addView(root) })
        .setPositiveButton("閉じる", null)
        .show()
}

private fun textScrollDialog(ctx: Context, title: String, body: String) {
    val dp = ctx.resources.displayMetrics.density
    val tv = TextView(ctx).apply {
        text = body
        textSize = 13f
        setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
        setTextColor(MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
    }
    MaterialAlertDialogBuilder(ctx)
        .setTitle(title)
        .setView(ScrollView(ctx).apply { addView(tv) })
        .setPositiveButton("閉じる", null)
        .show()
}
