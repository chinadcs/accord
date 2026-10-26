# Envia (ou remove) o arquivo com as frases REAIS do casal para a bancada. O arquivo fica fora do repositório
# (padrão: E:\Projects\accord-poc-private\frases-reais.tsv; formato em docs\poc\frases-reais.modelo.tsv).
# No aparelho ele vive em filesDir/private e a bancada nunca grava o texto das frases nos resultados.
# Uso: .\scripts\poc\poc-private.ps1 [-File caminho.tsv] [-Serial XXXX] | -Remove
param(
    [string]$File = 'E:\Projects\accord-poc-private\frases-reais.tsv',
    [string]$Serial,
    [switch]$Remove
)
. "$PSScriptRoot\_common.ps1"
$null = Initialize-Poc -Serial $Serial

if ($Remove) {
    Invoke-RunAs 'rm -rf files/private' | Out-Null
    Write-Host 'Frases reais removidas do aparelho.'
    return
}
if (-not (Test-Path $File)) { throw "Arquivo não encontrado: $File (veja docs\poc\frases-reais.modelo.tsv)" }
$repo = [System.IO.Path]::GetFullPath($script:Root).TrimEnd('\') + '\'
if ([System.IO.Path]::GetFullPath($File).StartsWith($repo, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'O arquivo de frases reais não pode ficar dentro do repositório (S3, história 9).'
}
$tmp = '/data/local/tmp/frases-reais.tsv'
Invoke-Adb push $File $tmp | Out-Null
try {
    Invoke-RunAs 'mkdir -p files/private'
    Invoke-RunAs "cp $tmp files/private/frases-reais.tsv"
} finally {
    Invoke-Adb shell rm -f $tmp | Out-Null
}
Write-Host "Frases reais enviadas ($((Get-Item $File).Length) bytes). A bancada valida o formato ao iniciar e registra só os números de linha com erro."
