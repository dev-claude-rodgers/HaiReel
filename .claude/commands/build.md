Gradle でデバッグビルドする。

```powershell
Set-Location C:\Users\rodge\DeliveryApp
.\gradlew assembleRelease 2>&1 | Select-Object -Last 20
```
