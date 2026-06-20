# Creates Desktop and Start-menu shortcuts that launch the app windowlessly
# (via wscript -> launch.vbs -> tray.ps1) with the generated icon.
#
# Usage:  launcher\install-shortcut.ps1

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$vbs  = Join-Path $PSScriptRoot 'launch.vbs'
$ico  = Join-Path $PSScriptRoot 'icon.ico'

$wsh = New-Object -ComObject WScript.Shell
$targets = @(
    (Join-Path ([Environment]::GetFolderPath('Desktop')) 'Java Interview Dungeon.lnk'),
    (Join-Path ([Environment]::GetFolderPath('Programs')) 'Java Interview Dungeon.lnk')
)

foreach ($lnk in $targets) {
    $sc = $wsh.CreateShortcut($lnk)
    $sc.TargetPath = Join-Path $env:WINDIR 'System32\wscript.exe'
    $sc.Arguments = "`"$vbs`""
    $sc.WorkingDirectory = $root
    $sc.IconLocation = "$ico,0"
    $sc.Description = 'Java Interview Dungeon'
    $sc.WindowStyle = 7   # minimized; wscript itself shows nothing
    $sc.Save()
    Write-Host "Created $lnk" -ForegroundColor Green
}
