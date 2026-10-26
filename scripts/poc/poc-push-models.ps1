# Copia modelos GGUF de E:\Projects\ai-models para o armazenamento interno da bancada no aparelho
# (filesDir/model, o mesmo tipo de local que o produto usará) e confere o SHA-256 no próprio aparelho.
# Uso: .\scripts\poc\poc-push-models.ps1 -Models pulse-qwen3-0.6b-q4km,qwen3.5-2b-q4km [-Serial XXXX] [-Source E:\Projects\ai-models]
# Precisa de espaço livre para o modelo e para uma cópia temporária (em /data/local/tmp) durante a cópia.
# Com -Remove apaga os modelos listados do aparelho (ids ou 'all'); com -List mostra o que há lá.
param(
    [string[]]$Models,
    [string]$Serial,
    [string]$Source = 'E:\Projects\ai-models',
    [switch]$Force,
    [switch]$Remove,
    [switch]$List
)
. "$PSScriptRoot\_common.ps1"
$null = Initialize-Poc -Serial $Serial
if (-not ((Invoke-Adb shell pm list packages $script:Pkg) -join '')) { throw 'Instale a bancada antes (poc-prepare.ps1).' }

if ($List) {
    Invoke-RunAs 'ls -l files/model 2>/dev/null'
    return
}
if (-not $Models) { throw 'Informe -Models (ids de poc\assets\models.json) ou use -List.' }
if ($Remove) {
    $ids = if ($Models -contains 'all') { (Get-PocCatalog).id } else { $Models }
    foreach ($id in $ids) {
        $m = Find-PocModel $id
        Invoke-RunAs "rm -f files/model/$($m.file)" | Out-Null
        Write-Host "$id removido do aparelho."
    }
    return
}

$entries = $Models | ForEach-Object { Find-PocModel $_ }
foreach ($m in $entries) {
    if ($m.bytes -gt 2000000000) { throw "$($m.id) passa do teto de 2.000.000.000 bytes (S3, critério 2): não é candidato aprovável." }
}

# Espaço: maior arquivo × 2 (cópia temporária + destino) + o que já vai ficar instalado.
$freeKb = [int64]((Invoke-Adb shell "df -k /data | tail -1") -split '\s+' | Where-Object { $_ } | Select-Object -Index 3)
$needBytes = ($entries | Measure-Object bytes -Sum).Sum + ($entries | Measure-Object bytes -Maximum).Maximum
if ($freeKb * 1024 -lt $needBytes) {
    throw ("Espaço insuficiente: livres {0:N0} MB, preciso de ~{1:N0} MB (modelos + cópia temporária)." -f ($freeKb / 1024), ($needBytes / 1MB))
}

Invoke-RunAs 'mkdir -p files/model' | Out-Null
foreach ($m in $entries) {
    $local = Join-Path $Source $m.file
    if (-not (Test-Path $local)) { throw "Arquivo não encontrado: $local" }
    if ((Get-Item $local).Length -ne $m.bytes) { throw "$($m.file): tamanho local diferente do catálogo ($((Get-Item $local).Length) ≠ $($m.bytes))." }

    $remote = "files/model/$($m.file)"
    $existing = (Invoke-RunAs "stat -c %s $remote 2>/dev/null") -join ''
    if (($existing -eq "$($m.bytes)") -and -not $Force) {
        Write-Host "$($m.id): já está no aparelho com o tamanho certo (use -Force para recopiar)."
    } else {
        Write-Host "$($m.id): enviando $([math]::Round($m.bytes / 1MB)) MB…"
        $tmp = "/data/local/tmp/$($m.file)"
        Invoke-Adb push $local $tmp | Out-Null
        try {
            Invoke-RunAs "cp $tmp $remote"
        } finally {
            Invoke-Adb shell rm -f $tmp | Out-Null
        }
    }

    Write-Host "$($m.id): conferindo SHA-256 no aparelho…"
    $sum = ((Invoke-RunAs "sha256sum $remote") -join '').Split(' ')[0]
    if ($sum -ne $m.sha256) { throw "$($m.id): SHA-256 diferente do catálogo ($sum). Arquivo corrompido ou trocado." }
    Write-Host "$($m.id): ok."
}
