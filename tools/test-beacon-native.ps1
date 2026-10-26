<#
.SYNOPSIS
Compila y ejecuta las verificaciones nativas en un mundo desechable. Nunca instala el JAR de la fixture.
.EXAMPLE
./tools/test-beacon-native.ps1 -Profile
Incluye la entrada y las clases de profiling, y genera startup.jfr, workload.jfr y profile-results.csv.
.EXAMPLE
./tools/test-beacon-native.ps1 -AssetsPath C:/cache/release-0.7.4-Assets.zip -Offline
Usa assets locales y las dependencias ya resueltas por Gradle.
#>
param(
    [string]$GamePath = (Join-Path $env:APPDATA 'Hytale\install\release\package\game\latest'),
    [string]$AssetsPath,
    [switch]$Profile,
    [switch]$Offline,
    [string]$GradleUserHome
)

$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$serverJar = Join-Path $GamePath 'Server/HytaleServer.jar'
if (!$AssetsPath) { $AssetsPath = Join-Path $GamePath 'Assets.zip' }
if (!(Test-Path -LiteralPath $serverJar) -or !(Test-Path -LiteralPath $AssetsPath)) {
    throw 'Hytale not found. Pass -GamePath and, if needed, -AssetsPath.'
}
$serverJar = (Resolve-Path -LiteralPath $serverJar).Path
$assetsZip = (Resolve-Path -LiteralPath $AssetsPath).Path
Push-Location -LiteralPath $projectPath
try {
    $properties = Get-Content -LiteralPath gradle.properties -Raw | ConvertFrom-StringData
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($serverJar)
    try {
        $reader = [IO.StreamReader]::new($archive.GetEntry('META-INF/MANIFEST.MF').Open())
        try { $serverManifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $serverVersion = [regex]::Match($serverManifest, '(?m)^Implementation-Version: ([^\r\n]+)').Groups[1].Value.Trim()
        if ($serverVersion -ne $properties.hytale_version) {
            throw "Expected Hytale $($properties.hytale_version), found $serverVersion. The latest installation may have advanced."
        }
    } finally { $archive.Dispose() }
    & (Join-Path $PSScriptRoot 'testing/test-beacon-assets.ps1')

    $gradleArgs = @('build', 'compileIntegrationTestJava', '--console=plain', '-x', 'createManifestIfMissing', '-x', 'updatePluginManifest')
    if ($Profile) { $gradleArgs += 'compileProfilingJava' }
    if ($Offline) { $gradleArgs += '--offline' }
    if ($GradleUserHome) { $gradleArgs += '--gradle-user-home', $GradleUserHome }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification or fixture compilation failed.' }

    $jarName = "$($properties.mod_id)-ht$($properties.hytale_version)-$($properties.version)"
    $productionJar = Join-Path $projectPath "build/libs/$jarName.jar"
    & java -cp $serverJar tools/diagnostics/CheckBeaconCompatibility.java $productionJar
    if ($LASTEXITCODE -ne 0) { throw 'Native manifest compatibility check failed.' }

    $mode = if ($Profile) { 'profile' } else { 'native-test' }
    $testPath = Join-Path $projectPath ("build/beacon-$mode/" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Path "$testPath/mods" | Out-Null
    $fixtureJar = Join-Path $testPath "mods/$jarName-$mode.jar"
    Copy-Item -LiteralPath $productionJar -Destination $fixtureJar
    # El manifiesto de pruebas se deriva del JAR validado; el manifiesto fuente queda intacto.
    $archive = [IO.Compression.ZipFile]::OpenRead($productionJar)
    try {
        $reader = [IO.StreamReader]::new($archive.GetEntry('manifest.json').Open())
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
    $manifest.Main = if ($Profile) { 'dev.hytalemodding.beacon.BeaconProfilePlugin' } else { 'dev.hytalemodding.beacon.BeaconSmokePlugin' }
    $manifest | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath "$testPath/manifest.json" -Encoding utf8
    & jar --update --file $fixtureJar -C "$projectPath/build/classes/java/integrationTest" . -C $testPath manifest.json
    if ($LASTEXITCODE -ne 0) { throw 'Native fixture packaging failed.' }
    if ($Profile) {
        & jar --update --file $fixtureJar -C "$projectPath/build/classes/java/profiling" .
        if ($LASTEXITCODE -ne 0) { throw 'Profiling fixture packaging failed.' }
    }
    '{"users":{},"groups":{}}' | Set-Content -LiteralPath "$testPath/permissions.json" -Encoding utf8
    $jvmOptions = @('-Xmx2G')
    if ($Profile) { $jvmOptions += '-XX:StartFlightRecording=name=BeaconStartup,settings=profile,filename=startup.jfr,dumponexit=true' }
    Write-Output "Native run: $testPath"
    Set-Location -LiteralPath $testPath
    & java @jvmOptions -jar $serverJar --assets $assetsZip --auth-mode offline --bind 127.0.0.1:0 --disable-sentry *> smoke-test.log
    if ($LASTEXITCODE -ne 0) { throw "Native server failed. See $testPath/smoke-test.log" }
    $result = Get-Content -LiteralPath smoke-result.txt -Raw
    if (!$result.StartsWith('PASS')) { throw $result }
    if ($Profile) {
        foreach ($recording in 'startup.jfr','workload.jfr','profile-results.csv') {
            if (!(Test-Path -LiteralPath $recording) -or (Get-Item -LiteralPath $recording).Length -eq 0) {
                throw "Profiling output missing or empty: $recording"
            }
        }
    }
    Write-Output $result
    Write-Output "Results: $testPath"
} finally {
    Pop-Location
}
