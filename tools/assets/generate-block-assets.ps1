#requires -Version 7.4
<#
.SYNOPSIS
Convierte las fuentes de art/beacon/reference en el modelo, atlas e icono vigentes.
Los candidatos quedan en build/; el núcleo conserva el fullbright del recurso publicado.
#>
param([string]$OutputRoot)
. (Join-Path $PSScriptRoot 'AssetOutput.ps1')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$referenceRoot = Join-Path $projectRoot 'art/beacon/reference'
$outputRoot = Resolve-BeaconAssetOutput $OutputRoot
$modelDirectory = Join-Path $outputRoot 'Common/Blocks/Beacon'
$iconDirectory = Join-Path $outputRoot 'Common/Icons/Items'
New-Item -ItemType Directory -Path $modelDirectory, $iconDirectory -Force | Out-Null
$minecraftModel = Get-Content -Raw -LiteralPath (Join-Path $referenceRoot 'beacon.json') | ConvertFrom-Json
$atlasOffsets = [ordered]@{ glass = 0; obsidian = 32; beacon = 64 }
$faceNames = [ordered]@{ down = 'bottom'; up = 'top'; north = 'back'; south = 'front'; west = 'left'; east = 'right' }
$textureImages = [ordered]@{}
$atlas = [Drawing.Bitmap]::new(96, 32, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$atlasGraphics = [Drawing.Graphics]::FromImage($atlas)
$atlasGraphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
$atlasGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$atlasGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
try {
    foreach ($textureName in @('glass', 'obsidian', 'beacon')) {
        $sourceFile = Join-Path $referenceRoot "$textureName.png"
        $textureImages[$textureName] = [Drawing.Bitmap]::new($sourceFile)
        if ($textureImages[$textureName].Width -ne 16 -or $textureImages[$textureName].Height -ne 16) { throw 'Expected original 16x16 Minecraft textures' }
        $atlasGraphics.DrawImage($textureImages[$textureName], [Drawing.Rectangle]::new($atlasOffsets[$textureName], 0, 32, 32), 0, 0, 16, 16, [Drawing.GraphicsUnit]::Pixel)
    }
    $atlas.Save((Join-Path $modelDirectory 'Beacon_Texture.png'), [Drawing.Imaging.ImageFormat]::Png)

    $nodes = @()
    $iconFaces = @()
    foreach ($element in $minecraftModel.elements) {
        $from = $element.from
        $to = $element.to
        $width = [int](($to[0] - $from[0]) * 2)
        $height = [int][Math]::Ceiling(($to[1] - $from[1]) * 2)
        $depth = [int](($to[2] - $from[2]) * 2)
        $textureLayout = [ordered]@{}
        $isGlass = $element.__comment -eq 'Glass shell'
        foreach ($faceProperty in $element.faces.PSObject.Properties) {
            $face = $faceProperty.Name
            $uv = $faceProperty.Value.uv
            $textureName = $faceProperty.Value.texture.TrimStart('#')
            $textureLayout[$faceNames[$face]] = [ordered]@{
                offset = [ordered]@{ x = $atlasOffsets[$textureName] + $uv[0] * 2; y = $uv[1] * 2 }
                mirror = [ordered]@{ x = $false; y = $false }
                angle = 0
            }
            $uvWidth = ($uv[2] - $uv[0]) * 2
            $uvHeight = ($uv[3] - $uv[1]) * 2
            $expectedWidth = if ($face -in @('west', 'east')) { $depth } else { $width }
            $expectedHeight = if ($face -in @('up', 'down')) { $depth } else { $height }
            if ($uvWidth -ne $expectedWidth -or $uvHeight -ne $expectedHeight) { throw "UV dimensions do not match $($element.__comment) $face" }
            if ($textureLayout[$faceNames[$face]].offset.x + $uvWidth -gt 96 -or $uv[3] * 2 -gt 32) { throw 'UV coordinates exceed the texture atlas' }

            if (-not $isGlass -and $face -notin @('up', 'south', 'east')) { continue }
            $x0 = $from[0] * 2 - 16; $x1 = $to[0] * 2 - 16
            $y0 = $from[1] * 2; $y1 = $to[1] * 2
            $z0 = $from[2] * 2 - 16; $z1 = $to[2] * 2 - 16
            $corners = switch ($face) {
                up    { @(@($x0,$y1,$z0), @($x1,$y1,$z0), @($x0,$y1,$z1)) }
                down  { @(@($x0,$y0,$z0), @($x1,$y0,$z0), @($x0,$y0,$z1)) }
                south { @(@($x0,$y1,$z1), @($x1,$y1,$z1), @($x0,$y0,$z1)) }
                north { @(@($x1,$y1,$z0), @($x0,$y1,$z0), @($x1,$y0,$z0)) }
                east  { @(@($x1,$y1,$z1), @($x1,$y1,$z0), @($x1,$y0,$z1)) }
                west  { @(@($x0,$y1,$z0), @($x0,$y1,$z1), @($x0,$y0,$z0)) }
            }
            $center = @(0..2 | ForEach-Object { ($corners[1][$_] + $corners[2][$_]) / 2 })
            # Hashtable conserva el recorrido de pintado del icono publicado en el pipeline de PowerShell.
            $iconFaces += @{
                corners = $corners; uv = $uv; texture = $textureName
                depth = $center[0] + $center[2] + 0.842105 * $center[1]
                shade = if ($face -eq 'up') { 1.0 } elseif ($face -in @('east','west')) { 0.7 } else { 0.85 }
            }
        }
        $nodes += [ordered]@{
            id = "$($nodes.Count)"; name = $element.__comment; children = @()
            position = [ordered]@{ x = $from[0] + $to[0] - 16; y = $from[1] + $to[1]; z = $from[2] + $to[2] - 16 }
            orientation = [ordered]@{ x = 0; y = 0; z = 0; w = 1 }
            shape = [ordered]@{
                type = 'box'; offset = [ordered]@{ x = 0; y = 0; z = 0 }
                stretch = [ordered]@{ x = 1; y = ($to[1] - $from[1]) * 2 / $height; z = 1 }
                settings = [ordered]@{ size = [ordered]@{ x = $width; y = $height; z = $depth } }
                visible = $true; doubleSided = $isGlass; shadingMode = if ($element.__comment -eq 'Inner beacon texture') { 'fullbright' } else { 'standard' }
                unwrapMode = 'custom'; textureLayout = $textureLayout
            }
        }
    }
    if ($nodes.Count -ne 3 -or $nodes[0].shape.settings.size.x -ne 32 -or $nodes[2].shape.settings.size.y -ne 22) { throw 'Unexpected Beacon geometry' }
    [ordered]@{ nodes = $nodes; lod = 'auto' } | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $modelDirectory 'Beacon.blockymodel') -Encoding utf8

    $icon = [Drawing.Bitmap]::new(64, 64, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $iconGraphics = [Drawing.Graphics]::FromImage($icon)
    $iconGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $iconGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    try {
        foreach ($iconFace in ($iconFaces | Sort-Object depth)) {
            $points = [Drawing.PointF[]]@($iconFace.corners | ForEach-Object { [Drawing.PointF]::new(32 + ($_[0] - $_[2]) * 0.8, 48 - $_[1] * 0.95 + ($_[0] + $_[2]) * 0.4) })
            $uv = $iconFace.uv
            $attributes = [Drawing.Imaging.ImageAttributes]::new()
            try {
                $matrix = [Drawing.Imaging.ColorMatrix]::new()
                $matrix.Matrix00 = $iconFace.shade; $matrix.Matrix11 = $iconFace.shade; $matrix.Matrix22 = $iconFace.shade
                $attributes.SetColorMatrix($matrix)
                $iconGraphics.DrawImage($textureImages[$iconFace.texture], $points, [Drawing.RectangleF]::new($uv[0], $uv[1], $uv[2] - $uv[0], $uv[3] - $uv[1]), [Drawing.GraphicsUnit]::Pixel, $attributes)
            } finally { $attributes.Dispose() }
        }
        if ($icon.GetPixel(0,0).A -ne 0 -or $icon.GetPixel(32,32).A -eq 0) { throw 'Beacon icon is empty or lost transparency' }
        $icon.Save((Join-Path $iconDirectory 'Beacon_Block_Icon.png'), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $iconGraphics.Dispose(); $icon.Dispose() }
} finally {
    $atlasGraphics.Dispose(); $atlas.Dispose()
    foreach ($textureImage in $textureImages.Values) { $textureImage.Dispose() }
}
Write-Output 'Beacon model, 96x32 atlas and 64x64 transparent icon generated and checked.'
