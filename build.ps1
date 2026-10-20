[CmdletBinding()]
param(
    [string[]]$Tasks = @(':app:assembleDebug'),
    [string]$JavaHome = '',
    [string]$AndroidSdk = '',
    [string]$GradleHome = '',
    [switch]$Offline,
    [switch]$Validate
)

$ErrorActionPreference = 'Stop'
$taskWorkRoot = Split-Path -Parent $PSScriptRoot
$environmentRoot = Join-Path $taskWorkRoot 'android-build-env'
$localEnvironmentFile = Join-Path $environmentRoot 'environment.json'
$localEnvironment = $null
if (Test-Path -LiteralPath $localEnvironmentFile -PathType Leaf) {
    $localEnvironment = Get-Content -LiteralPath $localEnvironmentFile -Raw | ConvertFrom-Json
}

function Find-BuildDirectory {
    param([string]$ExplicitPath, [string[]]$Candidates, [string]$RequiredFile, [string]$Hint)
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (-not (Test-Path -LiteralPath (Join-Path $ExplicitPath $RequiredFile) -PathType Leaf)) {
            throw "Invalid directory: $ExplicitPath. $Hint"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }
    foreach ($candidate in $Candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath (Join-Path $candidate $RequiredFile) -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw $Hint
}

$javaCandidates = @($env:JAVA_HOME, $env:JDK_HOME)
foreach ($vendorRoot in @('Microsoft', 'Eclipse Adoptium', 'Java')) {
    if ($env:ProgramFiles) {
        $vendorDirectory = Join-Path $env:ProgramFiles $vendorRoot
        if (Test-Path -LiteralPath $vendorDirectory -PathType Container) {
            $javaCandidates += @(Get-ChildItem -LiteralPath $vendorDirectory -Directory |
                Where-Object { $_.Name -match '^jdk-?17([.\-+_]|$)' } |
                Sort-Object Name -Descending | Select-Object -ExpandProperty FullName)
        }
    }
}
$pathJava = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($pathJava) {
    $javaCandidates += Split-Path -Parent (Split-Path -Parent $pathJava.Source)
}
if ($localEnvironment) {
    $javaCandidates += $localEnvironment.jdkHome
}
$JavaHome = Find-BuildDirectory -ExplicitPath $JavaHome -Candidates $javaCandidates `
    -RequiredFile 'bin\java.exe' -Hint 'Install JDK 17 and set JAVA_HOME, or supply -JavaHome.'

$sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
$localProperties = Join-Path $PSScriptRoot 'local.properties'
if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
    $sdkProperty = Get-Content -LiteralPath $localProperties |
        Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
    if ($sdkProperty) {
        $sdkCandidates += (($sdkProperty -replace '^\s*sdk\.dir\s*=\s*', '') -replace '\\:', ':' -replace '\\\\', '\')
    }
}
if ($env:LOCALAPPDATA) {
    $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
$sdkCandidates += Join-Path $environmentRoot 'android-sdk'
$AndroidSdk = Find-BuildDirectory -ExplicitPath $AndroidSdk -Candidates $sdkCandidates `
    -RequiredFile 'platforms\android-35\android.jar' `
    -Hint 'Install Android SDK Platform 35; set ANDROID_HOME or supply -AndroidSdk.'

if ([string]::IsNullOrWhiteSpace($GradleHome)) {
    if (-not [string]::IsNullOrWhiteSpace($env:GRADLE_USER_HOME)) {
        $GradleHome = $env:GRADLE_USER_HOME
    }
    elseif (Test-Path -LiteralPath (Join-Path $taskWorkRoot 'gradle-home') -PathType Container) {
        $GradleHome = Join-Path $taskWorkRoot 'gradle-home'
    }
    else {
        $GradleHome = Join-Path $PSScriptRoot '.build-env\gradle-home'
    }
}

# Prefer the version-pinned project wrapper. The unpacked cache is a fallback
# for this prepared workspace when the wrapper has not yet been generated.
$gradleExe = Join-Path $PSScriptRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleExe -PathType Leaf)) {
    $gradleExe = $null
    $distributionRoot = Join-Path $GradleHome 'wrapper\dists\gradle-8.13-bin'
    if (Test-Path -LiteralPath $distributionRoot -PathType Container) {
        foreach ($distribution in (Get-ChildItem -LiteralPath $distributionRoot -Directory)) {
            $candidate = Join-Path $distribution.FullName 'gradle-8.13\bin\gradle.bat'
            if (Test-Path -LiteralPath $candidate -PathType Leaf) {
                $gradleExe = $candidate
                break
            }
        }
    }
    if (-not $gradleExe) {
        throw 'The Gradle wrapper is missing. Restore gradlew.bat and gradle/wrapper from the source package.'
    }
}
if (-not ((Test-Path -LiteralPath (Join-Path $PSScriptRoot 'settings.gradle.kts')) -or
          (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'settings.gradle')))) {
    throw 'Project settings.gradle(.kts) is not ready.'
}

# Keep the prepared workspace's isolated directories when present. Elsewhere,
# defaults are project-local; explicit environment settings remain supported.
if (-not (Test-Path -LiteralPath $environmentRoot -PathType Container)) {
    $environmentRoot = Join-Path $PSScriptRoot '.build-env'
}
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
$env:GRADLE_USER_HOME = $GradleHome
if ([string]::IsNullOrWhiteSpace($env:ANDROID_USER_HOME)) {
    $env:ANDROID_USER_HOME = Join-Path $environmentRoot 'android-user-home'
}
if ([string]::IsNullOrWhiteSpace($env:ANDROID_AVD_HOME)) {
    $env:ANDROID_AVD_HOME = Join-Path $environmentRoot 'avd'
}
$env:Path = (Join-Path $JavaHome 'bin') + ';' + (Join-Path $AndroidSdk 'platform-tools') + ';' + $env:Path
$logDirectory = Join-Path $PSScriptRoot '.build-env\logs'
New-Item -ItemType Directory -Path $env:ANDROID_USER_HOME, $env:ANDROID_AVD_HOME, $logDirectory -Force | Out-Null

$gradleArguments = @('--no-daemon', '--console=plain', '--stacktrace', '--max-workers=2', '-Dfile.encoding=UTF-8')
if ($Offline) {
    $gradleArguments += '--offline'
}
if ($Validate) {
    $Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':yolo-engine:testDebugUnitTest', ':app:lintDebug')
}
$gradleArguments += $Tasks
$logPath = Join-Path $logDirectory ('android-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')

Write-Host "Project: $PSScriptRoot"
Write-Host "SDK: $AndroidSdk"
Write-Host "Gradle cache: $GradleHome"
Write-Host "Log: $logPath"
Push-Location -LiteralPath $PSScriptRoot
try {
    & $gradleExe @gradleArguments 2>&1 | Tee-Object -FilePath $logPath
    $buildExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}
if ($buildExitCode -ne 0) {
    throw "Gradle failed with exit code $buildExitCode. See $logPath"
}
Write-Host 'Build completed.'
