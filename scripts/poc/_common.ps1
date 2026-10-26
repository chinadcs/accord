# Funções comuns dos scripts da bancada da Etapa 0 (S3 §2). Uso: . "$PSScriptRoot\_common.ps1"
# A bancada é o aplicativo debug app.accord.poc (poc/), separado do Accord. Os scripts só conversam com
# ele por adb: nada aqui toca o aplicativo do casal nem envia dados para fora do computador.
$ErrorActionPreference = 'Stop'
$script:Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$script:Pkg = 'app.accord.poc'

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
$script:AdbExe = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'

function Initialize-Poc {
    param([string]$Serial)
    if (-not (Test-Path $script:AdbExe)) { throw "adb não encontrado em $script:AdbExe" }
    if (-not $Serial) { $Serial = $env:ANDROID_SERIAL }
    $devices = @(& $script:AdbExe devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if (-not $Serial) {
        if ($devices.Count -eq 0) { throw 'Nenhum aparelho conectado (adb devices). Ative a depuração USB e autorize o computador.' }
        if ($devices.Count -gt 1) { throw "Mais de um aparelho: informe -Serial. Conectados: $($devices -join ', ')" }
        $Serial = $devices[0]
    } elseif ($devices -notcontains $Serial) {
        throw "Aparelho '$Serial' não está conectado/autorizado. Conectados: $($devices -join ', ')"
    }
    $script:Serial = $Serial
    return $Serial
}

# adb no aparelho escolhido. Devolve a saída como texto.
function Invoke-Adb {
    # adb escreve progresso em stderr; com 'Stop' o PowerShell 5.1 trataria isso como erro.
    $ErrorActionPreference = 'Continue'
    & $script:AdbExe -s $script:Serial @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') falhou ($LASTEXITCODE)" }
}

# Comando de shell dentro do usuário do aplicativo (app debug; run-as lê/escreve filesDir).
function Invoke-RunAs {
    param([Parameter(Mandatory)][string]$Command)
    $ErrorActionPreference = 'Continue'
    & $script:AdbExe -s $script:Serial shell "run-as $script:Pkg sh -c '$Command'" 2>&1 | ForEach-Object { "$_" }
}

function Get-PocCatalog {
    $path = Join-Path $script:Root 'poc\assets\models.json'
    (Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json).models
}

function Find-PocModel {
    param([Parameter(Mandatory)][string]$Id)
    $model = Get-PocCatalog | Where-Object { $_.id -eq $Id }
    if (-not $model) { throw "Modelo '$Id' não está em poc/assets/models.json. Disponíveis: $((Get-PocCatalog).id -join ', ')" }
    $model
}

function Get-PocStatus {
    $text = & $script:AdbExe -s $script:Serial exec-out "run-as $script:Pkg cat files/status.json" 2>$null
    if (-not $text) { return $null }
    try { ($text -join '') | ConvertFrom-Json } catch { $null }
}

# Espera o trabalho em andamento terminar. Devolve o último status.
function Wait-PocJob {
    param([int]$TimeoutMinutes = 60)
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    $last = ''
    while ((Get-Date) -lt $deadline) {
        $status = Get-PocStatus
        if ($status) {
            $line = "$($status.phase) · $($status.stage) · $($status.message)"
            if ($line -ne $last) { Write-Host $line; $last = $line }
            if ($status.phase -in 'Done', 'Failed', 'Cancelled') { return $status }
        }
        Start-Sleep -Seconds 3
    }
    throw "Tempo esgotado ($TimeoutMinutes min) esperando a bancada."
}

# Pasta de resultados no computador (ignorada pelo Git; o relatório é que vai para docs/poc).
function Get-PocResultsDir {
    param([Parameter(Mandatory)][string]$Label)
    $dir = Join-Path $script:Root "poc\results\$Label"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $dir
}
