param([string]$MapJar,[ValidateSet('1.20.1','1.21.1')][string]$MinecraftVersion='1.20.1',[string]$InstancePath)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$mapVersion=if($MinecraftVersion -eq '1.21.1') {'1.44.2'} else {'1.47.0'}
$libVersion=if($MinecraftVersion -eq '1.21.1') {'1.7.1'} else {'1.7.3'}
if(!$MapJar -and $InstancePath) {
    $MapJar=(Get-ChildItem -LiteralPath (Join-Path $InstancePath 'mods') -File | Where-Object { $_.Name -match ('xaeroworldmap.*'+[regex]::Escape($MinecraftVersion)+'.*'+[regex]::Escape($mapVersion)) } | Select-Object -First 1).FullName
}
if(!$MapJar -or !(Test-Path -LiteralPath $MapJar -PathType Leaf)) { throw ('Specify -MapJar or -InstancePath for Fabric '+$MinecraftVersion+', Xaero World Map '+$mapVersion) }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $MapJar).Path)
try {
    $reader=[IO.StreamReader]::new($archive.GetEntry('fabric.mod.json').Open())
    try { $metadata=$reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if($metadata.id -ne 'xaeroworldmap' -or $metadata.version -ne $mapVersion) { throw ('Expected Xaero World Map '+$mapVersion) }
    $entry=$archive.GetEntry('META-INF/jars/xaerolib-fabric-'+$MinecraftVersion+'-'+$libVersion+'.jar')
    if(!$entry) { throw 'Wrong Minecraft version or missing embedded XaeroLib' }
    $libs=Join-Path $projectRoot $(if($MinecraftVersion -eq '1.21.1') {'libs/1.21.1'} else {'libs'})
    [IO.Directory]::CreateDirectory($libs) | Out-Null
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libs ('xaerolib-'+$libVersion+'.jar')),$true)
    $target=Join-Path $libs ('xaeroworldmap-'+$mapVersion+'.jar')
    if([IO.Path]::GetFullPath($MapJar) -ne [IO.Path]::GetFullPath($target)) { Copy-Item -LiteralPath $MapJar -Destination $target }
} finally { $archive.Dispose() }
Write-Output ('Prepared compile-only dependencies for '+$MinecraftVersion)