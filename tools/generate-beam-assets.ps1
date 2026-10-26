<#
.SYNOPSIS
Entrada compatible del generador del haz. Los candidatos se escriben en build/, no se publican.
#>
param([string]$OutputRoot)
& (Join-Path $PSScriptRoot 'assets/generate-beam-assets.ps1') -OutputRoot $OutputRoot
