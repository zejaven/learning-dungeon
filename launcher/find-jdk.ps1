# Locates the JDK this app needs, independent of the machine's default Java.
#
# Dot-source it and call Find-AppJdk <repo root>. The system default cannot be
# trusted: any other Java installer (e.g. a JDK 17 for a game) is free to take
# over JAVA_HOME and the front of PATH, and the app then dies at startup with
# UnsupportedClassVersionError. So every candidate is CHECKED, not just found.
#
# A candidate qualifies when it is a full JDK (bin\javac.exe — the backend
# compiles learner code with the JDK compiler API, a JRE is not enough) and its
# `release` file says major version 21. If no 21 exists, the lowest newer JDK
# is used, since the jar's class files (version 65) also run there.

$script:RequiredJavaMajor = 21

function Get-JdkMajor([string]$home_) {
    $release = Join-Path $home_ 'release'
    if (-not (Test-Path (Join-Path $home_ 'bin\javac.exe')) -or -not (Test-Path $release)) {
        return $null
    }
    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="([0-9]+)' | Select-Object -First 1
    if (-not $line) { return $null }
    $major = [int]$line.Matches[0].Groups[1].Value
    if ($major -eq 1) { return 8 }   # "1.8.0_x"
    return $major
}

function Find-AppJdk([string]$repoRoot) {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    foreach ($dir in ($env:Path -split ';')) {
        if ($dir -and (Test-Path (Join-Path $dir 'java.exe'))) {
            $candidates.Add((Split-Path $dir -Parent))
        }
    }
    # The bundled tools\jdk-21 next to the repo, then the usual install roots
    # (Microsoft, Eclipse Adoptium, Oracle, Zulu, Corretto all live one level
    # under Program Files).
    Get-ChildItem (Join-Path $repoRoot '..\tools') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
        ForEach-Object { $candidates.Add($_.FullName) }
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:LOCALAPPDATA 'Programs'))) {
        if (-not $base) { continue }
        Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Get-ChildItem $_.FullName -Directory -ErrorAction SilentlyContinue } |
            ForEach-Object { $candidates.Add($_.FullName) }
    }

    $best = $null
    $bestMajor = [int]::MaxValue
    foreach ($cand in $candidates) {
        $major = Get-JdkMajor $cand
        if ($null -eq $major -or $major -lt $script:RequiredJavaMajor) { continue }
        if ($major -eq $script:RequiredJavaMajor) { return $cand.TrimEnd('\') }
        if ($major -lt $bestMajor) { $best = $cand.TrimEnd('\'); $bestMajor = $major }
    }
    return $best
}
