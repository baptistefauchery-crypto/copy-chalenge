param(
    [Parameter(Mandatory = $true)]
    [string]$Tag,
    [string]$GradleFile,
    [switch]$CheckGitHistory
)

$ErrorActionPreference = "Stop"
if (-not $GradleFile) {
    $GradleFile = Join-Path $PSScriptRoot "..\app\build.gradle.kts"
}
$tagMatch = [regex]::Match($Tag, '^v(?<version>\d+\.\d+\.\d+-beta\.(?<beta>\d+))$')
if (-not $tagMatch.Success) {
    throw "Invalid beta tag '$Tag'; expected vMAJOR.MINOR.PATCH-beta.NUMBER"
}

$source = Get-Content -Raw -LiteralPath $GradleFile
$versionNameMatches = [regex]::Matches($source, 'versionName\s*=\s*"([^"]+)"')
$versionCodeMatches = [regex]::Matches($source, 'versionCode\s*=\s*(\d+)')
if ($versionNameMatches.Count -ne 1 -or $versionCodeMatches.Count -ne 1) {
    throw "Expected exactly one versionName and versionCode in $GradleFile"
}

$versionName = $versionNameMatches[0].Groups[1].Value
$versionCode = [int]$versionCodeMatches[0].Groups[1].Value
$tagVersion = $tagMatch.Groups['version'].Value
$tagBeta = [int]$tagMatch.Groups['beta'].Value
$problems = @()
if ($versionName -ne $tagVersion) {
    $problems += "versionName is '$versionName', but tag requires '$tagVersion'"
}
if ($versionCode -ne $tagBeta) {
    $problems += "versionCode is $versionCode, but beta tag number is $tagBeta"
}

if ($CheckGitHistory) {
    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
    $historicalCodes = @()
    $betaTags = & git -C $repositoryRoot tag --list 'v*-beta.*'
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect historical beta tags"
    }
    foreach ($historicalTag in $betaTags) {
        if ($historicalTag -eq $Tag -or $historicalTag -notmatch '^v\d+\.\d+\.\d+-beta\.\d+$') {
            continue
        }
        $historicalGradle = & git -C $repositoryRoot show "${historicalTag}:android/app/build.gradle.kts" 2>$null
        if ($LASTEXITCODE -ne 0) {
            continue
        }
        $historicalMatch = [regex]::Match(($historicalGradle -join "`n"), 'versionCode\s*=\s*(\d+)')
        if ($historicalMatch.Success) {
            $historicalCodes += [int]$historicalMatch.Groups[1].Value
        }
    }
    if ($historicalCodes.Count -gt 0) {
        $highestHistoricalCode = ($historicalCodes | Measure-Object -Maximum).Maximum
        if ($versionCode -le $highestHistoricalCode) {
            $problems += "versionCode $versionCode must be greater than the historical beta maximum $highestHistoricalCode"
        }
    }
}
if ($problems.Count -gt 0) {
    throw "Version/tag mismatch: $($problems -join '; ')"
}

Write-Output "Version/tag verified: $Tag (versionCode $versionCode, monotonic history checked: $CheckGitHistory)"
