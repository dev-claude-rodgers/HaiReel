# MapList - 端末インストールスクリプト
# 使い方: PowerShell で .\install_on_device.ps1 を実行

$adb      = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$apkDir   = "C:\Users\rodge\DeliveryApp\releases"

Write-Host "=== MapList インストーラー ===" -ForegroundColor Cyan
Write-Host ""

# 最新APKを取得
$apk = Get-ChildItem $apkDir -Filter "*.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) {
    Write-Host "APKが見つかりません: $apkDir" -ForegroundColor Red
    Write-Host "先に build_and_install.ps1 を実行してください。" -ForegroundColor Yellow
    exit 1
}
Write-Host "APK: $($apk.Name) ($([math]::Round($apk.Length/1MB,1)) MB)" -ForegroundColor Green
Write-Host ""

# 接続デバイス確認
$devices = @((& $adb devices) | Where-Object { $_ -match "^\S+\s+device$" -and $_ -notmatch "^emulator" })

if ($devices.Count -eq 0) {
    Write-Host "Android端末が接続されていません。" -ForegroundColor Red
    Write-Host ""
    Write-Host "【手順】 新しい端末にインストールするには:" -ForegroundColor Cyan
    Write-Host "  1. 端末の「設定」→「開発者向けオプション」を開く"
    Write-Host "     ※開発者向けオプションが見えない場合:"
    Write-Host "       「設定」→「端末情報」→「ビルド番号」を7回タップ"
    Write-Host "  2. 「USBデバッグ」をONにする"
    Write-Host "  3. USBケーブルでPCに接続"
    Write-Host "  4. 端末に「USBデバッグを許可しますか？」が出たら「許可」"
    Write-Host "  5. このスクリプトを再実行"
    Write-Host ""
    Write-Host "【USB不要の場合】 APKファイルを端末に直接転送:"
    Write-Host "  $($apk.FullName)"
    Write-Host "  → LINE/メール/Google Drive などで共有して端末で開く"
    Write-Host "  → 「提供元不明のアプリ」を許可してインストール"
    exit 0
}

Write-Host "接続中の端末:" -ForegroundColor Cyan
$i = 0
$deviceList = @()
foreach ($line in $devices) {
    $serial = ($line -split "\s+")[0]
    $model  = (& $adb -s $serial shell getprop ro.product.model 2>$null).Trim()
    $ver    = (& $adb -s $serial shell getprop ro.build.version.release 2>$null).Trim()
    Write-Host "  [$i] $model  Android $ver  ($serial)"
    $deviceList += [PSCustomObject]@{ Serial = $serial; Model = $model }
    $i++
}

Write-Host ""
if ($deviceList.Count -eq 1) {
    $target = $deviceList
    Write-Host "→ $($target.Model) にインストールします" -ForegroundColor Yellow
} else {
    $choice = Read-Host "インストール先の番号を入力 (全部: A)"
    if ($choice -eq "A" -or $choice -eq "a") {
        $target = $deviceList
    } else {
        $idx = [int]$choice
        if ($idx -lt 0 -or $idx -ge $deviceList.Count) {
            Write-Host "無効な番号です" -ForegroundColor Red
            exit 1
        }
        $target = @($deviceList[$idx])
    }
}

Write-Host ""
foreach ($dev in $target) {
    Write-Host "インストール中: $($dev.Model) ..." -NoNewline
    $result = & $adb -s $dev.Serial install -r $apk.FullName 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host " 完了" -ForegroundColor Green
    } else {
        Write-Host " 失敗" -ForegroundColor Red
        Write-Host $result
    }
}

Write-Host ""
Write-Host "=== 完了 ===" -ForegroundColor Cyan
