param(
    [string]$Prefix = (Join-Path $env:LOCALAPPDATA 'atomic-api'),
    [string]$Jar
)
$ErrorActionPreference = 'Stop'
if (-not $Jar -and (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'lib/atomic-api/atomic-api.jar'))) {
    $Jar = Join-Path $PSScriptRoot 'lib/atomic-api/atomic-api.jar'
} elseif (-not $Jar) {
    & mvn -q -f (Join-Path $PSScriptRoot 'cli/pom.xml') package
    if ($LASTEXITCODE -ne 0) { throw 'CLI build failed' }
    $Jar = Join-Path $PSScriptRoot 'cli/target/atomic-api-0.3.1.jar'
}
if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) { throw 'Built CLI artifact is missing' }
$Prefix = [IO.Path]::GetFullPath($Prefix)
$binDir = Join-Path $Prefix 'bin'
$libDir = Join-Path $Prefix 'lib/atomic-api'
function Assert-PlainPath([string]$Candidate) {
    $checkPath = [IO.Path]::GetFullPath($Candidate)
    while ($checkPath) {
        $item = Get-Item -LiteralPath $checkPath -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Refusing link or junction destination: $checkPath"
        }
        $checkPath = Split-Path -Parent $checkPath
    }
}
Assert-PlainPath (Join-Path $libDir 'atomic-api.jar')
Assert-PlainPath (Join-Path $binDir 'atomic-api.cmd')
Assert-PlainPath (Join-Path $binDir 'atomic-api-launch.ps1')
New-Item -ItemType Directory -Force -Path $binDir, $libDir | Out-Null
Copy-Item -LiteralPath $Jar -Destination (Join-Path $libDir 'atomic-api.jar') -Force
foreach ($name in @('atomic-api.cmd', 'atomic-api-launch.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "bin/$name") -Destination $binDir -Force
}
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (@($userPath -split ';') -notcontains $binDir) {
    [Environment]::SetEnvironmentVariable('Path', ($binDir + ';' + $userPath), 'User')
}
if (@($env:Path -split ';') -notcontains $binDir) { $env:Path = $binDir + ';' + $env:Path }
Write-Output 'Installed atomic-api. Run: atomic-api -h (or reopen the terminal).'
