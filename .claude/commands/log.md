接続中の実機から Routist のログ（クラッシュ・エラー・警告）を取得して表示する。

```powershell
$adb = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
# 直近のログをクリアしてから取得（5秒間）
& $adb logcat -c
Start-Sleep -Seconds 1
$job = Start-Job { & "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat -v time *:W 2>&1 }
Start-Sleep -Seconds 5
Stop-Job $job
$output = Receive-Job $job
Remove-Job $job
$output | Select-String -Pattern "com\.rodgers\.routist|AndroidRuntime|FATAL|Exception|Error" | Select-Object -Last 50
```

取得したログを分析して：
- クラッシュ（FATAL EXCEPTION）があれば原因となるスタックトレースを日本語で説明する
- エラーや警告があれば重要度順に整理して報告する
- 問題がなければ「ログに異常なし」と伝える

引数 `$ARGUMENTS` が指定された場合はそのキーワードでさらに絞り込む。
