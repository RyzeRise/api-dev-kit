$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$toolRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("api skill ' " + [Guid]::NewGuid().ToString('N'))
$portableRoot = Join-Path $testRoot 'unpacked'
Expand-Archive -LiteralPath (Join-Path $toolRoot 'dist/api-dev.zip') -DestinationPath $portableRoot
$bundle = Join-Path $portableRoot 'api-dev'
foreach ($layout in @('inside', 'outside')) {
    $projectDir = Join-Path $testRoot "business $layout"
    New-Item -ItemType Directory -Path $projectDir -Force | Out-Null
    $skillDir = if ($layout -eq 'inside') {
        Join-Path $projectDir '.skills/api-dev'
    } else {
        Join-Path $testRoot 'arbitrary shared location/api-dev'
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $skillDir) -Force | Out-Null
    Copy-Item -LiteralPath $bundle -Destination $skillDir -Recurse
    $entry = Join-Path $skillDir 'scripts/atomic-api.cmd'
    Push-Location $projectDir
    try {
        & $entry -h
        if ($LASTEXITCODE -ne 0) { throw 'Skill help failed' }
        & $entry init
        if ($LASTEXITCODE -ne 0) { throw 'Business init failed' }
        foreach ($asset in @('SKILL.md', 'lib/atomic-api.jar', 'references/app-api.md', 'templates/app/handler.java.tpl', 'templates/atomic/tests.java.tpl')) {
            if (-not (Test-Path -LiteralPath (Join-Path $skillDir $asset))) { throw "Skill asset missing: $asset" }
        }
        foreach ($unexpected in @('.atomic-api/skills', '.atomic-api/templates', '.atomic-api/tool', '.agents', '.claude', '.cursor')) {
            if (Test-Path -LiteralPath $unexpected) { throw "Unexpected directory: $unexpected" }
        }
        if (Test-Path -LiteralPath (Join-Path $skillDir '.atomic-api')) { throw 'Working directory changed' }
        & $entry new --layer app --operation queryL2evpnEvplNodes --business l2evpn
        if ($LASTEXITCODE -ne 0) { throw 'App draft failed' }
        & $entry new --layer atomic --operation getEvplService --business evpl --api-version R26C10
        if ($LASTEXITCODE -ne 0) { throw 'Atomic draft failed' }
        $demo = Join-Path $testRoot "demo $layout"
        & $entry demo --repo $demo
        if ($LASTEXITCODE -ne 0) { throw 'Demo failed' }
        $contract = Join-Path $demo 'contracts/strict-message.yaml'
        & $entry generate --repo $demo --contract $contract --apply
        if ($LASTEXITCODE -ne 0) { throw 'Generation failed' }
        $repeated = & $entry generate --repo $demo --contract $contract --apply
        if ($LASTEXITCODE -ne 0 -or -not ($repeated -match 'NOOP')) { throw 'Repeated generation is not NOOP' }
        & $entry verify --repo $demo --contract $contract
        if ($LASTEXITCODE -ne 1) { throw 'Unimplemented business was not blocked' }
        $legacy = @{
            '.atomic-api/profile.yaml' = 'user configuration'
            '.atomic-api/contracts/user.yaml' = 'user contract'
            '.atomic-api/skills/api-dev/SKILL.md' = 'old custom skill'
            '.atomic-api/templates/app/handler.java.tpl' = 'old custom template'
            '.atomic-api/tool/user-file' = 'old runtime'
        }
        foreach ($name in $legacy.Keys) {
            $file = Join-Path $projectDir $name
            New-Item -ItemType Directory -Path (Split-Path -Parent $file) -Force | Out-Null
            [IO.File]::WriteAllText($file, $legacy[$name])
        }
        $custom = Join-Path $skillDir 'references/l2evpn-example.md'
        [IO.File]::WriteAllText($custom, 'installed customization')
        & $entry init
        if ($LASTEXITCODE -ne 0) { throw 'Repeated init failed' }
        foreach ($name in $legacy.Keys) {
            if ([IO.File]::ReadAllText((Join-Path $projectDir $name)) -ne $legacy[$name]) { throw "Customization changed: $name" }
        }
        if ([IO.File]::ReadAllText($custom) -ne 'installed customization') { throw 'Installed Skill changed' }
        $moved = Join-Path $testRoot "moved $layout/api-dev"
        New-Item -ItemType Directory -Path (Split-Path -Parent $moved) -Force | Out-Null
        Move-Item -LiteralPath $skillDir -Destination $moved
        $entry = Join-Path $moved 'scripts/atomic-api.cmd'
        & $entry -h
        if ($LASTEXITCODE -ne 0) { throw 'Moved Skill failed' }
        & $entry init
        if ($LASTEXITCODE -ne 0) { throw 'Moved Skill init failed' }
        & $entry check --repo 'missing directory'
        if ($LASTEXITCODE -ne 2) { throw 'Argument/exit code changed' }
    } finally {
        Pop-Location
    }
}
Write-Output 'Self-contained Skill portable checks passed.'
exit 0
