package com.rodgers.haireel.ui

import android.content.Context
import android.graphics.Color
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun showTermsDialog(ctx: Context) {
    textScrollDialog(ctx, "利用規約", """利用規約
最終更新日：2026年7月16日

第1条（目的）
本規約は、HaiReel（以下「本アプリ」）の利用条件を定めるものです。
本アプリをご利用になる前に、必ず本規約をお読みください。
ご利用をもって本規約に同意したものとみなします。

第2条（利用対象）
本アプリは、軽貨物・宅配ドライバーおよびその業務に関わる方を対象とした業務管理ツールです。
業務目的以外での使用を禁止します。

第3条（知的財産権）
本アプリのデザイン・UI・機能・画面構成・ロゴ・文言・アイコンおよびソースコードに関するすべての知的財産権は開発者（RODGERS）に帰属します。
本アプリを参考・模倣した類似アプリの作成・配布・販売を禁止します。

第4条（禁止事項）
利用者は以下の行為を行ってはなりません。
・本アプリの逆コンパイル・逆アセンブル・リバースエンジニアリング
・本アプリのデザインや機能を模倣した類似アプリの制作・配布・販売
・サブスクリプションの第三者への譲渡・アカウントの共有・転売
・違法または公序良俗に反する目的での使用
・本アプリを利用した第三者への迷惑行為

第5条（免責事項）
・本アプリはあくまで補助ツールです。点呼記録・日報の法的効力を保証しません。
・ルート最適化・到着予定時刻（ETA）の結果は参考情報であり、精度・正確性を保証しません。
・配達先台帳・メモ等のデータの消失・破損について開発者は責任を負いません。
・本アプリの利用により生じた損害について、開発者の故意または重大な過失による場合を除き、一切の責任を負いません。
・運転中の本アプリ操作は道路交通法に違反する場合があります。走行中は必ず安全な場所に停車してからご使用ください。

第6条（サービスの変更・停止）
開発者は、事前の通知なく本アプリの機能変更・アップデート・サービスの一時停止または終了を行う場合があります。
これによりユーザーに生じた損害について、開発者は責任を負いません。

第7条（料金・サブスクリプション）
試用期間（7日間）は無料でご利用いただけます。
継続利用にはGoogle Playのサブスクリプション登録が必要です。
・月額プラン：¥300（税込）/月
・年額プラン：¥2,980（税込）/年
自動更新は解約手続きを行わない限り継続されます。
返金についてはGoogle Playの返金ポリシーに準じます。

第8条（準拠法・管轄）
本規約は日本法に準拠します。
本アプリに関する紛争は、開発者所在地を管轄する裁判所を第一審の専属的合意管轄裁判所とします。

第9条（規約の変更）
本規約は事前の通知なく変更される場合があります。
変更後の規約はアプリ内に掲示された時点で効力を生じます。
重要な変更がある場合はアプリ内で通知します。""")
}

fun showSctDialog(ctx: Context) {
    textScrollDialog(ctx, "特定商取引法に基づく表記", """特定商取引法に基づく表記

■ 販売業者
RODGERS

■ 所在地
消費者からの請求があり次第、遅滞なく開示いたします。
（特定商取引法施行規則第11条の2の規定に基づきます）

■ 電話番号
消費者からの請求があり次第、遅滞なく開示いたします。
お問い合わせは下記メールにて受け付けています。

■ お問い合わせ先
dev.claude.rodgers@gmail.com
受付時間：平日 10:00〜18:00
※返信までに数日かかる場合があります。

■ 販売価格
月額プラン：¥300（税込）/月
年額プラン：¥2,980（税込）/年

■ 支払方法
Google Playを通じた決済（クレジットカード・キャリア決済等）

■ 支払時期
サブスクリプション登録時および各更新日に自動決済されます。

■ 提供時期
決済完了後、即時ご利用いただけます。
試用期間（7日間）は登録不要で無料でお使いいただけます。

■ 解約方法
Google Playの定期購入管理からいつでも解約できます。
解約後は次回更新日まで引き続きご利用いただけます。

■ 返金ポリシー
既払い分の返金はGoogle Playの返金ポリシーに準じます。
返金をご希望の場合はGoogle Playのサポートにお問い合わせください。

■ 動作環境
Android 8.0（API 26）以上
一部機能（地図・住所検索）にはGoogle APIキーが必要です。""")
}

