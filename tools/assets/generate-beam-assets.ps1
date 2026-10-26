#requires -Version 7.4
<#
.SYNOPSIS
Genera modelos y animación del haz en build/, conservando la geometría de Hytale 0.7.4.
#>
param([string]$OutputRoot)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AssetOutput.ps1')
$outputFolder = Join-Path (Resolve-BeaconAssetOutput $OutputRoot) 'Common/VFX/Beacon'
New-Item -ItemType Directory -Path $outputFolder -Force | Out-Null
$identity = [ordered]@{ x = 0; y = 0; z = 0; w = 1 }
$zero = [ordered]@{ x = 0; y = 0; z = 0 }
$flowAnimation = [ordered]@{}
$loopFrames = 240
$unitsPerBlock = 64 # Entity models use 64 pixels per block; block models use 32.
$beamHeight = 4096
$nearSegmentHeight = 4
$nearSegmentCount = 16
foreach ($layer in @('Core','Halo')) {
    $nodes = @()
    # Minecraft's rotating core has radius 0.2; its fixed outer square is 0.5 wide.
    $width = if ($layer -eq 'Core') { 0.2 * [Math]::Sqrt(2) } else { 0.5 }
    $widthStretch = $width * $unitsPerBlock / 160
    for ($index = 0; $index -le $nearSegmentCount; $index++) {
        $bottom = $index * $nearSegmentHeight
        # Keep nearby texels unchanged; a single long segment reaches beyond normal view distance.
        $height = if ($index -eq $nearSegmentCount) { $beamHeight - $bottom } else { $nearSegmentHeight }
        $layout = [ordered]@{}
        # Open ends keep the translucent halo free of horizontal caps between segments.
        foreach ($face in @('front','back','left','right')) {
            $layout[$face] = [ordered]@{ offset = [ordered]@{x=0;y=0}; angle=0; mirror=[ordered]@{x=$false;y=$false} }
        }
        $name = "Beam$index"
        $nodes += [ordered]@{
            id = "$($index+1)"; name = $name; position = [ordered]@{x=0; y=($bottom+$height/2)*$unitsPerBlock; z=0}; orientation = $identity
            shape = [ordered]@{
                type='box'; offset=$zero; stretch=[ordered]@{x=$widthStretch;y=$height*$unitsPerBlock/160;z=$widthStretch}; settings=[ordered]@{size=[ordered]@{x=160;y=160;z=160}}
                visible=$true; doubleSided=$false; shadingMode='fullbright'; unwrapMode='custom'; textureLayout=$layout
            }; children=@()
        }
        $flow = @(
            [ordered]@{time=0;delta=[ordered]@{x=0;y=0};interpolationType='linear'},
            [ordered]@{time=$loopFrames;delta=[ordered]@{x=0;y=160};interpolationType='linear'}
        )
        # Match the official exporter: omitted channels can crash the client animation reader.
        $flowAnimation[$name] = [ordered]@{position=@();orientation=@();shapeStretch=@();shapeVisible=@();shapeUvOffset=$flow}
    }
    $root = [ordered]@{id='0';name='BeamRoot';position=$zero;orientation=$identity;shape=[ordered]@{type='none';offset=$zero;stretch=[ordered]@{x=1;y=1;z=1};settings=[ordered]@{};visible=$true;doubleSided=$false;shadingMode='fullbright';unwrapMode='custom';textureLayout=[ordered]@{}};children=$nodes}
    $model = [ordered]@{format='character';lod='auto';nodes=@($root)}
    if ($nodes.Count -ne 17 -or ($nodes[-1].position.y + $nodes[-1].shape.stretch.y*80) / $unitsPerBlock -ne $beamHeight) { throw 'Beam must span exactly 4096 blocks using 17 segments.' }
    if ([Math]::Abs($nodes[0].shape.stretch.x * 160 / $unitsPerBlock - $width) -gt 0.000001) { throw "Incorrect $layer width." }
    $previousTop = 0
    foreach ($node in $nodes) {
        $halfHeight = $node.shape.stretch.y * 80
        if ([Math]::Abs($node.position.y - $halfHeight - $previousTop) -gt 0.000001) { throw "Gap in $layer at $($node.name)." }
        $previousTop = $node.position.y + $halfHeight
    }
    $model | ConvertTo-Json -Depth 30 -Compress | Set-Content -LiteralPath (Join-Path $outputFolder "Beacon_Beam_$layer.blockymodel") -Encoding utf8
    if ($layer -eq 'Core') {
        $sectionFolder = Join-Path $outputFolder 'Sections'
        New-Item -ItemType Directory -Path $sectionFolder -Force | Out-Null
        # Retira únicamente candidatos obsoletos de este generador, dentro de build/.
        foreach ($obsolete in 14..16) {
            $candidate = Join-Path $sectionFolder "Beacon_Core_$obsolete.blockymodel"
            if (Test-Path -LiteralPath $candidate) { Remove-Item -LiteralPath $candidate }
        }
        $template = $model | ConvertTo-Json -Depth 30 -Compress
        for ($power = 0; $power -lt 14; $power++) {
            $piece = $template | ConvertFrom-Json -AsHashtable
            $top = (1 -shl $power) * 2 # Thirty-seconds of a block, expressed in model units (64/block).
            foreach ($node in $piece.nodes[0].children) {
                $bottom = $node.position.y - $node.shape.stretch.y * 80
                if ($bottom -ge $top) { $node.shape.visible = $false }
                elseif ($node.position.y + $node.shape.stretch.y * 80 -gt $top) {
                    $node.position.y = ($bottom + $top) / 2
                    $node.shape.stretch.y = ($top - $bottom) / 160
                }
            }
            # Keep every animation target, including hidden nodes beyond this piece's height.
            $piece | ConvertTo-Json -Depth 30 -Compress |
                Set-Content -LiteralPath (Join-Path $sectionFolder "Beacon_Core_$power.blockymodel") -Encoding utf8
        }
    }
}
[ordered]@{formatVersion=1;duration=$loopFrames;holdLastKeyframe=$false;nodeAnimations=$flowAnimation} |
    ConvertTo-Json -Depth 30 -Compress | Set-Content -LiteralPath (Join-Path $outputFolder 'Beacon_Beam_Flow.blockyanim') -Encoding utf8
Write-Output 'Generated 4096-block beam: 17 open segments per layer, 136 total faces; nearby texture detail, widths and flow preserved.'
