param(
    [Parameter(Mandatory=$true)][string]$SourceInstance,
    [Parameter(Mandatory=$true)][string]$Destination,
    [string]$ModVersion='0.3.0'
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$source=(Resolve-Path -LiteralPath $SourceInstance).Path
$target=[IO.Path]::GetFullPath($Destination)
if(Test-Path -LiteralPath $target) { throw 'Choose a new test instance directory; existing instances are never overwritten' }
$projectRoot=Split-Path -Parent $PSScriptRoot
$version=Get-Content -LiteralPath (Join-Path $source ((Split-Path -Leaf $source)+'.json')) -Raw | ConvertFrom-Json
if($version.clientVersion -ne '1.21.1' -or $version.mainClass -notmatch 'fabric') { throw 'Source must be an installed Fabric 1.21.1 instance' }
$artifact=Join-Path $projectRoot ('dist/hereby-named-'+$ModVersion+'+1.21.1.jar')
if(!(Test-Path -LiteralPath $artifact)) { throw 'Export the mod release first' }
$providers=@{}
$roots=@{}
function Read-ModMetadata($archive,[string]$root,[string]$rootId='') {
    $entry=$archive.GetEntry('fabric.mod.json')
    if(!$entry) { return }
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $metadata=$reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    if(!$rootId) { $rootId=$metadata.id }
    $item=[pscustomobject]@{Root=$root;RootId=$rootId;Metadata=$metadata}
    if(!$roots.ContainsKey($root)) { $roots[$root]=[Collections.Generic.List[object]]::new() }
    $roots[$root].Add($item)
    foreach($id in @($metadata.id)+@($metadata.provides)) {
        if(!$id) { continue }
        if(!$providers.ContainsKey($id)) { $providers[$id]=[Collections.Generic.List[object]]::new() }
        $providers[$id].Add($item)
    }
    foreach($nested in $metadata.jars) {
        $nestedEntry=$archive.GetEntry($nested.file)
        if(!$nestedEntry) { throw ('Missing nested mod: '+$nested.file) }
        $memory=[IO.MemoryStream]::new();$stream=$nestedEntry.Open()
        try {
            $stream.CopyTo($memory);$memory.Position=0
            $nestedArchive=[IO.Compression.ZipArchive]::new($memory,[IO.Compression.ZipArchiveMode]::Read,$true)
            try { Read-ModMetadata $nestedArchive $root $rootId } finally { $nestedArchive.Dispose() }
        } finally { $stream.Dispose();$memory.Dispose() }
    }
}
foreach($file in Get-ChildItem -LiteralPath (Join-Path $source 'mods') -Filter '*.jar' -File) {
    $archive=[IO.Compression.ZipFile]::OpenRead($file.FullName)
    try { Read-ModMetadata $archive $file.FullName } finally { $archive.Dispose() }
}
$needed=[Collections.Generic.Queue[string]]::new()
foreach($id in @('fabric-api','xaeroworldmap','xaerominimap','travelerstitles','biomesoplenty','regions_unexplored',
        'biomeswevegone','natures_spirit','terralith','tectonic','mysticsbiomes','wwoo','sodium','lithium','ferritecore')) { $needed.Enqueue($id) }
$selected=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$resolved=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($id in @('minecraft','java','fabricloader')) { [void]$resolved.Add($id) }
function Get-VersionRank([string]$text) {
    $match=[regex]::Match($text,'\d+(?:\.\d+){0,3}')
    if(!$match.Success) { return [version]'0.0' }
    $number=$match.Value;if(!$number.Contains('.')){$number+='.0'}
    return [version]$number
}
while($needed.Count) {
    $id=$needed.Dequeue()
    if(!$resolved.Add($id)) { continue }
    if(!$providers.ContainsKey($id)) { throw ('Missing dependency in source instance: '+$id) }
    $provider=$providers[$id] | Sort-Object @{Expression={Get-VersionRank $_.Metadata.version};Descending=$true},
        @{Expression={if($_.RootId -eq $id){0}else{1}}},Root | Select-Object -First 1
    if(!$selected.Add($provider.Root)) { continue }
    foreach($item in $roots[$provider.Root]) {
        foreach($dependency in $item.Metadata.depends.PSObject.Properties.Name) { if($dependency){$needed.Enqueue($dependency)} }
    }
}
[IO.Directory]::CreateDirectory((Join-Path $target 'mods')) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $target 'PCL')) | Out-Null
$id=Split-Path -Leaf $target
$version.id=$id
$version | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath (Join-Path $target ($id+'.json')) -Encoding utf8
Copy-Item -LiteralPath (Join-Path $source ((Split-Path -Leaf $source)+'.jar')) -Destination (Join-Path $target ($id+'.jar'))
$natives=Join-Path $source ((Split-Path -Leaf $source)+'-natives')
if(Test-Path -LiteralPath $natives) { Copy-Item -LiteralPath $natives -Destination (Join-Path $target ($id+'-natives')) -Recurse }
foreach($file in $selected) { Copy-Item -LiteralPath $file -Destination (Join-Path $target 'mods') }
Copy-Item -LiteralPath $artifact -Destination (Join-Path $target 'mods')
# The configuration is fresh: no accounts, server list, private maps or personal launcher settings are copied.
@'
VersionArgumentIndie:1
VersionArgumentIndieV2:True
VersionVanillaName:1.21.1
VersionFabric:0.19.3
State:9
Info:此地名曰 0.3.0 轻量测试实例
VersionArgumentTitle:此地名曰 0.3.0 测试
'@ | Set-Content -LiteralPath (Join-Path $target 'PCL/Setup.ini') -Encoding utf8
@'
lang:zh_cn
guiScale:3
renderDistance:6
simulationDistance:5
maxFps:120
fullscreen:false
pauseOnLostFocus:false
'@ | Set-Content -LiteralPath (Join-Path $target 'options.txt') -Encoding utf8
$manifest=@($selected | Sort-Object | ForEach-Object {
    [pscustomobject]@{File=(Split-Path -Leaf $_);SHA256=(Get-FileHash -LiteralPath $_).Hash}
})
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'test-mods.json') -Encoding utf8
Write-Output ('Prepared '+($selected.Count+1)+' mod jars in '+$target)