fun showPrivacyPolicyDialog(ctx: Context) {
    textScrollDialog(ctx, "プライバシーポリシー", """プライバシーポリシー
最終更新日：2026年7月16日

HaiReel（以下「本アプリ」）は、ユーザーのプライバシーを尊重し、個人情報の適切な保護に努めます。

■ 収集する情報
本アプリが端末内に保存する情報は以下のとおりです。
・配達先情報（氏名・住所・メモ・時間帯・営業時間・滞在時間・写真等）
・配達先台帳データ（過去の配達先履歴）
・日報・点呼記録・収支・給油データ
・帳票パターン設定・署名画像
・位置情報（地図・ルート最適化機能を使用する場合のみ・端末内処理）
・Google APIキー（端末内に暗号化して保存）
・アプリ設定（事業者名・車番・通知設定等）

■ 利用目的
収集した情報は本アプリの機能提供のみに使用します。
第三者への販売・提供は一切行いません。

■ データの保存場所
すべての業務データは端末内にのみ保存されます。
バックアップファイルをエクスポートした場合の管理はユーザー自身の責任となります。

■ 第三者への提供
ユーザーの業務データを第三者に提供・販売することは一切ありません。

■ Google APIの利用
住所検索・地図機能においてGoogle Geocoding API・Places API・Maps SDK for Androidを使用します。
住所検索時に入力された住所データはGoogleのサーバーに送信されます。
位置情報は地図・ルート最適化機能のためGoogleのサービスに送信される場合があります。
これらのデータはGoogleのプライバシーポリシー（https://policies.google.com/privacy）に従って処理されます。

■ Firebase Crashlytics
アプリの安定性向上のためクラッシュレポートをGoogleのサーバーに送信します。
このレポートには個人を特定できる情報は含まれません。

■ Firebase Analytics
アプリ改善のため画面遷移・起動回数などの匿名使用状況をGoogleのサーバーに送信します。
個人を特定できる情報は収集しません。

■ データの削除
アプリをアンインストールするとすべてのデータが端末から削除されます。
ただし端末のバックアップ機能により復元される場合があります。
アプリ内の「データをすべて初期化」からも削除できます。

■ お子様のプライバシー
本アプリは13歳未満を対象としておらず、意図的に13歳未満の情報を収集しません。

■ 本ポリシーの変更
本ポリシーは事前の通知なく変更される場合があります。
重要な変更がある場合はアプリ内で通知します。

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
    item("① 配達タブ右上メニュー →「名前・住所を追加」で住所を入力")
    item("② 地図に切り替え → メニュー「ルート最適化」で現在地から最短順に自動整列")
    item("③ 各行をタップ →「完了にする」で完了マークをつける")
    note("→ チップで「すべて / 未完了のみ / 完了のみ」を切り替えられます")
    item("④ 点呼タブで乗務前後の点呼を記録する")
    item("⑤ 報告タブで日報（収入・走行距離）を記録してExcel出力")

    section("🕒 到着予定時刻（ETA）の使い方")
    item("配達タブメニュー →「出発・滞在設定」で出発時刻を入力すると各配達先に到着予定時刻が表示されます。")
    item("配達先を長押し →「滞在時間を設定」で件ごとに個別の滞在時間を設定できます。")
    note("→ 閉店時刻を過ぎる予定の配達先は赤字で警告されます。")

    section("📋 配達先台帳の使い方")
    item("配達タブメニュー →「配達先台帳」から過去の全配達先を参照できます。")
    item("「＋」ボタンで現在のルートの末尾に追加できます。")
    note("→ 長押しで台帳から削除（元データは残ります）")

    section("🔑 Google APIキーについて")
    item("住所検索・地図機能にGoogle APIキーが必要です。")
    item("設定 →「Google APIキー設定」をタップするとウィザードが開きます。")
    note("→ 毎月200ドル分の無料枠があり、個人利用は通常無料枠内に収まります。")

    section("💡 便利な使い方")
    item("配達先台帳: メニュー →「配達先台帳」で過去の配達先を一覧確認・再利用")
    item("帳票パターン: 取引先ごとに帳票設定（社名・単価・締め日）を切り替えられます")
    item("ウィジェット: ホーム画面にウィジェットを追加して今日の配達件数を確認")
    item("バックアップ: 定期的に設定 → バックアップを作成してください")

    section("❓ よくある質問")
    item("Q. 地図が白くなる")
    note("→ Google APIキーが未設定または無効です。設定 →「Google APIキー設定」を確認してください。")
    item("Q. ルート最適化ができない")
    note("→「⏳ 検索中」が消えてから実行してください。住所がまだ地図に配置されていない場合があります。")
    item("Q. ETAが表示されない")
    note("→「出発・滞在設定」で出発時刻を入力し、「地点間距離」が表示されているか確認してください。")
    item("Q. 収入が表示されない")
    note("→ 報告タブの「収入（円）」欄に金額を入力して保存してください。")
    item("Q. データが消えた")
    note("→ 設定 → バックアップから復元できます。定期的なバックアップをおすすめします。")
    item("Q. サブスクが有効にならない")
    note("→ 設定 → ライセンスをタップしてGoogle Playの購入状況を確認してください。解決しない場合は dev.claude.rodgers@gmail.com へ。")

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
