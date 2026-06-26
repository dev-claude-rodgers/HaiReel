# HaiReel - CLAUDE.md

## 🏷️ タグルール（自動表示・必須）

### 社長が使うタグ（入力）

| タグ | Claudeの動き |
|---|---|
| ＜指示＞ | 統括部→各部門が実行する |
| ＜調査＞ | 調査部門がNLMで調べてObsidianに保存・各部門に共有 |
| ＜確認＞ | Obsidianを読んで現状を報告するだけ（実行なし）|
| ＜承認＞ | 統括部が待機中の操作を実行する |
| ＜却下＞ | 待機中の操作をキャンセル・理由を記録 |
| ＜緊急＞ | 最優先で即対応する |
| ＜相談＞ | 実行せず意見・提案・選択肢を返す |
| ＜中止＞ | 進行中の作業を止めて現状を報告 |

### Claudeが自動表示するタグ（出力・必須）

返答の冒頭に必ず「【担当部門】＜タグ＞」を表示する:

| 状況 | 表示するタグ |
|---|---|
| 実行前に社長確認が必要なとき | 【統括部】＜承認待ち＞ |
| 作業が完了したとき | 【統括部】＜完了報告＞ |
| コードを書いているとき | 【実装チーム】＜作業中＞ |
| NLMで調査しているとき | 【調査部門】＜調査中＞ |
| 調査結果を報告するとき | 【調査部門】＜調査結果＞ |
| 仕様・設計を検討するとき | 【設計チーム】＜設計中＞ |
| テスト・動作確認するとき | 【品質チーム】＜テスト中＞ |
| コミット・プッシュ前のとき | 【リリースチーム】＜確認待ち＞ |
| マーケ施策を提案するとき | 【マーケ部門】＜提案＞ |
| 現状を報告するだけのとき | 【統括部】＜報告＞ |
| 部門間で情報を共有するとき | 【統括部】＜情報共有＞ |

タグ規則の詳細: `C:\second-brain\second-brain\RODGERS\統括部\統括部_タグ規則.md`

## 🚨 統括部の確認ルール（絶対遵守）

以下の操作は **必ず社長に確認を取ってから実行する**。
確認なしに実行してはならない。

| 操作 | 禁止事項 |
|---|---|
| `git commit` | 確認なしのコミット禁止 |
| `git push` | 確認なしのプッシュ禁止 |
| `adb install` | 確認なしのインストール禁止 |
| バックアップ作成 | 確認なしの実行禁止 |
| バックアップ復元 | 確認なしの実行禁止（データ上書き危険）|
| データ初期化 | 確認なしの実行禁止（取り消し不可）|

### 確認のやり方

実行前に以下の形式で社長に確認する:

```
---
【統括部より確認】
操作: [コミット / プッシュ / インストール / バックアップ]
内容: [具体的な変更内容]
影響: [何が変わるか・リスク]
実行してよいですか？（はい / いいえ）
---
```

社長から「はい」「OK」「お願い」などの承認が得られてから実行する。

## 🔑 完全ワークフロールール（最重要）

### 基本原則
1. **Obsidianが唯一の真実** — 作業前は必ずObsidianを読み、作業後は必ず更新する
2. **＜指示＞タグ** — 社長からの公式指示。統括部を通じて各部門・チームが行動する
3. **調査部門はNLMを使う** — 市場調査・競合調査は必ず `nlm query` を使い結果をObsidianに保存し各部門と共有
4. **作業記録の義務** — 作業後は必ず各チームの `*_作業記録.md` に「何をしたか・なぜしたか」を記録する

### ＜指示＞が来たときの処理（詳細）

```
Step 1: 統括部として以下を読む
  - 統括部_指示書.md（過去の指示・優先度）
  - RODGERS_ドキュメント台帳.md（どの部門が担当か確認）
  - 対象部門の*_作業記録.md（現在の状態を把握）

Step 2: 統括部_指示書.md に記録する
  - 指示ID・日時・優先度・内容・対象部門・完了条件

Step 3: 対象部門・チームの作業記録を読んでから行動する
  - 開発部門の場合: 内容に応じて設計→実装→品質→リリースチームに振り分け
  - 各チームの*_作業記録.md を読んでから実装

Step 4: 調査が必要な場合は調査部門に依頼
  - nlm query でNotebookLMを検索
  - 結果を 調査部門_作業記録.md に保存
  - 関係部門の*_作業記録.md の「他部門からの共有情報」に追記

Step 5: 作業完了後にObsidianを更新
  - 各チームの*_作業記録.md に記録
  - 統括部_意思決定ログ.md に決定内容を記録
  - 統括部_指示書.md を【完了】に更新
  - RouteJin_作業ログ.md を更新

Step 6: Gmailで完了報告を送信
  python C:\Users\rodge\週次レポート\rodgers_notify.py --subject "指示完了" --body "内容"
```

### 調査部門のNLMワークフロー

