# Starts the backend (Spring Boot) and the frontend (Vite) for local development.
# Backend: http://localhost:8080   Frontend (open this): http://localhost:5173
#
# Usage:  ./dev.ps1
# Stop both with Ctrl+C in their windows, or close the spawned windows.

$root = $PSScriptRoot

Write-Host "Building visual-runtime jar (needed by the code runner)..." -ForegroundColor Cyan
& "$root\gradlew.bat" :visual-runtime:jar --console=plain
if (-not $?) { Write-Host "Failed to build visual-runtime jar" -ForegroundColor Red; exit 1 }

Write-Host "Starting backend in a new window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$root'; & '$root\gradlew.bat' :backend:bootRun --console=plain"
)

Write-Host "Starting frontend in a new window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$root\frontend'; npm install; npm run dev"
)

Write-Host ""
Write-Host "Open http://localhost:5173 once both windows finish starting." -ForegroundColor Green
