リリース APK をビルドして接続中の全実機にインストールし、GitHub にプッシュする。
build_and_install.ps1 を使用する（R8 難読化・コード圧縮有効）。

```powershell
Set-Location "C:\Users\rodge\Desktop\Routist"
.\build_and_install.ps1
```

完了後、生成された APK 名と各デバイスへのインストール結果を日本語でまとめて報告する。
エラーが発生した場合は原因を特定して修正方法を提案する。
