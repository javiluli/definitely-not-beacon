#requires -Version 7.4
<#
.SYNOPSIS
Genera los veintisiete recursos reproducibles del Beacon en build/, sin modificar los publicados.
.EXAMPLE
./tools/assets/generate-beacon-assets.ps1
Genera bloque, atlas, icono, modelos del haz, animación y copias de las seis texturas originales.
#>
param([string]$OutputRoot)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'AssetOutput.ps1')
$outputRoot = Resolve-BeaconAssetOutput $OutputRoot
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
& (Join-Path $PSScriptRoot 'generate-block-assets.ps1') -OutputRoot $outputRoot
& (Join-Path $PSScriptRoot 'generate-beam-assets.ps1') -OutputRoot $outputRoot
$icons = Join-Path $outputRoot 'Common/UI/Custom/Beacon'
New-Item -ItemType Directory -Path $icons -Force | Out-Null
$obsoleteIcon = Join-Path $icons 'regeneration.png'
if (Test-Path -LiteralPath $obsoleteIcon) { Remove-Item -LiteralPath $obsoleteIcon }
foreach ($power in 'speed','haste','resistance','jump_boost','strength') {
    Copy-Item -LiteralPath (Join-Path $projectRoot "public/beacon_effect_icons/$power.png") -Destination $icons
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'public/Beacon_Beam_(texture)_JE2.png') -Destination (Join-Path $outputRoot 'Common/VFX/Beacon/Beacon_Beam.png')
$logo = [Drawing.Bitmap]::new((Join-Path $projectRoot 'public/icon.png'))
$modIcon = [Drawing.Bitmap]::new(256, 256, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($modIcon)
try {
    $scale = [Math]::Min(256.0 / $logo.Width, 256.0 / $logo.Height)
    $width = [int][Math]::Round($logo.Width * $scale)
    $height = [int][Math]::Round($logo.Height * $scale)
    $graphics.Clear($logo.GetPixel(0, 0))
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.DrawImage($logo, [Drawing.Rectangle]::new((256 - $width) / 2, (256 - $height) / 2, $width, $height))
    $modIcon.Save((Join-Path $outputRoot 'icon-256.png'), [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $modIcon.Dispose()
    $logo.Dispose()
}
Write-Output "Beacon asset candidates: $outputRoot"
