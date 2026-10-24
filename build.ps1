param(
    [string]$ModsDir = "",
    [string]$LibrariesDir = "$env:APPDATA\ModrinthApp\meta\libraries",
    [string]$JavaExe = "$env:APPDATA\ModrinthApp\meta\java_versions\zulu21.52.203-ca-jre21.0.12.1-win_x64\bin\java.exe",
    [switch]$SkipDeploy
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression

$ErrorActionPreference = "Stop"
$baseDir = $PSScriptRoot
$ecjJar = Join-Path $baseDir "build_tools\ecj.jar"

if (-not (Test-Path $ecjJar)) {
    Write-Output "Downloading Eclipse ECJ 3.38.0..."
    New-Item -ItemType Directory -Path (Split-Path $ecjJar -Parent) -Force | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar" -OutFile $ecjJar -UseBasicParsing
}
if (-not (Test-Path $JavaExe)) {
    throw "Java 21 runtime not found at $JavaExe - pass -JavaExe"
}

if ([string]::IsNullOrWhiteSpace($ModsDir)) {
    $ModsDir = Join-Path (Split-Path $baseDir -Parent) "mods"
}

$dependencyJars = @(
    "journeymap-neoforge-1.21.1-6.0.9.jar",
    "jmsharedmaps-1.7.1-1.21.1-neoforge.jar",
    "Chunky-NeoForge-1.4.23.jar"
)

Write-Output "=== Building JM Chunky Bridge ==="

$buildDir = Join-Path $baseDir "build"
$classesDir = Join-Path $buildDir "classes"
$jarName = "jmchunkybridge-1.0.0-neoforge-1.21.1.jar"
$jarOut = Join-Path $buildDir $jarName

if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$jars = Get-ChildItem -Path $LibrariesDir -Recurse -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object {
    $_.FullName -notmatch '1\.20' -and
    $_.FullName -notmatch '26\.3' -and
    $_.FullName -notmatch '12\.0' -and
    $_.FullName -notmatch '21\.1\.233' -and
    $_.FullName -notmatch '21\.1\.247' -and
    $_.FullName -notmatch 'slim' -and
    $_.FullName -notmatch 'neoforge-.*-client\.jar' -and
    $_.FullName -notmatch 'loader-4\.0\.42' -and
    $_.FullName -notmatch 'loader-4\.0\.43'
} | ForEach-Object { $_.FullName }

$modJars = @()
foreach ($dep in $dependencyJars) {
    $full = Join-Path $ModsDir $dep
    if (Test-Path $full) {
        $modJars += (Get-Item $full).FullName
    } else {
        Write-Warning "Dependency jar not found: $full"
    }
}

$cp = ($jars + $modJars) -join ";"

$javaFiles = Get-ChildItem -Path "$baseDir\src\main\java" -Recurse -Filter '*.java' | ForEach-Object { "`"$($_.FullName)`"" }
Write-Output "Compiling $($javaFiles.Count) source files..."

$argFile = Join-Path $buildDir "ecj_args.txt"
$ecjArgs = @("-21", "-nowarn", "-cp", "`"$cp`"", "-d", "`"$classesDir`"") + $javaFiles
[System.IO.File]::WriteAllText($argFile, ($ecjArgs -join "`n"))

& $JavaExe -jar $ecjJar "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "Compilation failed with exit code $LASTEXITCODE" }

$resDir = Join-Path $baseDir "src\main\resources"
if (Test-Path $resDir) {
    Copy-Item -Path "$resDir\*" -Destination $classesDir -Recurse -Force
}

if (Test-Path $jarOut) { Remove-Item $jarOut -Force }

$zipStream = [System.IO.File]::Create($jarOut)
$archive = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
foreach ($file in (Get-ChildItem -Path $classesDir -Recurse -File)) {
    $relPath = $file.FullName.Substring($classesDir.Length).TrimStart("\", "/").Replace("\", "/")
    $entry = $archive.CreateEntry($relPath, [System.IO.Compression.CompressionLevel]::Optimal)
    $entryStream = $entry.Open()
    $fileStream = [System.IO.File]::OpenRead($file.FullName)
    $fileStream.CopyTo($entryStream)
    $fileStream.Close()
    $entryStream.Close()
}
$archive.Dispose()
$zipStream.Close()

Write-Output "Built $jarOut ($((Get-Item $jarOut).Length) bytes)"

if (-not $SkipDeploy) {
    Copy-Item -Path $jarOut -Destination (Join-Path $ModsDir $jarName) -Force
    Write-Output "Deployed to $ModsDir"
}

Write-Output "=== Done ==="
