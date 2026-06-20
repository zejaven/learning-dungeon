# Creates the PostgreSQL database the app needs (java-interview-dungeon) using
# the credentials in config\secret.yml. PostgreSQL must already be running.
#
# The app connects straight to this database and only creates its *tables* on
# startup - it does NOT create the database itself, so run this once first.
#
# Usage:
#   launcher\create-db.ps1
#   launcher\create-db.ps1 -DbName other-db -DbHost localhost -Port 5432

param(
    [string]$DbName = 'java-interview-dungeon',
    [string]$DbHost = 'localhost',
    [int]$Port = 5432
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$secret = Join-Path $root 'config\secret.yml'

if (-not (Test-Path $secret)) {
    throw "config\secret.yml not found at $secret. Create it first (see config\secret.example.yml)."
}

# --- read db.username / db.password from the (tiny, flat) secret.yml ---
function Remove-YamlQuotes([string]$s) {
    if ($null -eq $s) { return $s }
    $s = $s.Trim()
    if ($s.Length -ge 2 -and
        (($s[0] -eq '"' -and $s[-1] -eq '"') -or ($s[0] -eq "'" -and $s[-1] -eq "'"))) {
        return $s.Substring(1, $s.Length - 2)
    }
    return $s
}

$user = $null
$pass = $null
foreach ($line in Get-Content $secret) {
    if ($line -match '^\s*username:\s*(.+?)\s*$') { $user = Remove-YamlQuotes $matches[1] }
    elseif ($line -match '^\s*password:\s*(.+?)\s*$') { $pass = Remove-YamlQuotes $matches[1] }
}
if (-not $user) { throw "Could not read db.username from $secret" }
if ($null -eq $pass) { $pass = '' }

# --- locate psql.exe (PATH, or a standard PostgreSQL install) ---
$psql = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    $psql = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $psql) {
    throw ("psql.exe not found. Add PostgreSQL's bin to PATH, or create the database manually:`n" +
        "  CREATE DATABASE `"$DbName`";")
}

Write-Host "Using psql:    $psql" -ForegroundColor DarkGray
Write-Host "Connecting as '$user' to ${DbHost}:${Port} ..." -ForegroundColor Cyan

# psql reads the password from this env var; cleared in finally.
$env:PGPASSWORD = $pass
try {
    $checkSql = "SELECT 1 FROM pg_database WHERE datname = '$DbName'"
    $exists = (& $psql -h $DbHost -p $Port -U $user -d postgres -tAc $checkSql) -join ''
    if ($LASTEXITCODE -ne 0) {
        throw "Could not connect to PostgreSQL. Is it running, and are the credentials in secret.yml correct?"
    }

    if ($exists.Trim() -eq '1') {
        Write-Host "Database '$DbName' already exists. Nothing to do." -ForegroundColor Green
    } else {
        Write-Host "Creating database '$DbName'..." -ForegroundColor Cyan
        # CREATE DATABASE has no IF NOT EXISTS; the name needs double quotes (hyphens).
        & $psql -h $DbHost -p $Port -U $user -d postgres -c ('CREATE DATABASE "' + $DbName + '"')
        if ($LASTEXITCODE -ne 0) {
            throw "CREATE DATABASE failed (the role '$user' may lack the CREATEDB privilege)."
        }
        Write-Host "Database '$DbName' created." -ForegroundColor Green
    }
} finally {
    Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "Done. The app creates its tables automatically on first start." -ForegroundColor Green
