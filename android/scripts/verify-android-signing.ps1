param(
    [string]$ApkPath,
    [string]$ExpectedFingerprintPath,
    [string]$ApkSignerPath
)

$ErrorActionPreference = "Stop"
if (-not $ApkPath) {
    $ApkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\beta\release\app-beta-release.apk"
}
if (-not $ExpectedFingerprintPath) {
    $ExpectedFingerprintPath = Join-Path $PSScriptRoot "..\signing-cert-sha256.txt"
}

function Normalize-Fingerprint([string]$Value) {
    return ($Value -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
}

$expected = Normalize-Fingerprint (Get-Content -Raw -LiteralPath $ExpectedFingerprintPath)
if ($expected.Length -ne 64) {
    throw "Expected signing fingerprint must contain exactly 64 hexadecimal characters"
}
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    throw "APK not found: $ApkPath"
}

if (-not $ApkSignerPath) {
    $sdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME) | Where-Object { $_ }
    $localProperties = Join-Path $PSScriptRoot "..\local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $localSdk = ($sdkLine -split '=', 2)[1].Replace('\\', '\').Replace('\:', ':')
            $sdkCandidates += $localSdk
        }
    }

    $executable = if ($IsLinux -or $IsMacOS) { 'apksigner' } else { 'apksigner.bat' }
    $apksigners = foreach ($sdkPath in ($sdkCandidates | Select-Object -Unique)) {
        Get-ChildItem -LiteralPath (Join-Path $sdkPath 'build-tools') -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Join-Path $_.FullName $executable } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
    }
    $ApkSignerPath = $apksigners |
        Sort-Object { [version](Split-Path (Split-Path $_ -Parent) -Leaf) } -Descending |
        Select-Object -First 1
}
if (-not $ApkSignerPath -or -not (Test-Path -LiteralPath $ApkSignerPath -PathType Leaf)) {
    throw "apksigner was not found; configure ANDROID_SDK_ROOT or android/local.properties"
}

$output = & $ApkSignerPath verify --print-certs $ApkPath 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "apksigner verification failed: $($output -join [Environment]::NewLine)"
}
$digestMatches = [regex]::Matches(($output -join "`n"), 'certificate SHA-256 digest:\s*([0-9a-fA-F:]+)')
$actual = @($digestMatches | ForEach-Object { Normalize-Fingerprint $_.Groups[1].Value } | Select-Object -Unique)
if ($actual.Count -ne 1 -or $actual[0] -ne $expected) {
    $found = if ($actual.Count -eq 0) { 'none' } else { $actual -join ', ' }
    throw "Signing certificate mismatch: expected $expected, found $found"
}

Write-Output "APK signing certificate verified: $expected"
