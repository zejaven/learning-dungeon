# Turns access from other devices (your phone) on and off.
#
#   launcher\remote.ps1                 show what is configured right now
#   launcher\remote.ps1 lan             home Wi-Fi: bind to 0.0.0.0 + token
#   launcher\remote.ps1 tailscale       from anywhere: Tailscale Serve + token
#   launcher\remote.ps1 off             back to loopback only
#
# Everything it writes goes into the managed block at the end of
# config/secret.yml (git-ignored). The backend reads it at startup, so restart
# the app afterwards (tray: gear -> Restart).
#
# Add -AllowCodeExecution to let the phone use Run / SQL / Run tests too. Those
# endpoints compile and execute whatever is sent to them; leave it off unless
# you mean it.

param(
    [ValidateSet('status', 'lan', 'tailscale', 'off')]
    [string]$Action = 'status',
    [switch]$AllowCodeExecution
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$secretPath = Join-Path $root 'config\secret.yml'
$port = 18080

$beginMark = '# >>> remote access (managed by launcher\remote.ps1) >>>'
$endMark = '# <<< remote access <<<'

function Read-SecretText {
    if (-not (Test-Path $secretPath)) {
        throw "config\secret.yml not found. Copy config\secret.example.yml to config\secret.yml first."
    }
    return [System.IO.File]::ReadAllText($secretPath)
}

function Write-SecretText([string]$text) {
    # No BOM: the file is read by snakeyaml, and a backup first because this is
    # the user's credentials file.
    Copy-Item $secretPath "$secretPath.bak" -Force
    [System.IO.File]::WriteAllText($secretPath, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Remove-ManagedBlock([string]$text) {
    $pattern = [regex]::Escape($beginMark) + '.*?' + [regex]::Escape($endMark) + '\r?\n?'
    return [regex]::Replace($text, $pattern, '', 'Singleline')
}

# A hand-written remote:/app: key outside the managed block would collide with
# the one we add (snakeyaml rejects duplicate keys), so stop instead.
function Assert-NoManualKeys([string]$text) {
    foreach ($key in @('remote:', 'app:')) {
        if ($text -match "(?m)^$([regex]::Escape($key))") {
            throw "config\secret.yml already defines '$key' outside the managed block. Remove it and run this again."
        }
    }
}

function Get-ExistingToken([string]$text) {
    $m = [regex]::Match($text, "(?m)^\s*token:\s*'([^']+)'")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function New-Token {
    # 24 chars from a lookalike-free alphabet: ~120 bits, still typeable.
    $alphabet = 'abcdefghijkmnpqrstuvwxyz23456789'
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $chars = foreach ($b in $bytes) { $alphabet[$b % $alphabet.Length] }
    return -join $chars
}

function Get-LanAddress {
    $ip = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.InterfaceAlias -notmatch 'Loopback|VMware|vEthernet|Tailscale' -and $_.IPAddress -notlike '169.254.*' } |
        Select-Object -First 1
    if ($ip) { return $ip.IPAddress }
    return $null
}

function Get-TailscaleExe {
    $cmd = Get-Command tailscale -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $default = 'C:\Program Files\Tailscale\tailscale.exe'
    if (Test-Path $default) { return $default }
    return $null
}

function Test-Elevated {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    return (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Set-ManagedBlock([string]$mode, [string]$token, [bool]$bindAll) {
    $text = Read-SecretText
    $text = Remove-ManagedBlock $text
    Assert-NoManualKeys $text
    if (-not $text.EndsWith("`n")) { $text += "`r`n" }

    $allowCode = 'false'
    if ($AllowCodeExecution) { $allowCode = 'true' }

    $block = @()
    $block += $beginMark
    $block += 'remote:'
    $block += "  mode: $mode"
    $block += "  token: '$token'"
    $block += "  allow-code-execution: $allowCode"
    if ($bindAll) {
        $block += 'app:'
        $block += '  bind-address: 0.0.0.0'
    }
    $block += $endMark

    Write-SecretText ($text + ($block -join "`r`n") + "`r`n")
}

function Show-Status {
    $text = ''
    if (Test-Path $secretPath) { $text = Read-SecretText }
    $mode = 'off'
    $m = [regex]::Match($text, "(?m)^\s*mode:\s*(\w+)")
    if ($m.Success) { $mode = $m.Groups[1].Value }
    $token = Get-ExistingToken $text
    $bindAll = $text -match 'bind-address:\s*0\.0\.0\.0'
    $allowCode = $text -match 'allow-code-execution:\s*true'

    Write-Host ''
    Write-Host "Remote access mode : $mode" -ForegroundColor Cyan
    if ($mode -ne 'off') {
        if ($bindAll) { Write-Host 'Server binding     : 0.0.0.0 (reachable on the LAN)' }
        else { Write-Host 'Server binding     : 127.0.0.1 (proxy only)' }
        if ($allowCode) { Write-Host 'Code execution     : ALLOWED for remote clients' -ForegroundColor Yellow }
        else { Write-Host 'Code execution     : blocked for remote clients' }
    }

    $listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listening) {
        $addrs = ($listening | Select-Object -ExpandProperty LocalAddress -Unique) -join ', '
        Write-Host "Backend on $port    : listening on $addrs" -ForegroundColor Green
    } else {
        Write-Host "Backend on $port    : not running" -ForegroundColor DarkGray
    }

    if ($mode -ne 'off' -and $token) {
        Write-Host ''
        Write-Host 'Open once on the phone (the token then lives in a cookie):' -ForegroundColor Green
        if ($mode -eq 'direct') {
            $lan = Get-LanAddress
            if ($lan) { Write-Host "  http://${lan}:$port/?token=$token" }
        } else {
            $ts = Get-TailscaleExe
            if ($ts) {
                $dns = (& $ts status --json | ConvertFrom-Json).Self.DNSName
                if ($dns) { Write-Host "  https://$($dns.TrimEnd('.'))/?token=$token" }
            }
        }
    }
    Write-Host ''
}

switch ($Action) {

    'status' {
        Show-Status
    }

    'lan' {
        $token = Get-ExistingToken (Read-SecretText)
        if (-not $token) { $token = New-Token }
        Set-ManagedBlock -mode 'direct' -token $token -bindAll $true

        $ruleName = 'Java Interview Dungeon (LAN)'
        $existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
        if (-not $existing) {
            if (Test-Elevated) {
                New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP `
                    -LocalPort $port -Action Allow -Profile Private | Out-Null
                Write-Host "Firewall rule added: $ruleName (private networks only)" -ForegroundColor Green
            } else {
                Write-Host 'Not elevated — add the firewall rule from an admin PowerShell:' -ForegroundColor Yellow
                Write-Host "  New-NetFirewallRule -DisplayName '$ruleName' -Direction Inbound -Protocol TCP -LocalPort $port -Action Allow -Profile Private"
            }
        }

        Write-Host ''
        Write-Host 'LAN access configured. Restart the app for it to take effect.' -ForegroundColor Green
        Write-Host 'Anyone on your Wi-Fi can reach the port now; the token is what keeps them out.' -ForegroundColor DarkGray
        Show-Status
    }

    'tailscale' {
        $ts = Get-TailscaleExe
        if (-not $ts) {
            Write-Host 'Tailscale is not installed.' -ForegroundColor Yellow
            Write-Host '  1. Install it on this PC:  winget install tailscale.tailscale'
            Write-Host '  2. Sign in:                tailscale up'
            Write-Host '  3. Install the Tailscale app on the phone and sign in with the same account'
            Write-Host '  4. Enable MagicDNS + HTTPS certificates in the admin console (needed for https://)'
            Write-Host '  5. Run this script again'
            exit 1
        }

        $token = Get-ExistingToken (Read-SecretText)
        if (-not $token) { $token = New-Token }

        # Publish FIRST: without HTTPS certificates enabled in the admin console
        # this fails, and writing `mode: proxied` before that would leave the app
        # expecting a proxy that does not exist.
        Write-Host "Publishing 127.0.0.1:$port over Tailscale Serve..." -ForegroundColor Cyan
        & $ts serve --bg --yes --https=443 "http://127.0.0.1:$port"
        if ($LASTEXITCODE -ne 0) {
            Write-Host ''
            Write-Host 'tailscale serve failed — nothing was changed.' -ForegroundColor Red
            Write-Host 'Most likely HTTPS certificates are still off: console.tailscale.com/admin/dns' -ForegroundColor Yellow
            Write-Host '-> HTTPS Certificates -> Enable HTTPS, then run this again.' -ForegroundColor Yellow
            exit 1
        }

        # Keep the server on loopback: Tailscale Serve is the only way in.
        Set-ManagedBlock -mode 'proxied' -token $token -bindAll $false

        Write-Host ''
        Write-Host 'Tailscale access configured. Restart the app for the token to take effect.' -ForegroundColor Green
        Write-Host 'The address is HTTPS, which is also what a future PWA needs to install.' -ForegroundColor DarkGray
        Show-Status
    }

    'off' {
        $text = Remove-ManagedBlock (Read-SecretText)
        Write-SecretText $text

        $ts = Get-TailscaleExe
        if ($ts) {
            $serving = & $ts serve status 2>$null
            if ($serving) {
                & $ts serve reset | Out-Null
                Write-Host 'Tailscale Serve reset.' -ForegroundColor Green
            }
        }

        $ruleName = 'Java Interview Dungeon (LAN)'
        $existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
        if ($existing) {
            if (Test-Elevated) {
                Remove-NetFirewallRule -DisplayName $ruleName
                Write-Host 'Firewall rule removed.' -ForegroundColor Green
            } else {
                Write-Host "Firewall rule '$ruleName' left in place (needs an admin shell to remove)." -ForegroundColor Yellow
            }
        }

        Write-Host ''
        Write-Host 'Back to loopback only. Restart the app for it to take effect.' -ForegroundColor Green
    }
}
