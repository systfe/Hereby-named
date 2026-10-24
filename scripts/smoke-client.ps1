param(
    [Parameter(Mandatory=$true)][string]$InstancePath,
    [string]$MinecraftVersion='1.21.1',
    [string]$WorldPath,
    [string]$JavaHome=$env:JAVA_HOME,
    [string]$ModVersion='0.3.0',
    [switch]$PrepareOnly,
    [switch]$SkipPrepare,
    [switch]$InPlace
)
$ErrorActionPreference='Stop'
if(!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Set JAVA_HOME or pass -JavaHome with a Java runtime directory' }
$projectRoot=Split-Path -Parent $PSScriptRoot
$instance=(Resolve-Path -LiteralPath $InstancePath).Path
$minecraftRoot=Split-Path -Parent (Split-Path -Parent $instance)
$testRoot=Join-Path $projectRoot ('.tools/smoke-'+$MinecraftVersion)
if($InPlace) {
    if(!$SkipPrepare -or !(Test-Path -LiteralPath (Join-Path $instance 'test-mods.json'))) {
        throw 'In-place launch is restricted to prepared test instances and requires -SkipPrepare'
    }
    $testRoot=$instance
}
[IO.Directory]::CreateDirectory($testRoot) | Out-Null
function Copy-TestTree([string]$source,[string]$destination) {
    if(!(Test-Path -LiteralPath $source)) { return }
    & robocopy $source $destination /E /COPY:DAT /R:1 /W:1 /NFL /NDL /NJH /NJS /NP /XF session.lock | Out-Null
    if($LASTEXITCODE -ge 8) { throw ('Cannot prepare test copy: '+$source) }
}
if(!$SkipPrepare) {
    foreach($folder in @('mods','config','defaultconfigs','datapacks','resourcepacks','kmdtravel','moonlight-global-datapacks','xaero')) {
        Copy-TestTree (Join-Path $instance $folder) (Join-Path $testRoot $folder)
    }
    foreach($filename in @('options.txt','options.amecs.key_modifiers.txt')) {
        $sourceFile=Join-Path $instance $filename
        if(Test-Path -LiteralPath $sourceFile) { Copy-Item -LiteralPath $sourceFile -Destination $testRoot }
    }
    if($WorldPath) {
        $world=(Resolve-Path -LiteralPath $WorldPath).Path
        if($world.StartsWith($testRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Use an original world as source, not the test destination' }
        Get-FileHash -LiteralPath (Join-Path $world 'level.dat') -Algorithm SHA256 | Select-Object Path,Hash | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRoot 'original-world-hashes.json')
        Copy-TestTree $world (Join-Path $testRoot 'saves/Hereby Named Test')
    }
    $optionsFile=Join-Path $testRoot 'options.txt'
    if(Test-Path -LiteralPath $optionsFile) {
        $options=[IO.File]::ReadAllText($optionsFile) -replace '(?m)^guiScale:.*$','guiScale:3' -replace '(?m)^fullscreen:.*$','fullscreen:false'
        [IO.File]::WriteAllText($optionsFile,$options,[Text.UTF8Encoding]::new($false))
    }
}
if($PrepareOnly) { Write-Output ('Prepared isolated test directory: '+$testRoot); exit 0 }
$artifact=Join-Path $projectRoot ('build/'+$MinecraftVersion+'/libs/hereby-named-'+$ModVersion+'+'+$MinecraftVersion+'.jar')
if(!(Test-Path -LiteralPath $artifact)) { throw ('Build the artifact first: '+$artifact) }
Copy-Item -LiteralPath $artifact -Destination (Join-Path $testRoot 'mods')
$versionFile=Join-Path $instance ((Split-Path -Leaf $instance)+'.json')
$version=Get-Content -LiteralPath $versionFile -Raw | ConvertFrom-Json
$classpath=[Collections.Generic.List[string]]::new()
foreach($library in $version.libraries) {
    $relative=$library.downloads.artifact.path
    if(!$relative) {
        $coordinate=$library.name.Split(':')
        if($coordinate.Length -lt 3) { continue }
        $relative=$coordinate[0].Replace('.','/')+'/'+$coordinate[1]+'/'+$coordinate[2]+'/'+$coordinate[1]+'-'+$coordinate[2]
        if($coordinate.Length -gt 3) { $relative+='-'+$coordinate[3] }
        $relative+='.jar'
    }
    $path=Join-Path (Join-Path $minecraftRoot 'libraries') $relative
    if(Test-Path -LiteralPath $path) { $classpath.Add($path) }
}
$classpath.Add((Join-Path $instance ((Split-Path -Leaf $instance)+'.jar')))
$javaArgs=@('-Xmx5G','-Dfile.encoding=UTF-8',('-Djava.library.path='+(Join-Path $instance ((Split-Path -Leaf $instance)+'-natives'))),
    '-cp',($classpath -join ';'),$version.mainClass,'--username','HerebyNamedTest','--version',$MinecraftVersion,
    '--gameDir',$testRoot,'--assetsDir',(Join-Path $minecraftRoot 'assets'),'--assetIndex',[string]$version.assetIndex.id,
    '--uuid','00000000000000000000000000000001','--accessToken','0','--userType','legacy','--versionType','release',
    '--width','1100','--height','720')
$argsFile=Join-Path $testRoot 'java-args.txt'
$escaped=$javaArgs | ForEach-Object { '"'+$_.Replace('\','\\').Replace('"','\"')+'"' }
# The Windows Java launcher reads @argfiles using the native ANSI code page.
Add-Type -TypeDefinition 'using System.Runtime.InteropServices; public static class HerebyNativeCodePage { [DllImport("kernel32.dll")] public static extern uint GetACP(); }'
[IO.File]::WriteAllLines($argsFile,$escaped,[Text.Encoding]::GetEncoding([int][HerebyNativeCodePage]::GetACP()))
$process=Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') -ArgumentList ('@"'+$argsFile+'"') -WorkingDirectory $testRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $testRoot 'stdout.log') -RedirectStandardError (Join-Path $testRoot 'stderr.log') -PassThru
$process.Id | Set-Content -LiteralPath (Join-Path $testRoot 'process-id.txt')
Write-Output ('Isolated '+$MinecraftVersion+' client PID: '+$process.Id)
exit 0