```
調査依頼を受けたら:
  1. nlm query notebook cd689ced-30ff-4fcb-962f-f777fc097841 "質問内容"
  2. 結果を Obsidian の調査部門_作業記録.md に保存
  3. 関係する調査ノートを RouteJin/ フォルダに作成
  4. 関係部門の*_作業記録.md「他部門からの共有情報」に要約を追記
  5. routejin_doc_sync.py で NotebookLM に同期
```

### 部門・チームのObsidianファイル一覧

| 部門/チーム | 読む前に確認するファイル | 作業後に更新するファイル |
|---|---|---|
| 統括部 | 統括部_指示書.md | 統括部_作業記録.md・統括部_意思決定ログ.md |
| 設計チーム | 設計チーム_INDEX.md | 設計チーム_作業記録.md |
| 実装チーム | 実装チーム_INDEX.md・設計チーム_作業記録.md | 実装チーム_作業記録.md |
| 品質チーム | 品質チーム_INDEX.md・実装チーム_作業記録.md | 品質チーム_作業記録.md |
| リリースチーム | リリースチーム_INDEX.md・品質チーム_作業記録.md | リリースチーム_作業記録.md |
| マーケ部門 | マーケ部門_INDEX.md・調査部門_作業記録.md | マーケ部門_作業記録.md |
| 事業部門 | 事業部門_INDEX.md・調査部門_作業記録.md | 事業部門_作業記録.md |
| 調査部門 | 調査部門_INDEX.md | 調査部門_作業記録.md + 関係部門の共有情報欄 |

## ⚡ セッション開始時に必ず読むこと

毎回セッション開始時に以下を読んで現在の状態を把握する:

1. `C:\second-brain\second-brain\RODGERS\統括部\統括部_INDEX.md` — 経営状況・優先課題
2. `C:\second-brain\second-brain\RODGERS\RODGERS_INDEX.md` — 組織全体の入口
3. `C:\second-brain\second-brain\RouteJin\RouteJin_作業ログ.md` — 最新状態・未完了タスク
4. 作業内容に応じて対応部門のINDEXも確認:
   - 統括: `統括部\統括部_INDEX.md`（経営・意思決定）
   - 開発: `開発部門\開発部門_INDEX.md`
   - マーケ: `マーケ部門\マーケ部門_INDEX.md`
   - 事業: `事業部門\事業部門_INDEX.md`
   - 調査: `調査部門\調査部門_INDEX.md`

読んだ後、ユーザーに現在の状態を簡潔に伝えてから作業を開始する。

## 🔴 ＜指示＞ タグの処理ルール（最重要）

メッセージの最初に `＜指示＞` が付いている場合、以下の手順で処理する:

1. **統括部_指示書.md に記録する**
   - `C:\second-brain\second-brain\RODGERS\統括部\統括部_指示書.md`
   - 指示ID・日時・優先度・内容・対象部門・完了条件を記載

2. **対象部門・チームとして実行する**

   **開発部門は内容に応じてチームに振り分ける:**
   - 設計チーム: 新機能の設計・仕様・アーキテクチャ判断
     → `C:\second-brain\second-brain\RODGERS\開発部門\設計チーム\設計チーム_INDEX.md`
   - 実装チーム: コーディング・バグ修正・UI実装・ビルド・インストール
     → `C:\second-brain\second-brain\RODGERS\開発部門\実装チーム\実装チーム_INDEX.md`
   - 品質チーム: テスト作成・品質確認・クラッシュ対応
     → `C:\second-brain\second-brain\RODGERS\開発部門\品質チーム\品質チーム_INDEX.md`
   - リリースチーム: ビルド・Play Store申請・バージョン管理・コミット
     → `C:\second-brain\second-brain\RODGERS\開発部門\リリースチーム\リリースチーム_INDEX.md`

   **複合的な指示の場合は 設計→実装→品質→リリース の順で動く**

   - マーケ部門: コンテンツ作成・SNS戦略
   - 事業部門: Play Store・収益・法務
   - 調査部門: NotebookLMで調査・Obsidianに保存

3. **完了後に統括部_指示書.md を更新する**
   - 【実行待ち】→【完了】に移動
   - 統括部_意思決定ログ.md にも記録

4. **Gmail で結果を報告する**
   ```powershell
   Set-Location "C:\Users\rodge\週次レポート"
   $env:PYTHONUTF8 = "1"
   python rodgers_notify.py --subject "指示完了報告" --body "完了内容..."
   ```

## 📝 作業中のルール

- 重要な変更・決定を行ったら `RouteJin_作業ログ.md` を更新する
- 新機能・設計変更は対応する Obsidian ノートも更新する
- セッション終了前に同期スクリプトを実行:
  ```powershell
  Set-Location "C:\Users\rodge\週次レポート"
  $env:PYTHONUTF8 = "1"
  python routejin_doc_sync.py
  ```

## プロジェクト概要

**アプリ名: HaiReel（ハイリール）**
宅配ドライバー向け業務管理 Android アプリ。
配達ルート管理・日報作成・点呼記録・複数案件対応を一体化。

