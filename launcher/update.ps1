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

# One updater at a time. Clicking Restart again while a rebuild is running used
# to start a second one that fought the first over the Gradle daemon and the jar;
# the one already running relaunches the app for both of us.
$mutex = New-Object System.Threading.Mutex($false, 'Local\JavaInterviewDungeonUpdate')
if (-not $mutex.WaitOne(0)) { return }

Set-Content -Path $log -Value "[update] $(Get-Date -Format o) pull=$pull" -Encoding utf8

# Let the exited JVM release port 18080 and its jar file handle before rebuilding.
Start-Sleep -Seconds 2

# Runs one build step and appends its output to the log.
#
# A build leaves a Gradle daemon behind that lives for hours and inherits
# whatever handles it was given, and this script must not wait for it. That
# rules out both of the obvious ways to run a child here:
#
#   & cmd *>> $log                 waits for end-of-stream, which the daemon
#                                  holds open -> hangs forever
#   Start-Process -Wait            waits for the process AND its descendants,
#                                  the daemon being one -> hangs forever
#
# So: let cmd.exe redirect into a file (an OS-level handle nobody has to drain)
# and wait on that one process through .NET, which does not care about children.
function Invoke-Step([string]$commandLine) {
    $outFile = Join-Path $env:TEMP ('jid-update-' + [guid]::NewGuid().ToString('N') + '.log')
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = 'cmd.exe'
        $psi.Arguments = '/c ' + $commandLine + ' > "' + $outFile + '" 2>&1'
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $proc = [System.Diagnostics.Process]::Start($psi)
        $proc.WaitForExit()
        if (Test-Path $outFile) {
            try {
                # Shared read: the daemon may still hold the file open.
                $stream = [System.IO.File]::Open($outFile, 'Open', 'Read', 'ReadWrite')
                $text = (New-Object System.IO.StreamReader($stream)).ReadToEnd()
                $stream.Close()
                if ($text.Trim()) { $text.TrimEnd() | Add-Content $log }
            } catch {}
        }
        return $proc.ExitCode
    } finally {
        Remove-Item $outFile -Force -ErrorAction SilentlyContinue
    }
}

$ok = $true
try {
    if ($pull) {
        '[update] git pull --ff-only' | Add-Content $log
        $code = Invoke-Step ('git -C "' + $root + '" pull --ff-only')
        if ($code -ne 0) {
            $ok = $false
            "[update] git pull failed (exit $code)" | Add-Content $log
        }
    }
    if ($ok) {
        '[update] build-app.ps1' | Add-Content $log
        # A child process: build-app.ps1 exits with a real code on failure, and
        # its `exit` must not take this updater down with it.
        $code = Invoke-Step ('powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' +
            (Join-Path $scriptDir 'build-app.ps1') + '"')
        if ($code -ne 0) {
            $ok = $false
            "[update] build failed (exit $code)" | Add-Content $log
        }
    }
} catch {
    $ok = $false
    "[update] ERROR: $_" | Add-Content $log
}

"[update] done ok=$ok $(Get-Date -Format o)" | Add-Content $log

# Relaunch FIRST, report second. Whatever went wrong, the app must come back:
# the failure notice below is modal, and a dialog nobody sees (this process has
# no visible window) would otherwise keep the app down until it is dismissed.
# ShellExecute runs the .vbs under wscript, detached from this process.
Start-Process -FilePath $launchVbs
"[update] relaunched $(Get-Date -Format o)" | Add-Content $log

if (-not $ok) {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show(
        "Update/rebuild failed.`n`nSee: $log`n`nThe app has been started on the previous build.",
        'Java Interview Dungeon',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
}
