# Executa um estágio da Etapa 0 no aparelho. Tudo é medido pela bancada; este script só dirige e espera.
#   info     ficha do aparelho (também feita por poc-prepare.ps1)
#   verify   SHA-256 do modelo no aparelho
#   cold     carga a frio (carga + primeira inferência), -Reps vezes. Com -ProcessFresh cada repetição reinicia o processo
#   battery  qualidade e latência carregado: bateria fictícia + frases reais (se enviadas), -Reps repetições
#   stop     interrompe o que estiver rodando
#   session  protocolo de bateria e calor (30 min, 1 frase a cada 30 s): exige 100%, modo avião e cabo desconectado
# Uso: .\scripts\poc\poc-run.ps1 -Stage battery -Model pulse-qwen3-0.6b-q4km -Label s20fe -Routed [-Reps 3] [-Threads 4]
# -Routed liga o perfil em etapas (classificar a ação, ligar trechos, classificar a categoria) com referências semânticas:
# o mesmo que deu os resultados do PC (routed-v14). Sem -Routed vale o prompt único, que acerta bem menos nos modelos pequenos.
param(
    [Parameter(Mandatory)][ValidateSet('info', 'verify', 'cold', 'battery', 'session', 'stop')][string]$Stage,
    [string]$Model,
    [Parameter(Mandatory)][string]$Label,
    [string]$Tag,
    [int]$Reps = 3,
    [int]$Threads = 4,
    [int]$Ctx = 4096,
    [int]$Minutes = 30,
    [int]$IntervalSec = 30,
    [int]$DelaySec = 60,
    [switch]$NoPrivate,
    [switch]$NoGrammar,
    [switch]$SmallContext,
    [switch]$NamedCategories,
    [switch]$Routed,
    [ValidateSet(14, 15, 16, 17, 18, 19)][int]$RoutedVersion = 14,
    [string]$Only,
    [switch]$ProcessFresh,
    [switch]$NoWait,
    [switch]$Force,
    [string]$Serial
)
. "$PSScriptRoot\_common.ps1"
$null = Initialize-Poc -Serial $Serial
if ($Stage -notin 'info', 'stop' -and -not $Model) { throw '-Model é obrigatório (ids em poc\assets\models.json).' }
if ($Model) { $null = Find-PocModel $Model }
# O prompt com 36 categorias tem ~2.300 tokens e a geração soma até 200: com janela menor toda mensagem falha com 'Memory'
# antes de o modelo ser chamado (rodada inválida, como a de 25/10/2026 com 2048). O PC usou 4096.
if ($Stage -in 'battery', 'session', 'cold' -and $Ctx -lt 4096 -and -not $SmallContext -and -not $Force) {
    throw "-Ctx $Ctx é pequeno para o prompt do produto (36 categorias): as mensagens falhariam antes do modelo. Use 4096 (padrão), -SmallContext ou -Force."
}
if (-not $Tag) { $Tag = "$Label-$Model-t$Threads" + $(if ($Routed) { '-routed' } else { '' }) }
$Tag = $Tag -replace '[^A-Za-z0-9._-]', '_'

function Start-Stage {
    param([string]$StageName, [string]$StageTag, [int]$Repetitions)
    # Sem isto, o 'Done' da rodada anterior seria lido como o fim desta.
    $busy = Get-PocStatus
    if ($busy -and $busy.phase -eq 'Running') { throw "A bancada já está executando '$($busy.stage)'. Espere ou interrompa com: .\scripts\poc\poc-run.ps1 -Stage stop -Label $Label" }
    Invoke-RunAs 'rm -f files/status.json' | Out-Null
    $amArgs = @(
        'shell', 'am', 'start', '-n', "$script:Pkg/.PocActivity",
        '--es', 'stage', $StageName, '--es', 'tag', $StageTag,
        '--ei', 'reps', "$Repetitions", '--ei', 'threads', "$Threads", '--ei', 'ctx', "$Ctx",
        '--ei', 'minutes', "$Minutes", '--ei', 'interval', "$IntervalSec", '--ei', 'delay', "$DelaySec",
        '--ez', 'private', $(if ($NoPrivate) { 'false' } else { 'true' }),
        '--ez', 'grammar', $(if ($NoGrammar) { 'false' } else { 'true' }),
        '--ez', 'small', $(if ($SmallContext) { 'true' } else { 'false' }),
        '--ez', 'namedcat', $(if ($NamedCategories) { 'true' } else { 'false' }),
        '--ez', 'routed', $(if ($Routed) { 'true' } else { 'false' }), '--ei', 'rv', "$RoutedVersion"
    )
    if ($Model) { $amArgs += @('--es', 'model', $Model) }
    if ($Only) { $amArgs += @('--es', 'only', $Only) }
    Invoke-Adb @amArgs | Out-Null
}

if ($Stage -eq 'stop') {
    Invoke-Adb shell am start -n "$script:Pkg/.PocActivity" --es stage stop | Out-Null
    Write-Host 'Pedido de parada enviado.'
    return
}

if ($Stage -eq 'session') {
    $levelLine = (Invoke-Adb shell dumpsys battery) | Where-Object { $_ -match '^\s*level:' } | Select-Object -First 1
    $level = [int](($levelLine -split ':')[1].Trim())
    $airplane = ((Invoke-Adb shell settings get global airplane_mode_on) -join '').Trim()
    $problems = @()
    if ($level -lt 100) { $problems += "bateria em $level% (o protocolo exige 100%)" }
    if ($airplane -ne '1') { $problems += 'modo avião desligado' }
    if ($problems -and -not $Force) { throw "Sessão não iniciada: $($problems -join '; '). Use -Force só para ensaio (a rodada ficará marcada como inválida)." }
    Start-Stage 'session' $Tag 1
    Write-Host ''
    Write-Host "Sessão iniciada: a bancada conta $DelaySec s e depois mede por $Minutes min (1 frase a cada $IntervalSec s)."
    Write-Host '  1. DESCONECTE O CABO USB agora (sem carregador durante a sessão).'
    Write-Host '  2. Não toque no aparelho e deixe a tela ligada.'
    Write-Host "  3. Depois de ~$($Minutes + 3) min reconecte e rode: .\scripts\poc\poc-collect.ps1 -Label $Label"
    return
}

if ($Stage -eq 'cold' -and $ProcessFresh) {
    for ($i = 1; $i -le $Reps; $i++) {
        Invoke-Adb shell am force-stop $script:Pkg | Out-Null
        Start-Sleep -Seconds 2
        Start-Stage 'cold' "$Tag-p$i" 1
        $null = Wait-PocJob -TimeoutMinutes 15
    }
} else {
    Start-Stage $Stage $Tag $Reps
    if (-not $NoWait) {
        $status = Wait-PocJob -TimeoutMinutes 90
        if ($status.phase -ne 'Done') { throw "A bancada terminou como $($status.phase): $($status.message)" }
    }
}
& "$PSScriptRoot\poc-collect.ps1" -Label $Label -Serial $script:Serial
