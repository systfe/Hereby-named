param([string]$Version='0.3.0')
$ErrorActionPreference='Stop'
if($Version -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$') { throw 'Invalid release version' }
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $changes=@(& git status --porcelain --untracked-files=normal)
    if($LASTEXITCODE -ne 0 -or $changes.Count) { throw 'Commit reviewed changes before exporting a source snapshot' }
    $destination=Join-Path $projectRoot 'dist'
    [IO.Directory]::CreateDirectory($destination) | Out-Null
    $archive=Join-Path $destination ('hereby-named-'+$Version+'-source.zip')
    & git archive --format=zip --prefix=hereby-named/ -o $archive HEAD
    if($LASTEXITCODE -ne 0) { throw 'Cannot export source snapshot' }
    Write-Output $archive
} finally { Pop-Location }
