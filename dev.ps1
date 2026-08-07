# Starts the backend (Spring Boot) and the frontend (Vite) for local development.
# Backend: http://localhost:18080   Frontend (open this): http://localhost:15173
#
# Usage:  ./dev.ps1
# On each run it first stops the backend/frontend started by a previous run —
# closing their windows AND freeing ports 18080/15173 — then starts fresh.
# The ports are deliberately not the 8080/5173 defaults, so other local projects
# can keep those (and so this script never kills them).

$root = $PSScriptRoot
$pidFile = Join-Path $root '.dev-pids'

# --- JDK: fall back to the bundled tools\jdk-21 when JAVA_HOME is not set ---
# (child windows inherit this process environment, so gradlew picks it up too)
if (-not $env:JAVA_HOME) {
    $bundled = Get-ChildItem (Join-Path $root '..\tools') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($bundled -and (Test-Path (Join-Path $bundled.FullName 'bin\java.exe'))) {
        $env:JAVA_HOME = $bundled.FullName
        Write-Host "JAVA_HOME not set — using bundled JDK: $env:JAVA_HOME" -ForegroundColor DarkGray
    }
}

# --- Cleanup: stop anything a previous ./dev.ps1 run left behind ----------

function Stop-Tree([int]$rootPid) {
    # Kill a process and all of its descendants (a spawned PowerShell window plus
    # the gradle/java or npm/node tree under it). Killing the root closes the
    # console window; killing the children stops the dev servers.
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$rootPid" -ErrorAction SilentlyContinue
    foreach ($c in $children) { Stop-Tree ([int]$c.ProcessId) }
    Stop-Process -Id $rootPid -Force -ErrorAction SilentlyContinue
}

function Stop-PreviousWindows {
    if (-not (Test-Path $pidFile)) { return }
    foreach ($line in Get-Content $pidFile -ErrorAction SilentlyContinue) {
        $linePid = 0
        if (-not [int]::TryParse($line.Trim(), [ref]$linePid)) { continue }
        # Only tree-kill if the PID still belongs to a PowerShell host (guards
        # against the OS having reused the PID for an unrelated process).
        $proc = Get-Process -Id $linePid -ErrorAction SilentlyContinue
        if ($proc -and $proc.ProcessName -in @('powershell', 'pwsh')) {
            Write-Host "Closing previous dev window (PID $linePid)..." -ForegroundColor DarkGray
            Stop-Tree $linePid
        }
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

function Stop-PortOwners([int]$port) {
    # Safety net: free the port even if the pid file was lost or a server
    # outlived its window.
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
Stop-PreviousWindows
Stop-PortOwners 18080
Stop-PortOwners 15173

# --- Build & start --------------------------------------------------------

Write-Host "Building visual-runtime jar (needed by the code runner)..." -ForegroundColor Cyan
& "$root\gradlew.bat" :visual-runtime:jar --console=plain
if (-not $?) { Write-Host "Failed to build visual-runtime jar" -ForegroundColor Red; exit 1 }

Write-Host "Starting backend in a new window..." -ForegroundColor Cyan
$backend = Start-Process powershell -PassThru -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'InterviewDev-Backend'; Set-Location '$root'; & '$root\gradlew.bat' :backend:bootRun --console=plain"
)

Write-Host "Starting frontend in a new window..." -ForegroundColor Cyan
$frontend = Start-Process powershell -PassThru -ArgumentList @(
    '-NoExit', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'InterviewDev-Frontend'; Set-Location '$root\frontend'; npm install; npm run dev"
)

# Remember the window PIDs so the next run can close them.
Set-Content -Path $pidFile -Value @($backend.Id, $frontend.Id) -Encoding ascii

Write-Host ""
Write-Host "Open http://localhost:15173 once both windows finish starting." -ForegroundColor Green
