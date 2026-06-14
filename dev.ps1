# Starts the backend (Spring Boot) and the frontend (Vite) for local development.
# Backend: http://localhost:8080   Frontend (open this): http://localhost:5173
#
# Usage:  ./dev.ps1
# On each run it first stops any backend/frontend started by a previous run
# (closes their windows and frees ports 8080/5173), then starts fresh.

$root = $PSScriptRoot
$backendTitle = 'InterviewDev-Backend'
$frontendTitle = 'InterviewDev-Frontend'

# --- Cleanup: stop anything a previous ./dev.ps1 run left behind ----------

function Stop-TaggedWindows([string]$title) {
    # The spawned windows set their own title; closing the owner PowerShell
    # process closes the window and the gradle/node tree underneath it.
    Get-Process powershell -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -eq $title } |
        ForEach-Object {
            Write-Host "Stopping window '$title' (PID $($_.Id))..." -ForegroundColor DarkGray
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        }
}

function Stop-PortOwners([int]$port) {
    # Kill whatever still listens on the port (java/node detached from the
    # window) so the new run can bind it.
    try {
        $owners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique
    } catch {
        $owners = @()
    }
    foreach ($procId in $owners) {
        if ($procId -and $procId -ne 0) {
            Write-Host "Freeing port $port (PID $procId)..." -ForegroundColor DarkGray
            Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "Stopping any previous dev processes..." -ForegroundColor Cyan
Stop-TaggedWindows $backendTitle
Stop-TaggedWindows $frontendTitle
Stop-PortOwners 8080
Stop-PortOwners 5173

# --- Build & start --------------------------------------------------------

Write-Host "Building visual-runtime jar (needed by the code runner)..." -ForegroundColor Cyan
& "$root\gradlew.bat" :visual-runtime:jar --console=plain
if (-not $?) { Write-Host "Failed to build visual-runtime jar" -ForegroundColor Red; exit 1 }

Write-Host "Starting backend in a new window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = '$backendTitle'; Set-Location '$root'; & '$root\gradlew.bat' :backend:bootRun --console=plain"
)

Write-Host "Starting frontend in a new window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = '$frontendTitle'; Set-Location '$root\frontend'; npm install; npm run dev"
)

Write-Host ""
Write-Host "Open http://localhost:5173 once both windows finish starting." -ForegroundColor Green