- パッケージ名: `com.rodgers.routist`（変更なし）
- APK名: RouteJin_yyyyMMdd_HHmmss.apk
- minSdk: 26 / targetSdk: 34 / Kotlin + ViewBinding + Room
- サブスク: 月額¥300 / 年額¥2,980（Google Play IAP）
- 販売業者: RODGERS（屋号）

## 環境

- プロジェクトパス: `C:\Users\rodge\Desktop\RouteJin`
- JDK: `C:\Users\rodge\.jdks\jbr-17.0.14`
- GitHub: https://github.com/proxyroutine777-coder/RouteJin (private)
- GitHub CLI: `C:\Tools\gh\bin\gh.exe`（使用前に `$env:PATH = "C:\Tools\gh\bin;$env:PATH"`）
- ADB: `C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- キーストア: `C:\Users\rodge\routist-release.keystore`（パスは local.properties 参照）

## Git 運用

- `main`: 安定版（ビルド成功・動作確認済み）
- `dev`: 開発用ブランチ（ここで作業する）
- 作業手順: `dev` で開発 → ビルド確認 → `main` にマージ

## ビルド・インストール

```powershell
# デバッグ（開発中）
Set-Location "C:\Users\rodge\Desktop\RouteJin"
.\gradlew.bat assembleDebug
# → app\build\outputs\apk\debug\RouteJin_yyyyMMdd_HHmmss.apk

# リリース（配布・Play Store）
.\build_and_install.ps1

# AAB（Play Store 用）
.\gradlew.bat bundleRelease
# → app\build\outputs\bundle\release\app-release.aab

# ADB インストール
$adb = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb install -r <apk-path>
```

## APIキー設定

`local.properties` に記載（Git に含まない）:
```
sdk.dir=C\:\\Users\\rodge\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=AIza...
RELEASE_STORE_FILE=../routist-release.keystore
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=routist
RELEASE_KEY_PASSWORD=...
```

## DB スキーマ履歴

| バージョン | 変更内容 |
|---|---|
| v4 | 初期スキーマ |
| v5 | work_records に assignmentId 追加 |
| v6 | tenko_records に assignmentId 追加（現在） |

マイグレーション: `MIGRATION_4_5`, `MIGRATION_5_6` を `AppDatabase.kt` に定義済み。
`fallbackToDestructiveMigration()` は v4 未満の安全網として残す。

## アーキテクチャ（4タブ構成）

```
MainActivity
├── DeliveryListFragment（ルートタブ）
├── MapFragment（地図タブ）
├── ReportContainerFragment（日報タブ）
│   └── DailyReportFragment
├── TenkoFragment（点呼タブ）
└── SettingsFragment（設定タブ）

共有 ViewModel（activityViewModels）:
├── DeliveryViewModel  ← グループ・配達リスト・ジオコーディング
├── ReportViewModel    ← 日報レコード（案件フィルタ付き）
└── TenkoViewModel     ← 点呼レコード（案件フィルタ付き）
```

## 主要ファイル

| ファイル | 役割 |
|---|---|
| `MainActivity.kt` | エントリポイント・タブ制御 |
| `ui/DeliveryListFragment.kt` | ルートリスト・インポート・グループ管理 |
| `ui/MapFragment.kt` | 地図・ピン・ルート線・ナビ |
| `ui/DailyReportFragment.kt` | 日報入力・Excel出力・案件別設定 |
| `ui/TenkoFragment.kt` | 点呼記録・案件フィルタ |
| `ui/SettingsFragment.kt` | グローバル設定 |
| `viewmodel/DeliveryViewModel.kt` | グループ・配達リスト・永続化 |
| `viewmodel/ReportViewModel.kt` | 日報 Flow（yearMonth + assignmentId で絞り込み）|
| `viewmodel/TenkoViewModel.kt` | 点呼 Flow（yearMonth + assignmentId で絞り込み）|
| `db/AppDatabase.kt` | Room DB v6・マイグレーション定義 |
| `util/AppSettings.kt` | SharedPreferences（案件別設定対応済み）|
| `excel/ExcelGenerator.kt` | 日報 Excel（XLSX）出力 |
| `excel/TenkoExcelGenerator.kt` | 点呼記録簿 Excel 出力 |

## 案件別設定（複数案件ドライバー対応）

- `DeliveryGroup.patternId` → グループに帳票パターンを紐づけ
- `AppSettings.getPaymentType(ctx, groupId)` → グループ別報酬設定（グローバル値にフォールバック）
- `WorkRecord.assignmentId` / `TenkoRecord.assignmentId` → レコードに案件ID付与
- グループ切り替え → `ReportViewModel.setAssignmentId()` / `TenkoViewModel.setAssignmentId()` を自動呼び出し

## 永続化

- Room DB: WorkRecord・TenkoRecord
- SharedPreferences: グループ・配達リスト・設定値
- Downloads: `RouteJin_グループ名.txt` を自動出力
- 写真: `filesDir/camera_photos/`

## ADB 操作

```powershell
$adb = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb shell am start -n "com.rodgers.routist/.MainActivity"
& $adb shell screencap -p /sdcard/screen.png; & $adb pull /sdcard/screen.png .\screen.png
```
