# System-tray launcher for the Java Interview Dungeon.
#
# Starts the packaged backend (which also serves the built frontend), waits for
# it to come up, opens the browser, and keeps a tray icon alive. Choosing
# "Exit" (or closing the JVM any other way) shuts the server down.
#
# Launched without a console window via launch.vbs. Run launcher\build-app.ps1
# first so the jar and frontend\dist exist.

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$root       = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$port       = 18080
$url        = "http://localhost:$port"
$iconPath   = Join-Path $PSScriptRoot 'icon.ico'
$logPath    = Join-Path $PSScriptRoot 'app.log'
$title      = 'Java Interview Dungeon'
# Sentinel written by the backend's /api/system/update; its presence on JVM exit
# means "rebuild+restart" rather than a normal shutdown (see update.ps1).
$updateFlag   = Join-Path $PSScriptRoot 'update.flag'
$updateScript = Join-Path $PSScriptRoot 'update.ps1'

function Show-Error([string]$text) {
    [System.Windows.Forms.MessageBox]::Show($text, $title,
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
}

# --- locate the runnable jar (newest backend-*.jar, excluding the plain one) ---
# Searched in several places so both the source/dev layout and a copied
# distribution (jar next to these scripts, or at the shared root) work. The
# frontend bundle ships inside the jar (static/), so no frontend/dist is needed.
$searchDirs = @(
    $PSScriptRoot,
    (Join-Path $root 'backend\build\libs'),
    $root
) | Where-Object { Test-Path $_ } | Select-Object -Unique
$jar = Get-ChildItem $searchDirs -Filter 'backend-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*-plain.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $jar) {
    Show-Error ("App jar (backend-*.jar) not found.`n`nLooked in:`n - " +
        ($searchDirs -join "`n - ") +
        "`n`nPut backend-0.0.1.jar in one of these folders (e.g. next to this" +
        " script), or run launcher\build-app.ps1 if you have the source.")
    return
}

# --- locate javaw.exe (windowless java) ---
$javaw = $null
if ($env:JAVA_HOME) {
    $cand = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
    if (Test-Path $cand) { $javaw = $cand }
}
if (-not $javaw) {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) {
    # Fall back to the bundled tools\jdk-21 next to the repo.
    $bundled = Get-ChildItem (Join-Path $root '..\tools') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($bundled) {
        $cand = Join-Path $bundled.FullName 'bin\javaw.exe'
        if (Test-Path $cand) { $javaw = $cand }
    }
}
if (-not $javaw) {
    Show-Error 'javaw.exe not found. Set JAVA_HOME or add a JDK 21 bin to PATH.'
    return
}

# --- already running? just open it ---
$listening = $false
try {
    $listening = [bool](Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop)
} catch { $listening = $false }
if ($listening) {
    # The port answers, but is it *our* app? A foreign service on 18080 must not
    # be silently opened in the browser as if it were the Dungeon.
    $ours = $false
    try {
        $status = Invoke-RestMethod "http://127.0.0.1:$port/api/system/status" -TimeoutSec 5
        $ours = [bool]$status.bootId
    } catch { $ours = $false }
    if ($ours) {
        Start-Process $url
        return
    }
    $owner = (Get-NetTCPConnection -LocalPort $port -State Listen |
        Select-Object -First 1).OwningProcess
    Show-Error ("Port $port is already occupied by another application (PID $owner)." +
        "`n`nFree the port or close that application, then start the Dungeon again.")
    return
}

# --- start the backend (windowless) ---
# The JVM's console output is captured to startup.log so that an early crash
# (e.g. wrong Java version) is visible even before Spring's own app.log logging
# initializes - which is exactly when app.log would otherwise be empty/missing.
$startupLog = Join-Path $PSScriptRoot 'startup.log'
Set-Content -Path $startupLog -Value '' -ErrorAction SilentlyContinue

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $javaw
$psi.Arguments = "-Dapp.repo-root=`"$root`" -Dapp.launcher=tray -jar `"$jar`" --logging.file.name=`"$logPath`""
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.CreateNoWindow = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$java = [System.Diagnostics.Process]::Start($psi)

