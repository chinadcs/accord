# Guarda cada experimento do PC com runtime, fontes, configuração e resultados próprios.
param(
    [Parameter(Mandatory)][ValidatePattern('^run-[0-9]{3}-[a-z0-9-]+$')][string]$Version,
    [string]$Models = 'all',
    [string]$Only,
    [string]$RegradeFrom,
    [string]$PrivateFile,
    [switch]$SemanticRefs,
    [switch]$AnchoredFields,
    [switch]$Routed,
    [switch]$NoCache,
    [switch]$CategoryEvidence,
    [switch]$MerchantGate,
    [ValidateRange(1, 5)][int]$Suggestions = 1,
    [ValidateSet(2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)][int]$RoutedVersion = 2,
    [ValidateSet('dev', 'holdout', 'expanded', 'brands', 'private')][string]$Suite = 'dev',
    [int]$Ctx = 2048,
    [int]$Threads = 0,
    [string]$Note = ''
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($Suite -eq 'private' -and -not $PrivateFile) { throw 'Suite private exige -PrivateFile.' }
if ($PrivateFile) {
    $PrivateFile = (Resolve-Path -LiteralPath $PrivateFile).Path
    if ($PrivateFile.StartsWith($repo.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Frases reais devem ficar fora do repositorio.' }
    if ($RegradeFrom) { throw 'Frases reais nao usam replay de saidas.' }
}
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
$destination = Join-Path $repo "poc/results/pc/$Version"
if (Test-Path -LiteralPath $destination) { throw "A versão já existe: $destination. Use um novo número." }
Push-Location $repo
try {
    & .\gradlew.bat :pocpc:installDist
    if ($LASTEXITCODE -ne 0) { throw "Build falhou ($LASTEXITCODE)." }
    New-Item -ItemType Directory -Path $destination | Out-Null
    Copy-Item -LiteralPath 'pocpc/build/install/pocpc' -Destination (Join-Path $destination 'runtime') -Recurse
    $snapshotFiles = @(
        'poc/assets/models.json', 'poc/llama-cpp.pin',
        'core/src/main', 'pocpc/src/main',
        'core/build.gradle.kts', 'pocpc/build.gradle.kts', 'gradle/libs.versions.toml',
        'scripts/poc/pc-run-version.ps1'
    )
    foreach ($relative in $snapshotFiles) {
        $target = Join-Path $destination "source/$relative"
        New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
        Copy-Item -LiteralPath $relative -Destination $target -Recurse
    }
    $serverExe = Join-Path $repo 'poc/third_party/llama-server-pc/llama-server.exe'
    $arguments = @('--models', $Models, '--out', $destination, '--tag', $Version, '--ctx', "$Ctx", '--suite', $Suite, '--server', $serverExe)
    if ($Only) { $arguments += @('--only', $Only) }
    if ($PrivateFile) { $arguments += @('--private', $PrivateFile) }
    if ($SemanticRefs) { $arguments += '--semantic-refs' }
    if ($AnchoredFields) { $arguments += '--anchored-fields' }
    if ($Routed) { $arguments += @('--routed', '--routed-version', "$RoutedVersion") }
    if ($NoCache) { $arguments += '--no-cache' }
    if ($CategoryEvidence) { $arguments += '--category-evidence' }
    if ($MerchantGate) { $arguments += '--merchant-gate' }
    if ($Suggestions -gt 1) { $arguments += @('--suggestions', "$Suggestions") }
    if ($RegradeFrom) { $arguments += @('--regrade', (Resolve-Path -LiteralPath $RegradeFrom).Path) }
    if ($Threads -gt 0) { $arguments += @('--threads', "$Threads") }
    $manifest = [ordered]@{
        version = $Version
        startedAt = (Get-Date).ToUniversalTime().ToString('o')
        note = $Note
        arguments = $arguments
        completeBattery = -not [bool]$Only
        models = $Models
        regradeFrom = $RegradeFrom
        privateFileSha256 = if ($PrivateFile) { (Get-FileHash -LiteralPath $PrivateFile -Algorithm SHA256).Hash.ToLower() } else { $null }
        serverSha256 = (Get-FileHash -LiteralPath $serverExe -Algorithm SHA256).Hash.ToLower()
        files = @(Get-ChildItem -LiteralPath (Join-Path $destination 'source'), (Join-Path $destination 'runtime/lib') -Recurse -File | ForEach-Object {
            [ordered]@{ path = $_.FullName.Substring($destination.Length + 1); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower() }
        })
    }
    $manifestPath = Join-Path $destination 'manifest.json'
    $manifest | ConvertTo-Json -Depth 7 | Set-Content -Encoding UTF8 -LiteralPath $manifestPath
    $runnerJava = Join-Path $env:JAVA_HOME 'bin/java.exe'
    # O runner lê catálogo e pino do snapshot, nunca da árvore que continuará sendo editada.
    Push-Location -LiteralPath (Join-Path $destination 'source')
    try {
        & $runnerJava '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-cp' (Join-Path $destination 'runtime/lib/*') 'app.accord.pocpc.MainKt' @arguments |
            Tee-Object -FilePath (Join-Path $destination 'console.log')
        $runnerExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $manifest['finishedAt'] = (Get-Date).ToUniversalTime().ToString('o')
    $manifest['exitCode'] = $runnerExit
    $manifest | ConvertTo-Json -Depth 7 | Set-Content -Encoding UTF8 -LiteralPath $manifestPath
    if ($runnerExit -ne 0) { throw "Runner terminou com código $runnerExit. Resultados preservados em $destination." }
} finally {
    Pop-Location
}
