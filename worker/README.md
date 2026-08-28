# haireel-geo-proxy

HaiReel（軽貨物ドライバー向けAndroidアプリ）向けの Geocoding / Places API プロキシ。

運営(開発者)が保有する Google API キーを Worker 側のみに保持し、試用中/サブスク中のユーザーからのリクエストのみ中継する。Google の生 JSON レスポンスをそのまま透過中継するため、Android 側の `GeocodingClient.kt` のパース処理は変更不要。

## エンドポイント

- `GET /maps/api/geocode/json`
- `GET /maps/api/place/textsearch/json`
- `GET /maps/api/place/nearbysearch/json`

上記以外のパスは 404。クエリパラメータも allowlist（`address`, `latlng`, `location`, `radius`, `query`, `language`, `region`, `bounds`）のみ通過し、`key` はクライアント指定を無視して Worker 側の `GOOGLE_API_KEY` で上書きする。

## 認証

以下のヘッダーが必須（`src/auth.ts` 参照）:

| ヘッダー | 内容 |
|---|---|
| `X-HaiReel-Device-Id` | Android側で生成するデバイスUUID |
| `X-HaiReel-Timestamp` | UNIX秒（±5分以内） |
| `X-HaiReel-Entitled` | `"1"` または `"0"`（試用中/サブスク中かどうかの自己申告） |
| `X-HaiReel-Signature` | `HMAC-SHA256(deviceId\|timestamp\|entitled, PROXY_CLIENT_SECRET)` の16進文字列 |
| `X-HaiReel-Package` | アプリのパッケージ名（allowlist: `ALLOWED_PACKAGES`） |

`X-HaiReel-Entitled` はクライアントのローカル判定を送るだけで、Worker 側では Google Play の購入をサーバー検証していない（改造APKによる偽装は理論上可能）。最終的な悪用対策は Google Cloud Console 側の日次クォータに委ねる。サーバーサイド購入検証は将来タスク。

## レート制限

デバイスIDごとに1日 `DAILY_QUOTA_PER_DEVICE`（既定300）件まで。KVの結果整合性により同時リクエスト時に多少のカウント漏れが起こりうるが、想定利用量では実害は軽微。

## セットアップ

```powershell
cd C:\HaiReel\worker
npm install

npx wrangler login
npx wrangler kv namespace create RATE_LIMIT_KV
# 出力された id を wrangler.toml の kv_namespaces.id に反映

npx wrangler secret put GOOGLE_API_KEY
npx wrangler secret put PROXY_CLIENT_SECRET

npx wrangler deploy
```

ローカル動作確認:

```powershell
copy .dev.vars.example .dev.vars
# .dev.vars を編集してテスト用の値を入れる
npx wrangler dev
```

## Google Cloud Console 側で行うこと（このリポジトリの外の作業）

- Geocoding API / Places API 専用の新規APIキーを発行し、上記 `GOOGLE_API_KEY` に設定する（既存の共有キーとは分離する）
- そのキーに API 制限（Geocoding API + Places API のみ）と、悪用時の被害を抑えるための日次クォータ上限を設定する
