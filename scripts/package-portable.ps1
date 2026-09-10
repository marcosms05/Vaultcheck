[CmdletBinding()]
param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$Destination
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JdkPath) { throw 'Specify -JdkPath pointing to a JDK 21 or later with jpackage.' }
$jpackage = Join-Path $JdkPath 'bin/jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'jpackage.exe was not found in this JDK.' }
$jar = Join-Path $projectRoot 'target/vaultcheck-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the project with mvnw.cmd verify first.' }
if (-not $Destination) { $Destination = Join-Path $projectRoot 'target/portable' }
$Destination = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath (Join-Path $Destination 'VaultCheck')) { throw 'Destination already contains VaultCheck. Choose a new -Destination; no files will be replaced.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    if (-not $archive.GetEntry('org/springframework/boot/loader/launch/JarLauncher.class')) {
        throw 'This is not the repackaged executable JAR. Close VaultCheck and rebuild.'
    }
} finally { $archive.Dispose() }
$staging = Join-Path $projectRoot ('target/package-input-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
$stagedJar = Join-Path $staging 'vaultcheck.jar'
try {
    Copy-Item -LiteralPath $jar -Destination $stagedJar
    $arguments = @('--type', 'app-image', '--name', 'VaultCheck', '--app-version', '0.1.0',
        '--input', $staging, '--main-jar', 'vaultcheck.jar',
        '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
        '--arguments', '--vaultcheck.ui=true', '--dest', $Destination,
        '--add-modules', 'ALL-MODULE-PATH',
        '--jlink-options', '--strip-debug --no-man-pages --no-header-files --compress=2',
        '--description', 'Local file integrity verification - development preview')
    & $jpackage @arguments
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed. An incomplete image may remain in the destination.' }
    $exe = Join-Path $Destination 'VaultCheck/VaultCheck.exe'
    if (-not (Test-Path -LiteralPath $exe)) { throw 'Expected launcher missing.' }
    $imageRoot = Split-Path -Parent $exe
    Copy-Item -LiteralPath (Join-Path $projectRoot 'LICENSE') -Destination (Join-Path $imageRoot 'LICENSE.txt')
    Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/laptop-test.md') -Destination (Join-Path $imageRoot 'LEEME-pruebas.md')
    $legalRoot = Join-Path $imageRoot 'dependency-notices'
    New-Item -ItemType Directory -Path $legalRoot | Out-Null
    $bundle = [IO.Compression.ZipFile]::OpenRead($jar)
    $inventory = @()
    try {
        foreach ($entry in $bundle.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' }) {
            $bytes = [IO.MemoryStream]::new()
            $inputStream = $entry.Open()
            try { $inputStream.CopyTo($bytes) } finally { $inputStream.Dispose() }
            try {
                $digest = [Security.Cryptography.SHA256]::Create()
                try { $hash = [BitConverter]::ToString($digest.ComputeHash($bytes.ToArray())).Replace('-', '').ToLowerInvariant() }
                finally { $digest.Dispose() }
                $bytes.Position = 0
                $nested = [IO.Compression.ZipArchive]::new($bytes, [IO.Compression.ZipArchiveMode]::Read, $true)
                try {
                    $notices = @($nested.Entries | Where-Object { $_.Name -match '^(LICENSE|NOTICE|COPYING)(\..*)?$' })
                    $index = 0
                    foreach ($notice in $notices) {
                        # Build output names ourselves; never extract archive-controlled paths.
                        $output = Join-Path $legalRoot ($entry.Name + '.' + $index + '.txt')
                        $reader = [IO.StreamReader]::new($notice.Open())
                        try { [IO.File]::WriteAllText($output, $reader.ReadToEnd()) } finally { $reader.Dispose() }
                        $index++
                    }
                    $inventory += [pscustomobject]@{ artifact = $entry.Name; sha256 = $hash; embeddedNotices = $notices.Count }
                } finally { $nested.Dispose() }
            } finally { $bytes.Dispose() }
        }
    } finally { $bundle.Dispose() }
    $inventory | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $imageRoot 'dependencies.json') -Encoding utf8
    Write-Output ('Portable development build: ' + $exe)
} finally {
    # Exact files created above only. No recursive deletion or replacement of distributions.
    if (Test-Path -LiteralPath $stagedJar) { Remove-Item -LiteralPath $stagedJar }
    if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging }
}
