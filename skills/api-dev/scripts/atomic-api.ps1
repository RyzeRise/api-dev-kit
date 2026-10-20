$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$skillRoot = Split-Path -Parent $PSScriptRoot
$jarPath = $env:ATOMIC_API_JAR
if (-not $jarPath) {
    $jarPath = Join-Path $skillRoot 'lib/atomic-api.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        $jarPath = Join-Path $skillRoot '../../tools/cli/target/atomic-api-0.8.0.jar'
    }
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    [Console]::Error.WriteLine('atomic-api JAR is missing; extract the complete portable package or build the source first.')
    exit 2
}
$runtime = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else {
    (Get-Command java -ErrorAction SilentlyContinue).Source
}
if (-not $runtime) {
    [Console]::Error.WriteLine('atomic-api requires Java 17 or newer.')
    exit 2
}
& $runtime '-Dfile.encoding=UTF-8' -jar $jarPath @args
exit $LASTEXITCODE
