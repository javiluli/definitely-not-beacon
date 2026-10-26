<#
.SYNOPSIS
Conserva la ruta histórica del conversor; delega en el generador que reproduce el bloque vigente.
#>
param([string]$OutputRoot = 'build/generated-assets/legacy-beacon')
& (Join-Path $PSScriptRoot '../generate-block-assets.ps1') -OutputRoot $OutputRoot
