param(
    [string]$CachePath = 'D:\programms\gradle-local'
)
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $env:JAVA_HOME -and (Test-Path 'D:\programms\AndroidStudio\jbr')) {
    $env:JAVA_HOME = 'D:\programms\AndroidStudio\jbr'
}
$gradlePath = 'D:\programms\gradle-8.13\bin\gradle.bat'
if (-not (Test-Path $gradlePath)) { $gradlePath = Join-Path $repoPath 'gradlew.bat' }
Push-Location $repoPath
try {
    & $gradlePath '--gradle-user-home' $CachePath ':app:testStandardDebugUnitTest' ':app:assembleParallelRelease' ':app:lintParallelRelease' '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw "Gradle завершился с кодом $LASTEXITCODE" }
} finally { Pop-Location }
