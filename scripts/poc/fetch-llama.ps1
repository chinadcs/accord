# Baixa o código-fonte do llama.cpp na versão fixada em poc/llama-cpp.pin para poc/third_party/llama.cpp
# (ignorado pelo Git). A bancada compila esse código com o NDK; nada disto entra no aplicativo do casal.
# Uso: .\scripts\poc\fetch-llama.ps1 [-Force]
param([switch]$Force)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pin = @{}
Get-Content (Join-Path $root 'poc\llama-cpp.pin') | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $pin[$k] = $v.Trim()
}
$dest = Join-Path $root 'poc\third_party\llama.cpp'

if (Test-Path $dest) {
    if (-not $Force) {
        $head = (git -C $dest rev-parse HEAD).Trim()
        if ($head -eq $pin.commit) { Write-Host "llama.cpp $($pin.tag) já está em $dest"; return }
        throw "Há outra versão em $dest ($head). Use -Force para refazer."
    }
    Remove-Item -Recurse -Force $dest
}

New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
# longpaths: o repositório tem caminhos acima de 260 caracteres.
git -c core.longpaths=true clone --depth 1 --branch $pin.tag $pin.repo $dest
if ($LASTEXITCODE -ne 0) { throw 'git clone falhou' }
$head = (git -C $dest rev-parse HEAD).Trim()
if ($head -ne $pin.commit) { throw "Commit inesperado: $head (esperado $($pin.commit))" }
$status = git -C $dest status --porcelain
if ($status) { throw 'Checkout incompleto (caminhos longos?).' }
Write-Host "llama.cpp $($pin.tag) ($head) em $dest"
