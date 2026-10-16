$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$toolRoot = Split-Path -Parent $PSScriptRoot
$jarPath = $env:ATOMIC_API_JAR
if (-not $jarPath) {
    $jarPath = Join-Path $toolRoot 'lib/atomic-api/atomic-api.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        $jarPath = Join-Path $toolRoot 'cli/target/atomic-api-0.1.0.jar'
    }
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    [Console]::Error.WriteLine('atomic-api installation is incomplete; run the installer again.')
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
