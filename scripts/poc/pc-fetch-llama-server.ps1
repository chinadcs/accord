# Baixa o llama-server PRONTO (binário oficial, CPU, Windows x64) da MESMA versão fixada em poc/llama-cpp.pin, para
# poc/third_party/llama-server-pc (ignorado pelo Git). Serve só à bateria de QUALIDADE no PC (:pocpc); a bancada do
# celular continua compilando o código-fonte com o NDK. Não instala nada no sistema e não roda nenhum modelo.
# Confere o SHA-256 do zip contra o resumo publicado pelo GitHub e a versão impressa pelo próprio servidor contra o pino.
# Uso: .\scripts\poc\pc-fetch-llama-server.ps1 [-Force] [-Pattern 'bin-win-cpu-x64']
param(
    [switch]$Force,
    [string]$Pattern = 'bin-win-cpu-x64\.zip$'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pin = @{}
Get-Content (Join-Path $root 'poc\llama-cpp.pin') | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    $pin[$k] = $v.Trim()
}
$dest = Join-Path $root 'poc\third_party\llama-server-pc'
$exe = Join-Path $dest 'llama-server.exe'

function Test-Pin([string]$Exe) {
    # --version escreve em stderr mesmo com sucesso. No Windows PowerShell 5.1,
    # 2>&1 transforma isso em NativeCommandError com ErrorActionPreference=Stop.
    $process = New-Object System.Diagnostics.Process
    try {
        $process.StartInfo.FileName = $Exe
        $process.StartInfo.Arguments = '--version'
        $process.StartInfo.UseShellExecute = $false
        $process.StartInfo.CreateNoWindow = $true
        $process.StartInfo.RedirectStandardOutput = $true
        $process.StartInfo.RedirectStandardError = $true
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $text = $stdout.GetAwaiter().GetResult() + "`n" + $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { return $false }
    } finally {
        $process.Dispose()
    }
    if (($text -match 'version:\s*\S+\s*\(build\s+(\d+),\s*commit\s+([0-9a-f]+)\)') -or
        ($text -match 'version:\s*(\d+)\s*\(([0-9a-f]+)\)')) {
        return ($Matches[1] -eq $pin.tag.TrimStart('b')) -and $pin.commit.StartsWith($Matches[2])
    }
    return $false
}

if ((Test-Path $exe) -and -not $Force) {
    if (Test-Pin $exe) { Write-Host "llama-server $($pin.tag) já está em $dest"; return }
    throw "Há outro llama-server em $dest. Use -Force para refazer."
}

[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$release = Invoke-RestMethod -UseBasicParsing -Uri "$($pin.repo.Replace('https://github.com/', 'https://api.github.com/repos/'))/releases/tags/$($pin.tag)" -Headers @{ 'User-Agent' = 'accord-poc' }
$asset = @($release.assets | Where-Object { $_.name -match $Pattern }) | Select-Object -First 1
if (-not $asset) { throw "Nenhum arquivo da versão $($pin.tag) casa com '$Pattern'. Disponíveis: $(($release.assets.name) -join ', ')" }
if (-not $asset.digest -or $asset.digest -notmatch '^sha256:([0-9a-f]{64})$') {
    throw "O GitHub não publicou o SHA-256 de $($asset.name); não vou usar um binário sem conferência."
}
$expected = $Matches[1]

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) "accord-llama-server-$($pin.tag)"
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory -Force $tmp | Out-Null
$zip = Join-Path $tmp $asset.name
Write-Host "Baixando $($asset.name) ($([math]::Round($asset.size / 1MB, 1)) MB)…"
Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile $zip -Headers @{ 'User-Agent' = 'accord-poc' }
$actual = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw "SHA-256 difere do publicado: $actual (esperado $expected)." }

if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
New-Item -ItemType Directory -Force $dest | Out-Null
Expand-Archive -Path $zip -DestinationPath $dest -Force
if (-not (Test-Path $exe)) {
    $found = Get-ChildItem -Recurse -Path $dest -Filter 'llama-server.exe' | Select-Object -First 1
    if (-not $found) { throw "llama-server.exe não está em $($asset.name)." }
    Get-ChildItem -Path $found.Directory.FullName | Move-Item -Destination $dest -Force
}
Remove-Item -Recurse -Force $tmp

if (-not (Test-Pin $exe)) { throw "O llama-server baixado não imprime a versão fixada ($($pin.tag), $($pin.commit.Substring(0, 7)))." }
Write-Host "llama-server $($pin.tag) ($($pin.commit.Substring(0, 7))) em $dest · zip SHA-256 $actual"
