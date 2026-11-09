# Captura no pacote isolado de validação (app.accord.uivalidation, dados sintéticos). Aceita AVD ou aparelho conectado; fonte, rotação e política de rotação são salvas e restauradas. Não altera recursos do Windows.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [ValidateSet('before', 'after')][string]$Round = 'before',
    [ValidateSet('light', 'dark')][string[]]$Themes = @('light', 'dark'),
    # accord-rub9: matriz vigente em retrato e fonte padrão.
    [ValidateSet('1')][string[]]$FontScales = @('1'),
    [ValidateSet('0')][string[]]$Rotations = @('0'),
    [ValidatePattern('^app\.accord\.[A-Za-z0-9]+(#[\p{L}0-9_]+)?$')][string[]]$Classes = @('app.accord.UiAuditAccessCaptureTest', 'app.accord.UiAuditCaptureTest'),
    [string]$OutputDirectory = 'app/build/ui-audit',
    [string]$ApkDirectory,
    [switch]$ContinueOnFailure
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$package = 'app.accord.uivalidation'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
function Invoke-Adb([string[]]$Arguments) {
    # No Windows PowerShell 5.1, progresso legítimo no stderr vira ErrorRecord.
    # A falha é decidida pelo exit code, não pela presença de stderr.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $adb -s $Serial @Arguments 2>&1
        $nativeExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($nativeExitCode -ne 0) { throw "adb falhou: $($Arguments -join ' ')`n$($result -join "`n")" }
    return $result
}
Push-Location $repo
$saved = @{}
$savedRotationPolicy = $null
$oldJava = $env:JAVA_HOME
$oldAndroid = $env:ANDROID_HOME
$failures = @()
try {
    if ((Invoke-Adb -Arguments @('shell', 'getprop', 'sys.boot_completed') | Out-String).Trim() -ne '1') {
        throw 'O AVD precisa terminar de iniciar antes da auditoria.'
    }
    $runId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $output = [IO.Path]::GetFullPath((Join-Path $OutputDirectory "$Round/$Serial/$runId"))
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $revision = (& git rev-parse HEAD | Out-String).Trim()
    # Avisos de CRLF do git vão ao stderr e viram erro fatal no PowerShell 5.1; decidir só pelo exit code.
    $ErrorActionPreference = 'Continue'
    & git diff HEAD -- app scripts 2>$null | Out-File (Join-Path $output 'worktree.diff') -Encoding utf8
    $ErrorActionPreference = 'Stop'
    $env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if (-not $ApkDirectory) {
        & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-Paccord.uiValidation=true'
        if ($LASTEXITCODE -ne 0) { throw 'Build da auditoria falhou.' }
        $ApkDirectory = 'app/build/outputs/apk'
    }
    $apk = Join-Path $ApkDirectory 'debug/app-debug.apk'
    $testApk = Join-Path $ApkDirectory 'androidTest/debug/app-debug-androidTest.apk'
    $apkHash = (Get-FileHash $apk -Algorithm SHA256).Hash
    $testApkHash = (Get-FileHash $testApk -Algorithm SHA256).Hash
    Invoke-Adb -Arguments @('install', '-r', $apk) | Out-Null
    Invoke-Adb -Arguments @('install', '-r', $testApk) | Out-Null
    foreach ($key in @('font_scale', 'accelerometer_rotation', 'user_rotation')) {
        $saved[$key] = (Invoke-Adb -Arguments @('shell', 'settings', 'get', 'system', $key) | Out-String).Trim()
    }
    $savedRotationPolicy = (Invoke-Adb -Arguments @('shell', 'wm', 'fixed-to-user-rotation') | Out-String).Trim()
    if ($savedRotationPolicy -notin @('default', 'enabled', 'disabled')) { throw "Política de rotação desconhecida: $savedRotationPolicy" }
    Invoke-Adb -Arguments @('shell', 'wm', 'fixed-to-user-rotation', 'enabled') | Out-Null
    Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0') | Out-Null
    foreach ($rotation in $Rotations) { foreach ($scale in $FontScales) { foreach ($theme in $Themes) { foreach ($class in $Classes) {
        # Nomes de método Kotlin podem exceder o MAX_PATH do adb no Windows.
        # Identidade completa fica no manifesto; caminho usa nome curto + hash do seletor.
        $className = ($class.Split('#')[0] -split '\.')[-1]
        $classFolder = $className
        if ($class.Contains('#')) {
            $hasher = [Security.Cryptography.SHA256]::Create()
            try { $selectorHash = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($class))).Replace('-', '').Substring(0, 8).ToLowerInvariant() }
            finally { $hasher.Dispose() }
            $classFolder = "$className-$selectorHash"
        }
        $folder = Join-Path $output "rotation-$rotation/font-$scale/$theme/$classFolder"
        New-Item -ItemType Directory -Path $folder -Force | Out-Null
        Write-Output "Captura: $Round / $theme / fonte $scale / rotacao $rotation / $class"
        # Limpeza restrita ao pacote de validação: identidade/banco são sintéticos pelo runner.
        Invoke-Adb -Arguments @('shell', 'pm', 'clear', $package) | Out-Null
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'font_scale', $scale) | Out-Null
        Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', $rotation) | Out-Null
        $metadata = [ordered]@{
            revision = $revision; round = $Round; serial = $Serial; theme = $theme
            apkSha256 = $apkHash; testApkSha256 = $testApkHash
            fontScale = $scale; rotation = $rotation; testClass = $class
            started = (Get-Date).ToString('o'); status = 'running'; synthetic = $true
            avd = $(try { (Invoke-Adb -Arguments @('emu', 'avd', 'name') | Out-String).Trim() } catch { 'dispositivo físico' })
            size = (Invoke-Adb -Arguments @('shell', 'wm', 'size') | Out-String).Trim()
            density = (Invoke-Adb -Arguments @('shell', 'wm', 'density') | Out-String).Trim()
            graphicsRenderer = ((Invoke-Adb -Arguments @('shell', 'dumpsys', 'SurfaceFlinger') | Select-String 'GLES:' | ForEach-Object { $_.Line.Trim() }) -join "`n")
            rotationPolicy = 'fixed-to-user-rotation enabled'
        }
        $metadata | ConvertTo-Json | Set-Content (Join-Path $folder 'run.json') -Encoding utf8
        try {
            $result = Invoke-Adb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $class,
                '-e', 'auditTheme', $theme, '-e', 'auditRound', $Round,
                "$package.test/app.accord.AccordTestRunner")
            $log = $result -join "`n"
            $log | Set-Content (Join-Path $folder 'instrumentation.txt') -Encoding utf8
            # am instrument pode retornar exit 0 mesmo com testes falhando.
            if ($log -notmatch 'OK \([1-9][0-9]* tests?\)' -or $log -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=') {
                throw "Instrumentação incompleta ou falhou: $class. Veja instrumentation.txt."
            }
            $metadata.status = 'captured-not-reviewed'
        } catch {
            $metadata.status = 'failed'
            $metadata.error = $_.Exception.Message
            if (-not $ContinueOnFailure) { throw }
        } finally {
            try {
                Invoke-Adb -Arguments @('pull', "/sdcard/Android/data/$package/files/screens", (Join-Path $folder 'screens')) | Out-Null
                $metadata.screenshots = @(Get-ChildItem (Join-Path $folder 'screens') -Recurse -Filter '*.png').Count
            } catch {
                $metadata.captureError = $_.Exception.Message
            }
            if (-not $metadata.screenshots) { $metadata.status = 'failed-no-screenshots' }
            if ($metadata.screenshots) {
                $dimensions = @()
                foreach ($png in Get-ChildItem (Join-Path $folder 'screens') -Recurse -Filter '*.png') {
                    $bytes = [IO.File]::ReadAllBytes($png.FullName)
                    $width = [int]($bytes[16] * 16777216 + $bytes[17] * 65536 + $bytes[18] * 256 + $bytes[19])
                    $height = [int]($bytes[20] * 16777216 + $bytes[21] * 65536 + $bytes[22] * 256 + $bytes[23])
                    $dimensions += [ordered]@{ file = $png.Name; width = $width; height = $height }
                }
                $metadata.pngDimensions = $dimensions
                $mismatch = @($dimensions | Where-Object { if ($rotation -eq '1') { $_.width -le $_.height } else { $_.height -le $_.width } })
                if ($mismatch.Count) {
                    $metadata.orientationMismatchCount = $mismatch.Count
                    $metadata.status = 'failed-orientation-mismatch'
                }
            }
            $metadata.finished = (Get-Date).ToString('o')
            $metadata | ConvertTo-Json | Set-Content (Join-Path $folder 'run.json') -Encoding utf8
            Write-Output "$($metadata.status): $($metadata.screenshots) PNGs"
        }
        if ($metadata.status -ne 'captured-not-reviewed') {
            $failures += $folder
            if (-not $ContinueOnFailure) { throw "Captura incompleta: $folder" }
            Write-Warning "Captura incompleta preservada: $folder"
        }
    } } } }
    if ($failures.Count) { throw "$($failures.Count) execuções incompletas. Consulte run.json em $output" }
    Write-Output "Evidências capturadas, ainda sem avaliação visual: $output"
} finally {
    if ($savedRotationPolicy -in @('default', 'enabled', 'disabled')) {
        try { Invoke-Adb -Arguments @('shell', 'wm', 'fixed-to-user-rotation', $savedRotationPolicy) | Out-Null }
        catch { Write-Warning "Não foi possível restaurar a política de rotação: $_" }
    }
    foreach ($key in $saved.Keys) {
        try {
            if ($saved[$key] -eq 'null') { Invoke-Adb -Arguments @('shell', 'settings', 'delete', 'system', $key) | Out-Null }
            else { Invoke-Adb -Arguments @('shell', 'settings', 'put', 'system', $key, $saved[$key]) | Out-Null }
        } catch { Write-Warning "Não foi possível restaurar $key no $Serial`: $_" }
    }
    $env:JAVA_HOME = $oldJava
    $env:ANDROID_HOME = $oldAndroid
    Pop-Location
}
