# La generación nunca publica archivos: todos los candidatos quedan dentro de build/.
function Resolve-BeaconAssetOutput([string]$OutputRoot) {
    $projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
    if (!$OutputRoot) { $OutputRoot = 'build/generated-assets/beacon' }
    $path = if ([IO.Path]::IsPathRooted($OutputRoot)) { [IO.Path]::GetFullPath($OutputRoot) }
            else { [IO.Path]::GetFullPath((Join-Path $projectRoot $OutputRoot)) }
    $buildRoot = Join-Path $projectRoot 'build'
    if (!$path.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Asset output must be a directory inside build/. Review candidates before copying them into src/main/resources.'
    }
    return $path
}
