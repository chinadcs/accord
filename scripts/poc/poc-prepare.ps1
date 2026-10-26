# Prepara um aparelho para a Etapa 0: compila e instala a bancada (app.accord.poc), registra a ficha do
# aparelho (variante, Android, espaço, RAM total e disponível) e lista o que falta fazer à mão.
# Uso: .\scripts\poc\poc-prepare.ps1 -Label s20fe [-Serial XXXX] [-SkipBuild]
# Não toca o Accord instalado no aparelho. Exige JDK 17+ (usa o JBR do Android Studio).
param(
    [Parameter(Mandatory)][string]$Label,
    [string]$Serial,
    [switch]$SkipBuild
)
. "$PSScriptRoot\_common.ps1"
$null = Initialize-Poc -Serial $Serial
$env:ANDROID_SERIAL = $script:Serial

Write-Host "== Aparelho $script:Serial ($Label)"
foreach ($prop in 'ro.product.manufacturer', 'ro.product.model', 'ro.product.device', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.board.platform', 'ro.product.cpu.abilist') {
    Write-Host ('{0,-28} {1}' -f $prop, ((Invoke-Adb shell getprop $prop) -join ''))
}
$abis = (Invoke-Adb shell getprop ro.product.cpu.abilist) -join ''
if ($abis -notmatch 'arm64-v8a|x86_64') { throw "ABI sem suporte na bancada: $abis" }

if (-not $SkipBuild) {
    Push-Location $script:Root
    try {
        & "$PSScriptRoot\fetch-llama.ps1"
        .\gradlew.bat :poc:installDebug
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar/instalar a bancada.' }
    } finally { Pop-Location }
}
# Tela acesa enquanto o cabo USB estiver conectado (as medições com cabo não podem rodar com a tela apagada).
# Desfaça no fim com: adb shell svc power stayon false
Invoke-Adb shell svc power stayon usb | Out-Null
if (-not ((Invoke-Adb shell pm list packages $script:Pkg) -join '')) { throw "A bancada não está instalada ($script:Pkg). Rode sem -SkipBuild." }

# Ficha: a bancada grava o estágio 'info' (RAM, espaço, núcleos, variantes de CPU do motor, modelos presentes).
Invoke-RunAs 'rm -f files/status.json' | Out-Null
Invoke-Adb shell am start -n "$script:Pkg/.PocActivity" --es stage info --es tag "$Label-ficha" | Out-Null
$status = Wait-PocJob -TimeoutMinutes 5
if ($status.phase -ne 'Done') { throw "A ficha falhou: $($status.message)" }
& "$PSScriptRoot\poc-collect.ps1" -Label $Label -Serial $script:Serial -Quiet

$info = Get-ChildItem (Get-PocResultsDir $Label) -Filter '*-info-*.jsonl' | Sort-Object LastWriteTime | Select-Object -Last 1
$record = Get-Content -Encoding UTF8 $info.FullName | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.type -eq 'info' } | Select-Object -First 1
$d = $record.device
Write-Host ''
Write-Host "== Ficha registrada ($($info.Name))"
Write-Host "Modelo/variante  : $($d.manufacturer) $($d.model) ($($d.device)) · $($d.hardware)"
Write-Host "SoC              : $($d.socManufacturer) $($d.socModel) · $($d.cores) núcleos · máx kHz: $($d.cpuMaxKhz -join ', ')"
Write-Host "Android          : $($d.androidRelease) (SDK $($d.sdk)) · patch $($d.securityPatch)"
Write-Host "RAM total / livre: $($d.totalMemMb) MB / $($d.availMemMb) MB agora"
Write-Host "Espaço livre     : $($d.internalFreeMb) MB de $($d.internalTotalMb) MB"
Write-Host "Variantes do motor: $(($record.nativeLibs | Where-Object { $_ -like 'libggml-cpu*' }) -join ', ')"
Write-Host ''
Write-Host 'Próximo passo: .\scripts\poc\poc-push-models.ps1 -Models <ids>  (ids em poc\assets\models.json)'
Write-Host 'Antes das medições, à mão: sem outros apps pesados abertos; anote o que estiver aberto.'
