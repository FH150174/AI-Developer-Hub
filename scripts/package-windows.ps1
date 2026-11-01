[CmdletBinding()]
param(
    [ValidateSet('exe', 'app-image')][string]$Type = 'exe',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$MavenCommand = 'mvn.cmd',
    [string]$WixBin,
    [string]$AppVersion,
    [string]$OutputDirectory,
    [switch]$SkipTests
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Run this script on Windows with JDK 21.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Specify -JdkHome or set JAVA_HOME to a JDK 21 installation.' }
$jdk = (Resolve-Path -LiteralPath $JdkHome).Path
$jpackage = Join-Path $jdk 'bin/jpackage.exe'
if (!(Test-Path -LiteralPath $jpackage)) { throw 'The selected JDK does not contain jpackage.exe.' }
$jdkRelease = Get-Content -LiteralPath (Join-Path $jdk 'release') -Raw
if ($jdkRelease -notmatch 'JAVA_VERSION="21[.\"]') {
    throw 'This packaging script requires JDK 21.'
}
if ($jdkRelease -notmatch 'OS_ARCH="(?:x86_64|amd64)"') {
    throw 'This packaging script uses Windows x64 JavaFX modules; select a Windows x64 JDK.'
}
$maven = (Get-Command $MavenCommand -ErrorAction Stop).Source
[xml]$pom = Get-Content -LiteralPath (Join-Path $projectRoot 'pom.xml') -Raw -Encoding UTF8
if (!$AppVersion) { $AppVersion = $pom.project.version -replace '-SNAPSHOT$', '' }
if ($AppVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Specify a numeric -AppVersion such as 0.1.0.' }

$savedPath = $env:PATH
$savedJavaHome = $env:JAVA_HOME
function Invoke-BuildTool([string]$File, [string[]]$ToolArguments) {
    & $File @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw "$File failed with exit code $LASTEXITCODE." }
}
try {
    $env:JAVA_HOME = $jdk
    $env:PATH = (Join-Path $jdk 'bin') + ';' + $savedPath
    if ($Type -eq 'exe') {
        if (!$WixBin) {
            $portable = Join-Path $projectRoot 'target/tools/wix-3.14.1'
            if (Test-Path -LiteralPath (Join-Path $portable 'candle.exe')) { $WixBin = $portable }
        }
        if ($WixBin) {
            $WixBin = (Resolve-Path -LiteralPath $WixBin).Path
            $env:PATH = $WixBin + ';' + $env:PATH
        }
        foreach ($tool in @('candle.exe', 'light.exe')) {
            if (!(Get-Command $tool -ErrorAction SilentlyContinue)) {
                throw 'EXE packaging requires WiX 3.x (candle.exe and light.exe). Supply -WixBin, or use -Type app-image.'
            }
        }
    }
    if (!$OutputDirectory) {
        $OutputDirectory = Join-Path $projectRoot ('target/dist/' + $AppVersion + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
    }
    $destination = [IO.Path]::GetFullPath($OutputDirectory)
    if (Test-Path -LiteralPath $destination) { throw 'OutputDirectory must not exist; previous artifacts will not be overwritten.' }
    $work = Join-Path $projectRoot ('target/jpackage/' + [Guid]::NewGuid().ToString('N'))
    $inputDirectory = Join-Path $work 'input'
    $dependencies = Join-Path $work 'dependencies'
    $libraries = Join-Path $inputDirectory 'lib'
    New-Item -ItemType Directory -Path $libraries, $destination -Force | Out-Null
    Push-Location $projectRoot
    try {
        $buildArguments = @('--batch-mode', 'package')
        if ($SkipTests) { $buildArguments += '-DskipTests' }
        Invoke-BuildTool $maven $buildArguments
        Invoke-BuildTool $maven @('--batch-mode', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies',
            '-DincludeScope=runtime', "-DoutputDirectory=$dependencies")
    } finally { Pop-Location }
    $jarName = $pom.project.artifactId + '-' + $pom.project.version + '.jar'
    Copy-Item -LiteralPath (Join-Path $projectRoot ('target/' + $jarName)) -Destination $inputDirectory
    foreach ($jar in Get-ChildItem -LiteralPath $dependencies -Filter '*.jar') {
        # JavaFX's unclassified Maven jars are empty placeholders. Use only Windows implementation modules.
        if ($jar.Name -like 'javafx-*' -and $jar.Name -notmatch '-win\.jar$') { continue }
        Copy-Item -LiteralPath $jar.FullName -Destination $libraries
    }
    foreach ($module in @('base', 'graphics', 'controls')) {
        if (!(Get-ChildItem -LiteralPath $libraries -Filter "javafx-$module-*-win.jar")) {
            throw "Missing Windows JavaFX module: $module. Build with a Windows x64 JDK."
        }
    }
    $imageDestination = Join-Path $destination 'image'
    Invoke-BuildTool $jpackage @('--type', 'app-image', '--name', 'AI Developer Hub',
        '--app-version', $AppVersion, '--vendor', 'FHAICL',
        '--description', 'Windows desktop toolbox for personal development projects',
        '--input', $inputDirectory, '--main-jar', $jarName,
        '--main-class', 'com.fhaicl.aidevhub.AiDeveloperHubApplication',
        '--dest', $imageDestination,
        '--add-modules', 'java.desktop,java.logging,java.xml,jdk.unsupported',
        '--java-options', '--module-path=$APPDIR/lib', '--java-options', '--add-modules=javafx.controls')
    $image = Join-Path $imageDestination 'AI Developer Hub'
    if ($Type -eq 'exe') {
        Invoke-BuildTool $jpackage @('--type', 'exe', '--name', 'AI Developer Hub', '--app-version', $AppVersion,
            '--vendor', 'FHAICL', '--app-image', $image, '--dest', $destination,
            '--win-per-user-install', '--win-dir-chooser', '--win-menu', '--win-menu-group', 'AI Developer Hub',
            '--win-shortcut', '--win-shortcut-prompt', '--win-upgrade-uuid', 'b8cb99bf-8c97-4a67-b062-41769e2902dd')
    }
    $hashLines = foreach ($artifact in Get-ChildItem -LiteralPath $destination -Filter '*.exe') {
        $sha256 = [Security.Cryptography.SHA256]::Create()
        $stream = [IO.File]::OpenRead($artifact.FullName)
        try {
            $digest = [BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
            $digest + '  ' + $artifact.Name
        } finally {
            $stream.Dispose()
            $sha256.Dispose()
        }
    }
    if ($hashLines) { $hashLines | Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding ASCII }
    @("Version: $AppVersion", "JDK: $jdk", "Application image: $image", "Type: $Type",
        "Tests skipped: $SkipTests", 'Upgrade UUID: b8cb99bf-8c97-4a67-b062-41769e2902dd') |
        Set-Content -LiteralPath (Join-Path $destination 'build-info.txt') -Encoding UTF8
    Write-Output "Application image: $image"
    Write-Output "Package output: $destination"
} finally {
    $env:PATH = $savedPath
    $env:JAVA_HOME = $savedJavaHome
}
