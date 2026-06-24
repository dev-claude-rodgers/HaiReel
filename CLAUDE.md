# RouteJin - CLAUDE.md

## プロジェクト概要

宅配ドライバー向け業務管理 Android アプリ。
配達ルート管理・日報作成・点呼記録・複数案件対応を一体化。

- パッケージ名: `com.rodgers.routist`
- APK名: RouteJin_yyyyMMdd_HHmmss.apk
- minSdk: 26 / targetSdk: 34 / Kotlin + ViewBinding + Room

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
