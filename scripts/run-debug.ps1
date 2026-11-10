# Ciclo rápido no emulador/aparelho: instala o APK de debug e abre o app.
# Uso:
#   .\scripts\run-debug.ps1                       # instala e abre
#   .\scripts\run-debug.ps1 -Fresh                # limpa os dados do app antes de abrir
#   .\scripts\run-debug.ps1 -Shot tela.png        # salva captura da tela depois de abrir
#   .\scripts\run-debug.ps1 -Dump                 # imprime a hierarquia de views (uiautomator)
#   .\scripts\run-debug.ps1 -TestClass app.accord.AccessUiTest   # um teste instrumentado só
# Com mais de um dispositivo conectado, informe -Serial (ex.: emulator-5554).
# Só para emulador: o debug usa o mesmo applicationId do release (app.accord) e o instalaria por cima (ou o
# desinstalaria) num aparelho com o app do usuário. Em aparelho físico use o pacote isolado app.accord.uivalidation
# (-Paccord.uiValidation=true), como em scripts/run-ui-audit.ps1.
param(
    [string]$Serial,
    [switch]$Fresh,
    [string]$Shot,
    [switch]$Dump,
    [string]$TestClass
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$pkg = 'app.accord'

if ($Serial) { $env:ANDROID_SERIAL = $Serial }

$target = $env:ANDROID_SERIAL
if (-not $target) {
    $attached = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } | ForEach-Object { ($_ -split '\s+')[0] })
    if ($attached.Count -eq 1) { $target = $attached[0] }
    elseif ($attached.Count -gt 1) { throw 'Mais de um dispositivo conectado: informe -Serial.' }
}
if ($target -and $target -notlike 'emulator-*') {
    throw "Recusado: '$target' não é emulador. Este script instala app.accord, o mesmo id do release, e poderia substituir ou apagar o app do usuário. Use o pacote isolado app.accord.uivalidation (-Paccord.uiValidation=true; ver scripts/run-ui-audit.ps1)."
}

Push-Location $root
try {
    if ($TestClass) {
        .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
        return
    }

    .\gradlew.bat :app:installDebug
    if ($LASTEXITCODE -ne 0) { throw 'installDebug falhou' }

    if ($Fresh) { & $adb shell pm clear $pkg | Out-Null }
    & $adb shell am start -n "$pkg/.MainActivity" | Out-Null

    if ($Shot) {
        Start-Sleep -Seconds 2
        # cmd preserva os bytes do PNG; o pipeline do PowerShell 5.1 os corromperia.
        cmd /c "`"$adb`" exec-out screencap -p > `"$Shot`""
        Write-Host "Captura salva em $Shot"
    }
    if ($Dump) {
        & $adb shell uiautomator dump /sdcard/ui.xml | Out-Null
        & $adb exec-out cat /sdcard/ui.xml
    }
}
finally {
    Pop-Location
}
