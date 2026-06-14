最新のデバッグ APK を接続中の実機にインストールする。

```powershell
$adb = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apk = Get-ChildItem "C:\Users\rodge\Desktop\Routist\app\build\outputs\apk\debug\*.apk" |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { Write-Host "APK が見つかりません。先に /build を実行してください。"; exit 1 }
Write-Host "インストール中: $($apk.Name)"
& $adb install -r $apk.FullName
```

APK が存在しない場合は「先に /build を実行してください」と案内する。
インストール成功後は「インストール完了: <ファイル名>」と表示する。
