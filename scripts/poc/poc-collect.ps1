# Copia os resultados (JSON Lines) da bancada para poc\results\<Label> no computador. Os arquivos da bancada
# não têm o texto das frases reais do casal; mesmo assim a pasta é ignorada pelo Git.
# Uso: .\scripts\poc\poc-collect.ps1 -Label s20fe [-Serial XXXX] [-Quiet]
param(
    [Parameter(Mandatory)][string]$Label,
    [string]$Serial,
    [switch]$Quiet
)
. "$PSScriptRoot\_common.ps1"
$null = Initialize-Poc -Serial $Serial
$dest = Get-PocResultsDir $Label

$names = @(Invoke-RunAs 'ls files/results 2>/dev/null') | Where-Object { $_ -like '*.jsonl' }
$copied = 0
foreach ($name in $names) {
    $target = Join-Path $dest $name
    $size = [int64]((Invoke-RunAs "stat -c %s files/results/$name") -join '')
    if ((Test-Path $target) -and ((Get-Item $target).Length -eq $size)) { continue }
    # cmd preserva os bytes (o pipeline do PowerShell 5.1 os corromperia).
    cmd /c "`"$script:AdbExe`" -s $script:Serial exec-out run-as $script:Pkg cat files/results/$name > `"$target`""
    if ((Get-Item $target).Length -ne $size) { throw "Cópia incompleta de $name" }
    $copied++
}
if (-not $Quiet) { Write-Host "Resultados: $copied novos arquivos em $dest ($($names.Count) no aparelho)." }
