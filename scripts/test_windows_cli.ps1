$ErrorActionPreference = 'Stop'
$toolRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('atomic cli ' + [Guid]::NewGuid().ToString('N'))
$installDir = Join-Path $testRoot 'installed command'
$projectDir = Join-Path $testRoot 'business project'
New-Item -ItemType Directory -Path $projectDir -Force | Out-Null
& (Join-Path $toolRoot 'install.ps1') -Prefix $installDir -Jar (Join-Path $toolRoot 'cli/target/atomic-api-0.1.0.jar')
Push-Location $projectDir
try {
    & atomic-api -h
    if ($LASTEXITCODE -ne 0) { throw 'atomic-api -h failed' }
    & atomic-api init --agent claude
    if ($LASTEXITCODE -ne 0) { throw 'atomic-api init failed' }
    if (-not (Test-Path '.atomic-api/profile.yaml')) { throw 'Project profile missing' }
    if (-not (Test-Path '.claude/skills/atomic-api-design/references/rules.md')) { throw 'Project Skill missing' }
    [IO.File]::WriteAllText((Join-Path $projectDir '.atomic-api/profile.yaml'), 'user configuration')
    & atomic-api init
    if ($LASTEXITCODE -ne 0) { throw 'Repeated init failed' }
    if ((Get-Content '.atomic-api/profile.yaml' -Raw) -ne 'user configuration') { throw 'User configuration overwritten' }
    & atomic-api check --repo 'missing directory'
    if ($LASTEXITCODE -ne 2) { throw 'Exit code or spaced argument was lost' }
} finally {
    Pop-Location
}
Write-Output 'Windows command smoke checks passed.'
