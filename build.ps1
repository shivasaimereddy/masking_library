$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$compilerPath = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
if ($env:JAVA_HOME -and !(Test-Path -LiteralPath $compilerPath) -and
    (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'javac.exe'))) {
    $compilerPath = Join-Path $env:JAVA_HOME 'javac.exe'
}
$jdkBin = Split-Path -Parent $compilerPath

$jarPath = Join-Path $jdkBin 'jar.exe'
$version = '1.0.0'
# A fresh output directory prevents stale classes without deleting existing files.
$buildPath = Join-Path $PSScriptRoot ('build/run-' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $buildPath 'classes'

New-Item -ItemType Directory -Force -Path $classes,(Join-Path $PSScriptRoot 'dist') | Out-Null
$compilerVersion = (& $compilerPath -version 2>&1 | Out-String)
$options = if ($compilerVersion -match 'javac 1\.8\.') { @('-source','8','-target','8') } else { @('--release','8') }
$mainSources = @(Get-ChildItem -LiteralPath src/main/java -Recurse -Filter *.java | ForEach-Object FullName)
& $compilerPath @options -encoding UTF-8 -Xlint:all -d $classes @mainSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$manifest = Join-Path $buildPath 'manifest.txt'
"Automatic-Module-Name: com.infyss.projects`nImplementation-Version: $version`n" | Set-Content -LiteralPath $manifest -Encoding ascii
& $jarPath cfm "dist/masking-$version.jar" $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Output "built dist/masking-$version.jar (Java 8+)"
