# 안드로이드 APK 빌드. PowerShell 에서 실행한다.
#   .\scripts\build-android.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Write-Host "1/2  브리핑 데이터 갱신 (data -> 앱 assets)" -ForegroundColor Cyan
python "$root\scripts\build.py"

Write-Host "2/2  APK 빌드" -ForegroundColor Cyan
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Push-Location "$root\android-app"
try {
    .\gradlew.bat :app:assembleDebug --console=plain
} finally {
    Pop-Location
}

$apk = "$root\android-app\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    $size = [math]::Round((Get-Item $apk).Length / 1MB, 1)
    Write-Host ""
    Write-Host "완료: $apk ($size MB)" -ForegroundColor Green
    Write-Host "이 파일을 휴대폰으로 옮겨 설치하세요."
}
