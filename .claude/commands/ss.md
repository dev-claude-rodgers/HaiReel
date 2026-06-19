接続中の実機のスクリーンショットを撮影して、プロジェクトの test_screenshots/ に保存する。

```powershell
$adb  = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$name = if ("$ARGUMENTS" -ne "") { "$ARGUMENTS" } else { "ss_$(Get-Date -Format 'yyyyMMdd_HHmmss')" }
$dest = "C:\Users\rodge\Desktop\RouteJin\test_screenshots\${name}.png"
& $adb shell screencap -p /sdcard/screen.png
& $adb pull /sdcard/screen.png $dest
& $adb shell rm /sdcard/screen.png
Write-Host "保存完了: $dest"
```

保存したスクリーンショットを Read ツールで読み込んで表示し、画面の状態を日本語で説明する。

使い方:
- `/ss` → タイムスタンプ付きファイル名で保存
- `/ss login_screen` → test_screenshots/login_screen.png として保存
