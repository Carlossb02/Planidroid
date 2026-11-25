$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$jar = Join-Path $PSScriptRoot 'gradle/wrapper/gradle-wrapper.jar'
if (!(Test-Path $jar)) {
    Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -OutFile "$jar.tmp"
    Move-Item "$jar.tmp" $jar -Force
}
Write-Host 'Gradle wrapper preparado. Abre esta carpeta en Android Studio.'
