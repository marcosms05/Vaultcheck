# Run from an interactive Windows session. Every smoke uses synthetic files only.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if ($env:JAVA_HOME) {
        $javaExecutable = Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'JAVA_HOME must point to a valid JDK 21 or later.' }
    } else {
        $javaExecutable = (Get-Command java.exe -ErrorAction Stop).Source
    }
    & .\mvnw.cmd verify
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed; desktop checks were not started.' }
    [xml]$report = Get-Content -LiteralPath 'target/surefire-reports/TEST-io.vaultcheck.BootstrapTest.xml' -Raw
    $testClasspath = ($report.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    if (-not $testClasspath) { throw 'Surefire did not provide the compiled test classpath.' }
    foreach ($check in @('DesktopSmoke', 'DesktopWorkerSmoke', 'DesktopFolderSmoke', 'DesktopReferenceSmoke', 'DesktopCreationSmoke')) {
        & $javaExecutable -cp $testClasspath ('io.vaultcheck.desktop.' + $check)
        if ($LASTEXITCODE -ne 0) { throw ('Desktop check failed: ' + $check) }
    }
    Write-Output 'All five desktop checks passed.'
} finally {
    Pop-Location
}
