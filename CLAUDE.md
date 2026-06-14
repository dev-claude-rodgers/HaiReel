# Routist - CLAUDE.md

## プロジェクト概要
配達ルート管理Androidアプリ。最大200件の配達先を地図にピン表示し、
ルート最適化・完了チェック・ナビ連携を行う。

- パッケージ名: `com.rodgers.routist`
- APK名: Routist_yyyyMMdd_HHmmss.apk
- minSdk: 26 / targetSdk: 34 / Kotlin + ViewBinding

## 環境
- プロジェクトパス: `C:\Users\rodge\DeliveryApp`
- Java: `C:\Program Files\Android\Android Studio1\jbr`
- GitHub: https://github.com/proxyroutine777-coder/DeliveryApp (private)
- GitHub CLI: `C:\Tools\gh\bin\gh.exe`（PATH未登録、毎回 `$env:PATH = "C:\Tools\gh\bin;$env:PATH"` が必要）
- ADB: `C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- エミュレーター: emulator-5554（Pixel 6 API34）

## Git運用
- `master`: 安定版（ビルド成功・動作確認済み）
- `dev`: 開発用ブランチ（ここで作業する）
- 作業手順: `dev` で開発 → ビルド確認 → `master` にマージ

## ビルド・インストール（必ずこちらを使う）
```powershell
Set-Location "C:\Users\rodge\DeliveryApp"
.\build_and_install.ps1
```
自動で行うこと:
- APKビルド（releases/ にバックアップ）
- 変更があれば Git コミット＆ GitHub プッシュ
- エミュレーター・USB接続端末に自動インストール

`.\gradlew.bat assembleRelease` を直接実行しない。必ず `.\build_and_install.ps1` を使うこと。

## APIキー設定
`local.properties` に記載（Gitに含まない）:
```
sdk.dir=C\:\\Users\\rodge\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=AIza...
```
BuildConfig経由でアクセス: `BuildConfig.MAPS_API_KEY`

## 主要ファイル
| ファイル | 役割 |
|---|---|
| `MainActivity.kt` | エントリポイント・グループ管理・タブ制御 |
| `ui/MapFragment.kt` | 地図・ピン・ルート線・ナビ |
| `ui/DeliveryListFragment.kt` | リスト・フィルター・選択・写真・メモ |
| `ui/InputActivity.kt` | 住所インポート（テキスト/CSV/URL） |
| `viewmodel/DeliveryViewModel.kt` | 全ビジネスロジック・永続化・ジオコーディング |
| `model/Delivery.kt` | 配達データモデル |
| `model/DeliveryGroup.kt` | グループデータモデル |
| `util/RouteOptimizer.kt` | 最近傍法ルート最適化 |
| `util/GeocodingClient.kt` | Google Geocoding API クライアント |
| `util/AddressParser.kt` | テキストから住所をパース |
| `util/MarkerIconFactory.kt` | 地図ピンアイコン生成 |
| `app/build.gradle.kts` | ビルド設定・versionCode |

## アーキテクチャ
```
MainActivity
├── MapFragment（地図タブ）
├── DeliveryListFragment（リストタブ）
└── DeliveryViewModel（activityViewModels・共有）
    ├── _groups: List<DeliveryGroup>
    ├── _deliveries: List<Delivery>（現在グループ）
    ├── _allDeliveries: Map<GroupId, List<Delivery>>（全グループ・地図用）
    └── SharedPreferences（永続化）
```

## データフロー
```
ImportAddresses → AddressParser → Delivery生成 → startGeocoding
→ GeocodingClient（Google API）→ 座標取得 → commitDeliveries
→ SharedPreferences保存 + Downloadsフォルダ出力 + 元ファイル書き戻し
```

## 永続化
- SharedPreferences: グループ・配達データをJSON保存
- Downloads: `Routist_グループ名.txt` を自動出力
- 元ファイル: `file_uri_グループID` で追跡、並び替え後に書き戻し
- 写真: `filesDir/camera_photos/` に保存、URIをDeliveryに紐づけ

## 注意事項
- `setOnMyLocationChangeListener` は非推奨。`@Suppress("DEPRECATION")` で抑制済み
- 現在地は `lastKnownLocation` に保存（MapFragment）
- ルート最適化は最近傍法（O(n²)）。200件でも実用速度
- ジオコーディング: Google Geocoding API（APIキー必須）

## ADB操作
```powershell
$adb = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices                          # デバイス確認
& $adb shell am start -n "com.rodgers.routist/.MainActivity"  # アプリ起動
& $adb shell screencap -p /sdcard/screen.png; & $adb pull /sdcard/screen.png ".\screen.png"  # スクリーンショット
```
