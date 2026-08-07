# One-time (and after-each-change) build of the packaged app the tray launcher
# runs: the visual-runtime jar, the production frontend bundle, and the backend
# bootJar that serves both the API and that bundle.
#
# Usage:  launcher\build-app.ps1

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# Fall back to the bundled tools\jdk-21 when JAVA_HOME is not set.
if (-not $env:JAVA_HOME) {
    $bundled = Get-ChildItem (Join-Path $root '..\tools') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($bundled -and (Test-Path (Join-Path $bundled.FullName 'bin\java.exe'))) {
        $env:JAVA_HOME = $bundled.FullName
        Write-Host "JAVA_HOME not set — using bundled JDK: $env:JAVA_HOME" -ForegroundColor DarkGray
    }
}

try {
    Write-Host 'Building visual-runtime jar...' -ForegroundColor Cyan
    & "$root\gradlew.bat" -p "$root" :visual-runtime:jar --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'visual-runtime jar build failed' }

    Write-Host 'Building frontend bundle (frontend\dist)...' -ForegroundColor Cyan
    Push-Location "$root\frontend"
    try {
        npm install
        if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw 'npm run build failed' }
    } finally {
        Pop-Location
    }

    Write-Host 'Building backend bootJar...' -ForegroundColor Cyan
    & "$root\gradlew.bat" -p "$root" :backend:bootJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'backend bootJar build failed' }


    $jar = Get-ChildItem "$root\backend\build\libs" -Filter 'backend-*.jar' |
        Where-Object { $_.Name -notlike '*-plain.jar' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1

    Write-Host ''
    Write-Host "Build complete. Runnable jar: $($jar.FullName)" -ForegroundColor Green
    Write-Host 'Next: launcher\install-shortcut.ps1 (once), then launch from the icon.' -ForegroundColor Green
    exit 0
} catch {
    Write-Host "BUILD FAILED: $_" -ForegroundColor Red
    exit 1
}
