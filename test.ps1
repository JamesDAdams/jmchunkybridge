param(
    [string]$ModsDir = "",
    [string]$LibrariesDir = "$env:APPDATA\ModrinthApp\meta\libraries",
    [string]$JavaExe = "$env:APPDATA\ModrinthApp\meta\java_versions\zulu21.52.203-ca-jre21.0.12.1-win_x64\bin\java.exe"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$ErrorActionPreference = "Stop"
$baseDir = $PSScriptRoot
$ecjJar = Join-Path $baseDir "build_tools\ecj.jar"

if ([string]::IsNullOrWhiteSpace($ModsDir)) {
    $ModsDir = Join-Path (Split-Path $baseDir -Parent) "mods"
}

& powershell -ExecutionPolicy Bypass -File (Join-Path $baseDir "build.ps1") -ModsDir $ModsDir -LibrariesDir $LibrariesDir -JavaExe $JavaExe -SkipDeploy
if ($LASTEXITCODE -ne 0) { throw "build.ps1 failed" }

$testClassesDir = Join-Path $baseDir "build\test_classes"
New-Item -ItemType Directory -Path $testClassesDir -Force | Out-Null

function Find-Lib($pattern) {
    $item = Get-ChildItem -Path $LibrariesDir -Recurse -Filter $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $item) { throw "Library not found: $pattern" }
    return $item.FullName
}

$testLibs = @(
    (Join-Path $baseDir "build\jmchunkybridge-1.0.0-neoforge-1.21.1.jar"),
    (Join-Path $ModsDir "jmsharedmaps-1.7.1-1.21.1-neoforge.jar"),
    (Join-Path $ModsDir "journeymap-neoforge-1.21.1-6.0.9.jar")
)
$patterns = @(
    "client-1.21.1-*srg.jar",
    "neoforge-21.1.264-universal.jar",
    "slf4j-api-2.0.17.jar",
    "brigadier-1.3.10.jar",
    "datafixerupper-8.0.16.jar",
    "fastutil-8.5.12.jar",
    "gson-2.10.1.jar",
    "commons-lang3-3.14.0.jar",
    "guava-32.1.2-jre.jar",
    "authlib-6.0.54.jar",
    "joml-1.10.5.jar",
    "netty-buffer-4.1.97.Final.jar",
    "netty-codec-4.1.97.Final.jar",
    "netty-common-4.1.97.Final.jar",
    "log4j-api-2.22.1.jar"
)
foreach ($p in $patterns) { $testLibs += (Find-Lib $p) }

$testCp = $testLibs -join ";"

$argFile = Join-Path $baseDir "build\ecj_test_args.txt"
$ecjArgs = @(
    "-21",
    "-nowarn",
    "-cp",
    "`"$testCp`"",
    "-d",
    "`"$testClassesDir`"",
    "`"$baseDir\src\main\java\com\jmchunkybridge\test\TestRunner.java`""
)
[System.IO.File]::WriteAllText($argFile, ($ecjArgs -join "`n"))

& $JavaExe -jar $ecjJar "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Test compile failed" }

Write-Output "=== Running TestRunner ==="
& $JavaExe -cp "$testClassesDir;$testCp" com.jmchunkybridge.test.TestRunner
if ($LASTEXITCODE -ne 0) { throw "Tests failed" }