# Drain both pipes to startup.log (also prevents the JVM blocking on a full pipe).
$pump = {
    if ($null -ne $EventArgs.Data) {
        Add-Content -Path $Event.MessageData -Value $EventArgs.Data -ErrorAction SilentlyContinue
    }
}
Register-ObjectEvent -InputObject $java -EventName OutputDataReceived -MessageData $startupLog -Action $pump | Out-Null
Register-ObjectEvent -InputObject $java -EventName ErrorDataReceived  -MessageData $startupLog -Action $pump | Out-Null
$java.BeginOutputReadLine()
$java.BeginErrorReadLine()

# --- tray icon + menu ---
$icon = if (Test-Path $iconPath) {
    New-Object System.Drawing.Icon $iconPath
} else {
    [System.Drawing.SystemIcons]::Application
}

$notify = New-Object System.Windows.Forms.NotifyIcon
$notify.Icon = $icon
$notify.Text = $title          # tooltip (<= 63 chars)
$notify.Visible = $true

$menu = New-Object System.Windows.Forms.ContextMenuStrip
$openItem = New-Object System.Windows.Forms.ToolStripMenuItem 'Open'
$logItem  = New-Object System.Windows.Forms.ToolStripMenuItem 'Open log'
$quitItem = New-Object System.Windows.Forms.ToolStripMenuItem 'Exit'
[void]$menu.Items.Add($openItem)
[void]$menu.Items.Add($logItem)
[void]$menu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))
[void]$menu.Items.Add($quitItem)
$notify.ContextMenuStrip = $menu

$openApp = { Start-Process $url }
$openItem.add_Click($openApp)
$notify.add_DoubleClick($openApp)
$logItem.add_Click({
    if (Test-Path $logPath) { Start-Process $logPath }
    elseif (Test-Path $startupLog) { Start-Process $startupLog }
})

$shutdown = {
    try {
        if ($java -and -not $java.HasExited) {
            # Tree-kill so any transient code-runner child JVMs go too.
            Start-Process taskkill -ArgumentList '/PID', $java.Id, '/T', '/F' `
                -WindowStyle Hidden -Wait -ErrorAction SilentlyContinue
        }
    } catch {}
    if ($notify) { $notify.Visible = $false; $notify.Dispose() }
    [System.Windows.Forms.Application]::Exit()
}
$quitItem.add_Click($shutdown)

# The backend exited after writing update.flag: hand rebuild+restart off to a
# detached updater (survives this tray quitting) and close the tray. The updater
# relaunches a fresh tray + jar when the build finishes.
$handoffUpdate = {
    $notify.ShowBalloonTip(3000, $title, 'Updating - rebuilding and restarting...',
        [System.Windows.Forms.ToolTipIcon]::Info)
    try {
        Start-Process powershell.exe -WindowStyle Hidden -ArgumentList @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $updateScript)
    } catch {}
    if ($notify) { $notify.Visible = $false; $notify.Dispose() }
    [System.Windows.Forms.Application]::Exit()
}

# --- wait for readiness, keeping the tray responsive ---
$notify.ShowBalloonTip(3000, $title, 'Starting...', [System.Windows.Forms.ToolTipIcon]::Info)
$deadline = (Get-Date).AddSeconds(90)
$ready = $false
while ((Get-Date) -lt $deadline) {
    if ($java.HasExited) { break }
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
        if ($resp.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Start-Sleep -Milliseconds 800
    [System.Windows.Forms.Application]::DoEvents()
}

if ($ready) {
    Start-Process $url
    $notify.ShowBalloonTip(2000, $title, 'Ready.', [System.Windows.Forms.ToolTipIcon]::Info)
} elseif ($java.HasExited) {
    Show-Error ("The backend exited during startup (exit code $($java.ExitCode)).`n`n" +
        "See $startupLog for the actual error.`n`n" +
        "Common causes:`n" +
        " - Java is not version 21 (run 'java -version' in a terminal)`n" +
        " - PostgreSQL is not running, or wrong credentials in config\secret.yml`n" +
        " - topics\ folder was not copied next to the launcher folder")
    & $shutdown
    return
} else {
    $notify.ShowBalloonTip(4000, $title,
        'Still starting - use Open from the tray when ready.',
        [System.Windows.Forms.ToolTipIcon]::Warning)
}

# --- exit the tray if the backend dies on its own ---
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 3000
$timer.add_Tick({
    if ($java.HasExited) {
        $timer.Stop()
        if (Test-Path $updateFlag) { & $handoffUpdate } else { & $shutdown }
    }
})
$timer.Start()

[System.Windows.Forms.Application]::Run()
