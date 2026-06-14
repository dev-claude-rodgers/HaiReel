エミュレーターの ADB logcat を DeliveryApp タグでフィルタして表示する。

```powershell
$adb = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb -s emulator-5554 logcat -s "DeliveryApp:V" AndroidRuntime:E *:S
```
