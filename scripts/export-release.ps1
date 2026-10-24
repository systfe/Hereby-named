param([Parameter(Mandatory=$true)][string]$Version)
$ErrorActionPreference='Stop'
if($Version -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$') { throw 'Invalid release version' }
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $versions=@('1.20.1','1.21.1')
    foreach($minecraft in $versions) {
        & (Join-Path $projectRoot 'gradlew.bat') ('-Pminecraft_version='+$minecraft) ('-Pmod_version='+$Version) build --console plain
        if($LASTEXITCODE -ne 0) { throw ('Build failed for Minecraft '+$minecraft) }
    }
    $packages=@(foreach($minecraft in $versions) {
        $name='hereby-named-'+$Version+'+'+$minecraft+'.jar'
        $source=Join-Path $projectRoot ('build/'+$minecraft+'/libs/'+$name)
        if(!(Test-Path -LiteralPath $source -PathType Leaf)) { throw ('Missing package: '+$name) }
        [pscustomobject]@{Name=$name;Source=$source}
    })
    $destination=Join-Path $projectRoot 'dist'
    [IO.Directory]::CreateDirectory($destination) | Out-Null
    foreach($package in $packages) {
        $target=Join-Path $destination $package.Name
        Copy-Item -LiteralPath $package.Source -Destination $target
        if((Get-FileHash -LiteralPath $package.Source).Hash -ne (Get-FileHash -LiteralPath $target).Hash) { throw ('Export differs: '+$package.Name) }
        Write-Output $target
    }
} finally { Pop-Location }
