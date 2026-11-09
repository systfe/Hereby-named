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
    if(!$java21) { throw 'Java 21 runtime is required for 1.21.1 and ModDevGradle builds' }

    $gradlew = Join-Path $projectRoot 'gradlew.bat'

    Write-Host "===> Running common unit tests..."
    & $gradlew :common:test --console plain
    if($LASTEXITCODE -ne 0) { throw 'Common tests failed' }

    Write-Host "===> Building Fabric 1.20.1..."
    & $gradlew ('-Pminecraft_version=1.20.1') ('-Pmod_version='+$Version) :fabric:remapJar --console plain
    if($LASTEXITCODE -ne 0) { throw 'Build failed for Fabric 1.20.1' }

    Write-Host "===> Building Fabric 1.21.1..."
    & $gradlew ('-Dorg.gradle.java.home='+$java21) ('-Pminecraft_version=1.21.1') ('-Pmod_version='+$Version) :fabric:remapJar --console plain
    if($LASTEXITCODE -ne 0) { throw 'Build failed for Fabric 1.21.1' }

    Write-Host "===> Building Forge 1.20.1..."
    & $gradlew ('-Dorg.gradle.java.home='+$java21) ('-Pmod_version='+$Version) :forge:jar --console plain
    if($LASTEXITCODE -ne 0) { throw 'Build failed for Forge 1.20.1' }

    Write-Host "===> Building NeoForge 1.21.1..."
    & $gradlew ('-Dorg.gradle.java.home='+$java21) ('-Pmod_version='+$Version) :neoforge:jar --console plain
    if($LASTEXITCODE -ne 0) { throw 'Build failed for NeoForge 1.21.1' }

    $packages = @(
        [pscustomobject]@{
            Name = "hereby-named-$Version+1.20.1-fabric.jar"
            Source = Join-Path $projectRoot "fabric/build/1.20.1/libs/hereby-named-$Version+1.20.1-fabric.jar"
        },
        [pscustomobject]@{
            Name = "hereby-named-$Version+1.20.1-forge.jar"
            Source = Join-Path $projectRoot "forge/build/libs/hereby-named-$Version+1.20.1-forge.jar"
        },
        [pscustomobject]@{
            Name = "hereby-named-$Version+1.21.1-fabric.jar"
            Source = Join-Path $projectRoot "fabric/build/1.21.1/libs/hereby-named-$Version+1.21.1-fabric.jar"
        },
        [pscustomobject]@{
            Name = "hereby-named-$Version+1.21.1-neoforge.jar"
            Source = Join-Path $projectRoot "neoforge/build/libs/hereby-named-$Version+1.21.1-neoforge.jar"
        }
    )

    $destination = Join-Path $projectRoot 'dist'
    [IO.Directory]::CreateDirectory($destination) | Out-Null

    Write-Host "===> Exporting packages to dist/..."
    foreach($package in $packages) {
        if(!(Test-Path -LiteralPath $package.Source -PathType Leaf)) { throw ('Missing package: ' + $package.Source) }
        $target = Join-Path $destination $package.Name
        Copy-Item -LiteralPath $package.Source -Destination $target -Force
        $srcHash = (Get-FileHash -LiteralPath $package.Source -Algorithm SHA256).Hash
        $dstHash = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
        if($srcHash -ne $dstHash) { throw ('Export differs: ' + $package.Name) }
        Write-Output $target
    }
} finally { Pop-Location }
