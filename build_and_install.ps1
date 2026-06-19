# Routist build/backup/distribute script

$env:JAVA_HOME  = "C:\Users\rodge\.jdks\jbr-17.0.14"
$env:PATH       = "$env:JAVA_HOME\bin;C:\Tools\gh\bin;$env:PATH"
$adb            = "C:\Users\rodge\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$projectDir     = "C:\Users\rodge\Desktop\RouteJin"
$releasesDir    = "$projectDir\releases"

Set-Location $projectDir
Write-Host "=== Routist Build Start ===" -ForegroundColor Cyan

# 1. Build (release: R8難読化・コード圧縮有効)
.\gradlew.bat assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build FAILED" -ForegroundColor Red
    exit 1
}

# 2. Get APK
$apk = Get-ChildItem "app\build\outputs\apk\release\RouteJin_*.apk" |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1
Write-Host "APK: $($apk.Name)" -ForegroundColor Green

# 3. Backup to releases/
if (-not (Test-Path $releasesDir)) {
    New-Item -ItemType Directory -Path $releasesDir | Out-Null
}
Copy-Item $apk.FullName -Destination $releasesDir -Force
Write-Host "Backup OK: releases\$($apk.Name)" -ForegroundColor Green

# 古いバックアップを削除（最新1件のみ残す）
Get-ChildItem "$releasesDir\RouteJin_*.apk" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip 1 |
    Remove-Item -Force

# 4. GitHub auto commit & push (only if changes exist)
$changed = git status --porcelain
if ($changed) {
    git add -A
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm"
    git commit -m "auto backup: $ts"
    git push
    Write-Host "GitHub push OK" -ForegroundColor Green
} else {
    Write-Host "No changes - GitHub skipped" -ForegroundColor Yellow
}

# 5. Install on ALL connected real devices (install -r でデータ保持)
$realDevices = @((& $adb devices) | Where-Object { $_ -match "^\S+\s+device$" -and $_ -notmatch "^emulator" })
if ($realDevices.Count -gt 0) {
    foreach ($deviceLine in $realDevices) {
        $serial = ($deviceLine -split "\s+")[0]
        $model  = (& $adb -s $serial shell getprop ro.product.model 2>$null).Trim()
        $result = & $adb -s $serial install -r $apk.FullName 2>&1
        if ($result -match "Success") {
            Write-Host "Install OK: $model ($serial)  ※データ保持" -ForegroundColor Green
        } else {
            Write-Host "Install FAILED: $model ($serial)" -ForegroundColor Red
            Write-Host $result -ForegroundColor Red
        }
    }
} else {
    Write-Host "実機未接続 - USBデバッグを有効にして接続してください" -ForegroundColor Yellow
}

# 6. Install on emulator (if running)
$emulators = @((& $adb devices) | Where-Object { $_ -match "^emulator.*\s+device$" })
if ($emulators.Count -gt 0) {
    $eSerial = ($emulators[0] -split "\s+")[0]
    $result = & $adb -s $eSerial install -r $apk.FullName 2>&1
    if ($result -match "Success") {
        Write-Host "Emulator install OK  ※データ保持" -ForegroundColor Green
    } else {
        Write-Host "Emulator install FAILED" -ForegroundColor Red
        Write-Host $result -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== Done: $($apk.Name) ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "■ 他の端末にインストールする場合:" -ForegroundColor Cyan
Write-Host "  1. APKをLINE/メール/Driveなどで共有: $($apk.FullName)" -ForegroundColor White
Write-Host "  2. または install_on_device.ps1 を実行 (USB接続時)" -ForegroundColor White