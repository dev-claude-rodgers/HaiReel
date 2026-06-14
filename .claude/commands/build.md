デバッグ APK をビルドして、生成されたファイルパスを表示する。

```powershell
Set-Location "C:\Users\rodge\Desktop\Routist"
.\gradlew.bat assembleDebug 2>&1 | Select-String -Pattern "BUILD|error:|FAILED" | Select-Object -Last 10
Get-ChildItem "app\build\outputs\apk\debug\*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Select-Object Name, LastWriteTime
```

ビルドが FAILED の場合はエラー内容を日本語で要約して原因を説明すること。
