[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ImageDirectory,
    [ValidateRange(5, 60)][int]$TimeoutSeconds = 20
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Run this smoke check on Windows.' }
$image = (Resolve-Path -LiteralPath $ImageDirectory).Path
$launcher = Join-Path $image 'AI Developer Hub.exe'
if (!(Test-Path -LiteralPath $launcher)) { throw 'Packaged launcher was not found.' }
if (!(Test-Path -LiteralPath (Join-Path $image 'runtime/bin/server/jvm.dll'))) { throw 'Bundled Java runtime was not found.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$smokeRoot = Join-Path $projectRoot ('target/package-smoke/' + [Guid]::NewGuid().ToString('N'))
$testHome = Join-Path $smokeRoot 'profile with spaces'
$appData = Join-Path $testHome 'AppData/Roaming'
New-Item -ItemType Directory -Path $appData -Force | Out-Null
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = $launcher
$start.WorkingDirectory = $smokeRoot
$start.UseShellExecute = $false
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
# Isolate both storage locations, so the real profile and legacy data are never read or migrated.
$start.EnvironmentVariables['APPDATA'] = $appData
$start.EnvironmentVariables['JAVA_TOOL_OPTIONS'] = '-Duser.home="' + $testHome + '"'
$start.EnvironmentVariables.Remove('JDK_JAVA_OPTIONS')
$start.EnvironmentVariables.Remove('_JAVA_OPTIONS')
# Prove that startup uses the bundled runtime and JavaFX, not Java or an IDE from PATH.
$start.EnvironmentVariables['PATH'] = (Join-Path $env:SystemRoot 'System32') + ';' + $env:SystemRoot
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $start
$baseline = @([Diagnostics.Process]::GetProcessesByName('AI Developer Hub') | ForEach-Object { $_.Id })
$owned = @{}
$started = $false
try {
    $started = $process.Start()
    if (!$started) { throw 'Packaged launcher could not be started.' }
    $owned[$process.Id] = $process
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $windowFound = $false
    $windowProcess = $null
    while ([DateTime]::UtcNow -lt $deadline) {
        $process.Refresh()
        if ($process.HasExited) { throw "Packaged application exited early with code $($process.ExitCode). See $smokeRoot." }
        # The Windows jpackage launcher can relaunch itself; check its new image processes as well.
        foreach ($candidate in [Diagnostics.Process]::GetProcessesByName('AI Developer Hub')) {
            if ($candidate.Id -notin $baseline -and !$owned.ContainsKey($candidate.Id)) {
                if (!$candidate.HasExited -and $candidate.MainModule.FileName -eq $launcher) {
                    $owned[$candidate.Id] = $candidate
                }
            }
        }
        foreach ($candidate in $owned.Values) {
            $candidate.Refresh()
            if (!$candidate.HasExited -and $candidate.MainWindowHandle -ne [IntPtr]::Zero -and $candidate.MainWindowTitle -eq 'AI Developer Hub') {
                $windowProcess = $candidate
                $windowFound = $true
                break
            }
        }
        if ($windowFound) { break }
        Start-Sleep -Milliseconds 100
    }
    if (!$windowFound) { throw "Application window did not appear within $TimeoutSeconds seconds." }
    if (!(Test-Path -LiteralPath (Join-Path $appData 'AI-Developer-Hub'))) { throw 'Application did not initialize its isolated data directory.' }
    if (!$windowProcess.CloseMainWindow()) { throw 'Could not request a normal application shutdown.' }
    if (!$process.WaitForExit(5000)) { throw 'Application did not close normally within five seconds.' }
    if ($process.ExitCode -ne 0) { throw "Application returned exit code $($process.ExitCode)." }
    Write-Output 'PASS: bundled launcher opened its main window without Java on PATH and closed normally.'
    Write-Output "Smoke-check artifacts: $smokeRoot"
} finally {
    foreach ($candidate in $owned.Values) {
        if (!$candidate.HasExited) {
            $candidate.Kill()
            $candidate.WaitForExit(5000) | Out-Null
        }
    }
    if (Get-Variable stdout -ErrorAction SilentlyContinue) {
        $stdout.Wait(1000) | Out-Null
        $stderr.Wait(1000) | Out-Null
        if ($stdout.IsCompleted) { $stdout.Result | Set-Content -LiteralPath (Join-Path $smokeRoot 'stdout.txt') -Encoding UTF8 }
        if ($stderr.IsCompleted) { $stderr.Result | Set-Content -LiteralPath (Join-Path $smokeRoot 'stderr.txt') -Encoding UTF8 }
    }
    foreach ($candidate in $owned.Values) { $candidate.Dispose() }
    if (!$started) { $process.Dispose() }
}
