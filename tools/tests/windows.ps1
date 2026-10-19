$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$toolRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('atomic cli ' + [Guid]::NewGuid().ToString('N'))
$projectDir = Join-Path $testRoot 'business project'
$portableRoot = Join-Path $testRoot 'portable tools'
New-Item -ItemType Directory -Path $projectDir -Force | Out-Null
Expand-Archive -LiteralPath (Join-Path $toolRoot 'dist/atomic-api-cli.zip') -DestinationPath $portableRoot
$localTool = Join-Path $projectDir '.atomic-api/tool'
New-Item -ItemType Directory -Path (Split-Path -Parent $localTool) -Force | Out-Null
# Both paths are explicit children of this test's unique temporary directory.
if ((Test-Path $localTool) -or -not (Test-Path (Join-Path $portableRoot 'atomic-api'))) { throw 'Unexpected portable paths' }
Move-Item -LiteralPath (Join-Path $portableRoot 'atomic-api') -Destination $localTool
$entry = Join-Path $localTool 'atomic-api.cmd'
Push-Location $projectDir
try {
    & $entry -h
    if ($LASTEXITCODE -ne 0) { throw 'Portable help failed' }
    & $entry init
    if ($LASTEXITCODE -ne 0) { throw 'Portable init failed' }
    foreach ($name in @('api-dev')) {
        if (-not (Test-Path ".atomic-api/skills/$name/SKILL.md")) { throw "Skill missing: $name" }
    }
    foreach ($ref in @('api-atomic.md', 'app-api.md', 'configuration.md', 'templates.md', 'rules.md', 'l2evpn-example.md')) { if (-not (Test-Path ".atomic-api/skills/api-dev/references/$ref")) { throw "Reference missing: $ref" } }
    foreach ($template in @('atomic/implementation.java.tpl', 'app/handler.java.tpl', 'atomic/tests.java.tpl', 'app/tests.java.tpl')) { if (-not (Test-Path ".atomic-api/templates/$template")) { throw "Template missing: $template" } }
    if (Test-Path (Join-Path $localTool '.atomic-api')) { throw 'Working directory changed' }
    if ((Test-Path '.agents') -or (Test-Path '.claude') -or (Test-Path '.cursor')) { throw 'Agent-specific directories created' }
    & $entry new --operation queryCircuit --business circuit
    if ($LASTEXITCODE -ne 0) { throw 'Repository-local draft failed' }
    [IO.File]::WriteAllText((Join-Path $projectDir '.atomic-api/profile.yaml'), 'user configuration')
    & $entry init
    if ($LASTEXITCODE -ne 0 -or (Get-Content '.atomic-api/profile.yaml' -Raw) -ne 'user configuration') { throw 'Configuration changed' }
    & $entry check --repo 'missing directory'
    if ($LASTEXITCODE -ne 2) { throw 'Argument/exit code changed' }
} finally {
    Pop-Location
}
$strictProject = Join-Path $testRoot 'strict project'
& $entry demo --repo $strictProject
if ($LASTEXITCODE -ne 0) { throw 'Demo failed' }
& $entry init --repo $strictProject
if ($LASTEXITCODE -ne 0) { throw 'Strict init failed' }
$contract = Join-Path $strictProject 'contracts/strict-message.yaml'
& $entry generate --repo $strictProject --contract $contract --apply
if ($LASTEXITCODE -ne 0) { throw 'Generation failed' }
& $entry verify --repo $strictProject --contract $contract
if ($LASTEXITCODE -ne 1) { throw 'Unimplemented business was not blocked' }
& $entry new --repo $strictProject --operation queryOther --business resource
if ($LASTEXITCODE -ne 0) { throw 'New operation failed' }
& $entry new --repo $strictProject --operation queryOther --business resource
if ($LASTEXITCODE -ne 1) { throw 'Duplicate contract overwritten' }
Write-Output 'Windows portable code-tool checks passed.'
exit 0
