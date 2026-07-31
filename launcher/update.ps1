# Detached updater, invoked by tray.ps1 when the backend left an update.flag on
# exit. Optionally pulls the latest commits from GitHub, rebuilds the packaged
# app (launcher\build-app.ps1), then relaunches the tray (launch.vbs), which
# starts the freshly built jar. Runs windowless and independent of the tray, so
# it survives the tray quitting during the rebuild.
#
# The frontend shows a "restarting" overlay meanwhile and reloads once the new
# backend answers with a changed bootId.

$ErrorActionPreference = 'Continue'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root      = (Resolve-Path (Join-Path $scriptDir '..')).Path
$flag      = Join-Path $scriptDir 'update.flag'
$log       = Join-Path $scriptDir 'update.log'
$launchVbs = Join-Path $scriptDir 'launch.vbs'

# Read the requested mode, then remove the flag so a failed build can't loop.
$pull = $false
if (Test-Path $flag) {
    if ((Get-Content $flag -Raw) -match 'pull\s*=\s*true') { $pull = $true }
    Remove-Item $flag -Force -ErrorAction SilentlyContinue
}

Set-Content -Path $log -Value "[update] $(Get-Date -Format o) pull=$pull" -Encoding utf8

# Let the exited JVM release port 18080 and its jar file handle before rebuilding.
Start-Sleep -Seconds 2

$ok = $true
try {
    if ($pull) {
        '[update] git pull --ff-only' | Add-Content $log
        & git -C $root pull --ff-only *>> $log
        if ($LASTEXITCODE -ne 0) {
            $ok = $false
            "[update] git pull failed (exit $LASTEXITCODE)" | Add-Content $log
        }
    }
    if ($ok) {
        '[update] build-app.ps1' | Add-Content $log
        & (Join-Path $scriptDir 'build-app.ps1') *>> $log
        if ($LASTEXITCODE -ne 0) {
            $ok = $false
            "[update] build failed (exit $LASTEXITCODE)" | Add-Content $log
        }
    }
} catch {
    $ok = $false
    "[update] ERROR: $_" | Add-Content $log
}

"[update] done ok=$ok $(Get-Date -Format o)" | Add-Content $log

if (-not $ok) {
    # Surface the failure; the app still comes back on the previous build below.
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show(
        "Update/rebuild failed.`n`nSee: $log`n`nThe app will start on the previous build.",
        'Java Interview Dungeon',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
}

# Relaunch the tray (starts the freshly built jar via javaw). ShellExecute runs
# the .vbs under wscript, detached from this process.
Start-Process -FilePath $launchVbs
