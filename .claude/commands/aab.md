Play Store 提出用の署名済み AAB（Android App Bundle）をビルドする。

```powershell
Set-Location "C:\Users\rodge\Desktop\Routist"
.\gradlew.bat bundleRelease 2>&1 | Select-String -Pattern "BUILD|error:|FAILED|signRelease" | Select-Object -Last 10
Get-ChildItem "app\build\outputs\bundle\release\*.aab" | Select-Object Name, @{N='サイズ(MB)';E={[math]::Round($_.Length/1MB,1)}}, LastWriteTime
```

完了後、AAB のパスとファイルサイズを表示する。
Play Console へのアップロード手順も簡単に案内する:
1. https://play.google.com/console にアクセス
2. Routist アプリ → リリース → 新しいリリースを作成
3. app-release.aab をアップロード
