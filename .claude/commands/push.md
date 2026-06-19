変更をすべてステージングして GitHub にコミット・プッシュする。

手順:
1. `git status` で変更ファイルを確認する
2. `git diff --stat` で変更内容の概要を把握する
3. 変更内容を分析して、適切な日本語コミットメッセージを作成する
   - 形式: `<種別>: <内容の要約>`
   - 種別: feat（新機能）/ fix（バグ修正）/ refactor（リファクタリング）/ docs（ドキュメント）/ chore（その他）
4. 以下を実行する:

```powershell
Set-Location "C:\Users\rodge\Desktop\RouteJin"
$env:PATH = "C:\Tools\gh\bin;$env:PATH"
git add -A
git commit -m "<自動生成したコミットメッセージ>"
git push
```

引数 `$ARGUMENTS` が指定された場合はそれをコミットメッセージとして使用する。
プッシュ完了後、GitHub URL（https://github.com/proxyroutine777-coder/RouteJin）を表示する。
