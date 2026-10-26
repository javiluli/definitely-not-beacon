#requires -Version 7.4
<#
.SYNOPSIS
Ensaya reproducción entre procesos y fallos del pipeline usando solo copias desechables en build/.
#>
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
. (Join-Path $projectRoot 'tools/assets/AssetOutput.ps1')
$testRoot = Resolve-BeaconAssetOutput ('build/asset-pipeline-tests/' + [guid]::NewGuid().ToString('N'))
$generator = Join-Path $projectRoot 'tools/assets/generate-beacon-assets.ps1'
$validator = Join-Path $PSScriptRoot 'test-beacon-assets.ps1'

function Assert-Rejected([scriptblock]$Action, [string]$Message) {
    try { & $Action | Out-Null }
    catch {
        if (!$_.Exception.Message.StartsWith($Message)) { throw }
        return
    }
    throw "Expected rejection: $Message"
}

foreach ($invalid in 'src/main/resources','build/../src/main/resources','build') {
    Assert-Rejected { Resolve-BeaconAssetOutput $invalid } 'Asset output must be'
}
$first = Join-Path $testRoot 'first'
$second = Join-Path $testRoot 'second'
& $generator -OutputRoot $first
& pwsh -NoProfile -File $generator -OutputRoot $second
if ($LASTEXITCODE -ne 0) { throw 'Second generation failed.' }
$files = @(Get-ChildItem -LiteralPath $first -Recurse -File)
if ($files.Count -ne 27) { throw 'Missing generated candidates.' }
foreach ($file in $files) {
    $other = Join-Path $second ([IO.Path]::GetRelativePath($first, $file.FullName))
    if ((Get-FileHash -LiteralPath $file.FullName).Hash -ne (Get-FileHash -LiteralPath $other).Hash) {
        throw "Generation differs between processes: $($file.Name)"
    }
}
& $validator -CandidateRoot $first

$generatedModel = Join-Path $first 'Common/VFX/Beacon/Beacon_Beam_Core.blockymodel'
$original = [IO.File]::ReadAllBytes($generatedModel)
try {
    $data = Get-Content -LiteralPath $generatedModel -Raw | ConvertFrom-Json
    $data.nodes[0].name = 'ChangedRoot'
    $data | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $generatedModel -Encoding utf8
    Assert-Rejected { & $validator -CandidateRoot $first } 'Generated asset differs from published asset:'
} finally { [IO.File]::WriteAllBytes($generatedModel, $original) }

$resourceCopy = Join-Path $testRoot 'resources'
Copy-Item -LiteralPath (Join-Path $projectRoot 'src/main/resources') -Destination $resourceCopy -Recurse
$modIcon = Join-Path $resourceCopy 'icon-256.png'
$originalIcon = [IO.File]::ReadAllBytes($modIcon)
try {
    Remove-Item -LiteralPath $modIcon
    Assert-Rejected { & $validator -ResourceRoot $resourceCopy -CandidateRoot $first } 'Missing published asset: icon-256.png'
} finally { [IO.File]::WriteAllBytes($modIcon, $originalIcon) }
$model = @(Get-ChildItem -LiteralPath (Join-Path $resourceCopy 'Server/Models') -Filter Beacon_Beam_Core.json -Recurse -File)[0].FullName
$original = [IO.File]::ReadAllBytes($model)
try {
    $data = Get-Content -LiteralPath $model -Raw | ConvertFrom-Json
    $data.Texture = 'VFX/Beacon/Missing.png'
    $data | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $model -Encoding utf8
    Assert-Rejected { & $validator -ResourceRoot $resourceCopy -CandidateRoot $first } 'Missing Common resource in'
    [IO.File]::WriteAllBytes($model, $original)
    $data = Get-Content -LiteralPath $model -Raw | ConvertFrom-Json
    $data.HitBox.Max.Y = 4095
    $data | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $model -Encoding utf8
    Assert-Rejected { & $validator -ResourceRoot $resourceCopy -CandidateRoot $first } 'Generated beam height and server model bounds disagree.'
} finally { [IO.File]::WriteAllBytes($model, $original) }

$duplicate = Join-Path $resourceCopy 'Server/Models/Duplicate'
New-Item -ItemType Directory -Path $duplicate | Out-Null
Copy-Item -LiteralPath $model -Destination $duplicate
Assert-Rejected { & $validator -ResourceRoot $resourceCopy -CandidateRoot $first } 'Duplicate asset IDs in'
Write-Output 'PASS: repeatable generation, output boundaries, missing mod icon, changed models, missing textures, beam bounds and duplicate IDs.'
Write-Output "Pipeline test files: $testRoot"
