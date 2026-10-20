param([string]$MapJar)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (!$MapJar) {
    $mods = Join-Path (Split-Path -Parent $projectRoot) 'mods'
    if (Test-Path -LiteralPath $mods) {
        $MapJar = (Get-ChildItem -LiteralPath $mods -File | Where-Object Name -Match 'xaeroworldmap.*1\.47\.0' | Select-Object -First 1).FullName
    }
}
if (!$MapJar -or !(Test-Path -LiteralPath $MapJar -PathType Leaf)) {
    throw '请用 -MapJar 指定 Xaero World Map Fabric 1.20.1 / 1.47.0 的 jar 文件。'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $MapJar).Path)
try {
    $reader = [IO.StreamReader]::new($archive.GetEntry('fabric.mod.json').Open())
    try { $meta = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if ($meta.id -ne 'xaeroworldmap' -or $meta.version -ne '1.47.0') { throw '需要 Xaero World Map 1.47.0。' }
    $entry = $archive.GetEntry('META-INF/jars/xaerolib-fabric-1.20.1-1.7.3.jar')
    if (!$entry) { throw '未找到适用的 XaeroLib，请使用 Fabric 1.20.1 发行文件。' }
    $libs = Join-Path $projectRoot 'libs'
    New-Item -ItemType Directory -Path $libs -Force | Out-Null
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libs 'xaerolib-1.7.3.jar'),$true)
    $target = Join-Path $libs 'xaeroworldmap-1.47.0.jar'
    if ([IO.Path]::GetFullPath($MapJar) -ne [IO.Path]::GetFullPath($target)) { Copy-Item -LiteralPath $MapJar -Destination $target }
} finally { $archive.Dispose() }
Write-Output 'Xaero 编译依赖已准备；不会打包进此地名曰。'
