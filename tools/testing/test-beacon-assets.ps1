#requires -Version 7.4
<#
.SYNOPSIS
Genera candidatos y comprueba su equivalencia, las referencias de UI y los canales de animación.
No modifica recursos publicados ni arranca Hytale.
#>
param([string]$ResourceRoot, [string]$CandidateRoot)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
if (!$ResourceRoot) { $ResourceRoot = Join-Path $projectPath 'src/main/resources' }
$ResourceRoot = (Resolve-Path -LiteralPath $ResourceRoot).Path
if (!$CandidateRoot) {
    & (Join-Path $projectPath 'tools/assets/generate-beacon-assets.ps1')
    $CandidateRoot = Join-Path $projectPath 'build/generated-assets/beacon'
}
$CandidateRoot = (Resolve-Path -LiteralPath $CandidateRoot).Path
Push-Location -LiteralPath $projectPath
try {
    $candidates = @(Get-ChildItem -LiteralPath $CandidateRoot -Recurse -File)
    if ($candidates.Count -ne 27) { throw 'Expected twenty-seven generated Beacon assets.' }
    foreach ($file in $candidates) {
        $relative = [IO.Path]::GetRelativePath($CandidateRoot, $file.FullName)
        $published = Join-Path $ResourceRoot $relative
        if (!(Test-Path -LiteralPath $published -PathType Leaf)) { throw "Missing published asset: $relative" }
        if ($file.Extension -in '.blockymodel','.blockyanim') {
            $candidateJson = [System.Text.Json.Nodes.JsonNode]::Parse([IO.File]::ReadAllText($file.FullName))
            $publishedJson = [System.Text.Json.Nodes.JsonNode]::Parse([IO.File]::ReadAllText($published))
            $same = [System.Text.Json.Nodes.JsonNode]::DeepEquals($candidateJson, $publishedJson)
        } else { $same = (Get-FileHash -LiteralPath $file.FullName).Hash -eq (Get-FileHash -LiteralPath $published).Hash }
        if (!$same) { throw "Generated asset differs from published asset: $relative" }
    }
    Write-Output 'PASS: twenty-seven generated assets match the published JSON and PNG files.'
    $beaconUi = Get-Content -LiteralPath (Join-Path $ResourceRoot 'Common/UI/Custom/Pages/Beacon.ui') -Raw
    if ($beaconUi -match '#[A-Za-z][A-Za-z0-9]*_') { throw 'Custom UI element IDs must be alphanumeric (no underscores).' }
    if ($beaconUi -match '%[A-Za-z][A-Za-z0-9.]*_') {
        throw 'Custom UI translation references cannot contain underscores; use Message.translation from Java for these keys.'
    }
    if ($beaconUi -match '(?m)^\s*Anchor:\s*\([^;]+;\s*@[A-Za-z]+\s*=') {
        throw 'Custom UI template parameters must precede element properties.'
    }
    foreach ($beaconAnchor in [regex]::Matches($beaconUi, '\bAnchor:\s*\(([^)]*)\)')) {
        foreach ($beaconField in [regex]::Matches($beaconAnchor.Groups[1].Value, '(\w+)\s*:')) {
            if ($beaconField.Groups[1].Value -notin 'Left','Right','Top','Bottom','Height','Full','Horizontal','Vertical','Width','MinWidth','MaxWidth') {
                throw "Unsupported Hytale Anchor property: $($beaconField.Groups[1].Value)"
            }
        }
    }
    $customUiRoot = [IO.Path]::GetFullPath((Join-Path $ResourceRoot 'Common/UI/Custom'))
    foreach ($texture in [regex]::Matches($beaconUi, 'TexturePath:\s*"([^"]+)"')) {
        $texturePath = [IO.Path]::GetFullPath((Join-Path "$customUiRoot/Pages" $texture.Groups[1].Value))
        if (!$texturePath.StartsWith($customUiRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Custom UI texture escapes its asset root: $texturePath"
        }
        $texture2xPath = $texturePath -replace '\.png$', '@2x.png'
        if (!(Test-Path -LiteralPath $texturePath) -and !(Test-Path -LiteralPath $texture2xPath)) { throw "Missing beacon UI texture: $texturePath" }
    }
    Write-Output 'PASS: beacon UI element IDs, translation references, supported Anchor properties and bundled texture paths.'
    # The server does not parse common animations; require the client's complete channel layout.
    $beamAnimation = Get-Content -LiteralPath (Join-Path $ResourceRoot 'Common/VFX/Beacon/Beacon_Beam_Flow.blockyanim') -Raw | ConvertFrom-Json
    foreach ($beamLayer in 'Core','Halo') {
        $beamModel = Get-Content -LiteralPath (Join-Path $ResourceRoot "Common/VFX/Beacon/Beacon_Beam_$beamLayer.blockymodel") -Raw | ConvertFrom-Json
        $beamNodeNames = @($beamModel.nodes[0].children | ForEach-Object name)
        foreach ($beamTrack in $beamAnimation.nodeAnimations.psobject.Properties) {
            if ($beamTrack.Name -notin $beamNodeNames) { throw "Animation targets missing $beamLayer node: $($beamTrack.Name)" }
            foreach ($beamChannel in 'position','orientation','shapeStretch','shapeVisible','shapeUvOffset') {
                $beamProperty = $beamTrack.Value.psobject.Properties[$beamChannel]
                if ($null -eq $beamProperty -or $beamProperty.Value -isnot [Array]) {
                    throw "Incomplete client animation: $($beamTrack.Name).$beamChannel must be an array (empty is valid)."
                }
            }
        }
    }
    Write-Output 'PASS: beam animation channels and model targets.'
    for ($power = 0; $power -lt 14; $power++) {
        $piece = Get-Content -LiteralPath (Join-Path $ResourceRoot "Common/VFX/Beacon/Sections/Beacon_Core_$power.blockymodel") -Raw | ConvertFrom-Json
        $names = @($piece.nodes[0].children | ForEach-Object name)
        foreach ($track in $beamAnimation.nodeAnimations.psobject.Properties) {
            if ($track.Name -notin $names) { throw "Missing animation target in core piece $power" }
        }
        $top = 0.0
        foreach ($node in $piece.nodes[0].children | Where-Object { $_.shape.visible }) {
            $half = $node.shape.stretch.y * 80
            if ([Math]::Abs($node.position.y - $half - $top) -gt 0.000001 -or $node.shape.shadingMode -ne 'fullbright') {
                throw "Invalid geometry or shading in core piece $power"
            }
            $top = $node.position.y + $half
        }
        if ([Math]::Abs($top - (1 -shl $power) * 2) -gt 0.000001) { throw "Wrong core piece height: $power" }
    }
    Write-Output 'PASS: fourteen reusable core pieces have exact heights, fullbright shading and complete animation targets.'
    foreach ($folder in 'Item/Items','Models','Entity/Effects','Entity/ModelVFX') {
        $duplicates = @(Get-ChildItem -LiteralPath (Join-Path $ResourceRoot "Server/$folder") -Filter *.json -Recurse -File |
                Group-Object BaseName -CaseSensitive | Where-Object Count -gt 1)
        if ($duplicates) { throw "Duplicate asset IDs in Server/$folder : $($duplicates.Name -join ', ')" }
    }
    foreach ($asset in Get-ChildItem -LiteralPath (Join-Path $ResourceRoot 'Server') -Filter *.json -Recurse -File) {
        foreach ($reference in [regex]::Matches([IO.File]::ReadAllText($asset.FullName), '"((?:Icons|Blocks|BlockTextures|VFX|UI)/[^"]+\.(?:png|blockymodel|blockyanim))"')) {
            # Dependencia vanilla comprobada en el registro Common por la fixture nativa.
            if ($reference.Groups[1].Value -eq 'UI/StatusEffects/AddHealth/Tiny.png') { continue }
            $common = Join-Path $ResourceRoot ('Common/' + $reference.Groups[1].Value)
            if (!(Test-Path -LiteralPath $common -PathType Leaf)) { throw "Missing Common resource in $($asset.Name): $common" }
        }
    }
    $beamConfig = @(Get-ChildItem -LiteralPath (Join-Path $ResourceRoot 'Server/Models') -Filter Beacon_Beam_Core.json -Recurse -File)
    if ($beamConfig.Count -ne 1) { throw 'Expected one server beam core model.' }
    $bounds = Get-Content -LiteralPath $beamConfig[0].FullName -Raw | ConvertFrom-Json
    $haloModel = Get-Content -LiteralPath (Join-Path $ResourceRoot 'Common/VFX/Beacon/Beacon_Beam_Halo.blockymodel') -Raw | ConvertFrom-Json
    $top = $haloModel.nodes[0].children[-1]
    $height = ($top.position.y + $top.shape.stretch.y * $top.shape.settings.size.y / 2) / 64
    if ($height -ne $bounds.HitBox.Max.Y) { throw 'Generated beam height and server model bounds disagree.' }
    Write-Output 'PASS: unique server IDs, Common resource references and beam bounds.'
} finally {
    Pop-Location
}
