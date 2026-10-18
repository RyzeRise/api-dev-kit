$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$toolRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('atomic cli ' + [Guid]::NewGuid().ToString('N'))
$installDir = Join-Path $testRoot 'installed command'
$projectDir = Join-Path $testRoot 'business project'
New-Item -ItemType Directory -Path $projectDir -Force | Out-Null
& (Join-Path $toolRoot 'install.ps1') -Prefix $installDir -Jar (Join-Path $toolRoot 'cli/target/atomic-api-0.3.0.jar')
Push-Location $projectDir
try {
    $helpText = & atomic-api -h
    if ($LASTEXITCODE -ne 0) { throw 'atomic-api -h failed' }
    $currentDirectoryText = -join @([char]0x5f53, [char]0x524d, [char]0x76ee, [char]0x5f55)
    if (-not (($helpText -join "`n").Contains($currentDirectoryText))) { throw 'UTF-8 command output was lost' }
    & atomic-api init --agent claude
    if ($LASTEXITCODE -ne 0) { throw 'atomic-api init failed' }
    if (-not (Test-Path '.atomic-api/profile.yaml')) { throw 'Project profile missing' }
    if (-not (Test-Path '.claude/skills/atomic-api-design/references/rules.md')) { throw 'Project Skill missing' }
    if (-not (Test-Path '.claude/skills/ipran-fault-api-dev/references/verification.md')) { throw 'Fault API Skill missing' }
    if (-not (Test-Path '.atomic-api/contracts/example.yaml')) { throw 'Response converter contract missing' }
    [IO.File]::WriteAllText((Join-Path $projectDir '.atomic-api/profile.yaml'), 'user configuration')
    & atomic-api init
    if ($LASTEXITCODE -ne 0) { throw 'Repeated init failed' }
    if ((Get-Content '.atomic-api/profile.yaml' -Raw) -ne 'user configuration') { throw 'User configuration overwritten' }
    & atomic-api check --repo 'missing directory'
    if ($LASTEXITCODE -ne 2) { throw 'Exit code or spaced argument was lost' }
} finally {
    Pop-Location
}
$strictProject = Join-Path $testRoot 'strict project'
& atomic-api demo --repo $strictProject
if ($LASTEXITCODE -ne 0) { throw 'Strict demo failed' }
& atomic-api init --repo $strictProject --agent codex
if ($LASTEXITCODE -ne 0) { throw 'Strict init failed' }
$strictContract = Join-Path $strictProject 'contracts/strict-message.yaml'
& atomic-api generate --repo $strictProject --contract $strictContract --apply
if ($LASTEXITCODE -ne 0) { throw 'Strict String generation failed' }
& atomic-api verify --repo $strictProject --contract $strictContract
if ($LASTEXITCODE -ne 1) { throw 'Unimplemented business was not blocked' }
& atomic-api new --repo $strictProject --operation queryOther --business resource
if ($LASTEXITCODE -ne 0) { throw 'New operation draft failed' }
& atomic-api new --repo $strictProject --operation queryOther --business resource
if ($LASTEXITCODE -ne 1) { throw 'Duplicate contract was not protected' }
Write-Output 'Windows command smoke checks passed.'
exit 0
