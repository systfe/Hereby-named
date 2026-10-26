param([Parameter(Mandatory=$true)][string]$Version)
$ErrorActionPreference='Stop'
if($Version -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$') { throw 'Invalid release version' }
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $java21Candidates = @(
        $env:JAVA21_HOME,
        (Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta'),
        'C:\Program Files\Java\jdk-21',
        'C:\Program Files\Eclipse Adoptium\jdk-21'
    )
    $java21 = $java21Candidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) } | Select-Object -First 1

    $versions=@('1.20.1','1.21.1')
    foreach($minecraft in $versions) {
        $extraArgs = @()
        if($minecraft -eq '1.21.1' -and $java21) {
            $extraArgs += ('-Dorg.gradle.java.home=' + $java21)
        }
        & (Join-Path $projectRoot 'gradlew.bat') @extraArgs ('-Pminecraft_version='+$minecraft) ('-Pmod_version='+$Version) build --console plain
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
