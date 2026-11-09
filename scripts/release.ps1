# Prepara um APK local verificável. Não cria tags, não instala e não publica.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^v(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$')]
    [string]$Tag,
    # APK anteriormente distribuído: exige mesma assinatura e código maior.
    [string]$PreviousApk,
    # Distribuição: exige fonte commitada e evita mudanças durante o build.
    [switch]$RequireClean
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Instale Android SDK Build-Tools antes de preparar a release.' }
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt = Join-Path $buildTools.FullName 'aapt.exe'
$version = $Tag.Substring(1)
$parts = $version.Split('.') | ForEach-Object { [int]$_ }
$expectedCode = $parts[0] * 10000 + $parts[1] * 100 + $parts[2]
if ($expectedCode -le 1) { throw 'A versão deve superar o código 1 do piloto.' }
if ($PreviousApk) { $PreviousApk = (Resolve-Path -LiteralPath $PreviousApk).Path }

function Read-Apk([string]$Path) {
    $signature = & $apksigner verify --verbose --print-certs $Path 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Assinatura do APK inválida.' }
    $cert = [regex]::Match(($signature -join "`n"), 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)').Groups[1].Value
    if (-not $cert) { throw 'Não foi possível identificar o certificado do APK.' }
    $badging = & $aapt dump badging $Path 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível ler os metadados do APK.' }
    $package = [regex]::Match(($badging -join "`n"), "package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
    if (-not $package.Success) { throw 'Metadados de versão ausentes.' }
    return [pscustomobject]@{
        packageName = $package.Groups[1].Value
        versionCode = [int]$package.Groups[2].Value
        versionName = $package.Groups[3].Value
        certificateSha256 = $cert.ToLowerInvariant()
        debuggable = (($badging -join "`n") -match '(?m)^application-debuggable')
        cameraPermission = (($badging -join "`n") -match "uses-permission: name='android.permission.CAMERA'")
    }
}

Push-Location $root
try {
    $startingCommit = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível identificar a fonte.' }
    $startingStatus = & git status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conferir a árvore de trabalho.' }
    if ($RequireClean -and $startingStatus) { throw 'Distribuição exige checkout limpo do commit revisado.' }
    $previous = if ($PreviousApk) { Read-Apk $PreviousApk }
    if ($previous -and $expectedCode -le $previous.versionCode) {
        throw 'A atualização exige versionCode maior que o anterior.'
    }
    & .\gradlew.bat :app:assembleRelease "-Paccord.version=$version"
    if ($LASTEXITCODE -ne 0) { throw 'assembleRelease falhou.' }
    $apk = Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
    $metadata = Read-Apk $apk
    if ($metadata.packageName -ne 'app.accord' -or $metadata.versionCode -ne $expectedCode -or $metadata.versionName -ne $version) {
        throw 'O APK não corresponde ao pacote/versão solicitados.'
    }
    if ($metadata.debuggable -or $metadata.cameraPermission) { throw 'Release não pode ser depurável nem pedir CAMERA (ADR 0065).' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($entry in @('assets/licenses/accord-GPL-3.0.txt', 'assets/licenses/THIRD-PARTY-NOTICES.txt', 'assets/licenses/runtime-dependencies.json')) {
            if (-not $archive.GetEntry($entry)) { throw "Aviso obrigatório ausente no APK: $entry" }
        }
    }
    finally { $archive.Dispose() }
    if ($previous) {
        if ($previous.packageName -ne $metadata.packageName -or $previous.certificateSha256 -ne $metadata.certificateSha256) {
            throw 'Atualização incompatível: pacote ou certificado diferente do APK anterior.'
        }
        if ($metadata.versionCode -le $previous.versionCode) { throw 'A atualização exige versionCode maior que o anterior.' }
    }
    $output = Join-Path $root "app/build/releases/$Tag"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $destination = Join-Path $output 'accord.apk'
    Copy-Item -LiteralPath $apk -Destination $destination -Force
    $commit = & git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível identificar o commit.' }
    $status = & git status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conferir a árvore de trabalho.' }
    if ($RequireClean -and ($status -or "$commit" -ne "$startingCommit")) { throw 'A fonte mudou durante o build; APK não liberado para distribuição.' }
    [ordered]@{
        tag = $Tag
        commit = "$commit"
        dirty = [bool]$status
        packageName = $metadata.packageName
        versionName = $metadata.versionName
        versionCode = $metadata.versionCode
        certificateSha256 = $metadata.certificateSha256
        sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = (Get-Item -LiteralPath $destination).Length
        previousApkChecked = [bool]$previous
        debuggable = $metadata.debuggable
        cameraPermission = $metadata.cameraPermission
        licenseNoticesChecked = $true
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'release.json') -Encoding UTF8
    Write-Host "APK verificado: $destination (versionCode $expectedCode)"
    Write-Host 'Preparação local; publicação e validação por Obtainium continuam pendentes.'
}
finally { Pop-Location }
