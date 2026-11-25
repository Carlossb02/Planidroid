param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$Tag,
    [Parameter(Mandatory=$true)][string]$PreviousApk
)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
$version = $Tag -creplace '^v',''
if ($version -cnotmatch '^(0|[1-9][0-9]*)(\.(0|[1-9][0-9]*)){3}$') { throw 'Tag invalido: usa x.x.x.x.' }
if ((Split-Path $Apk -Leaf) -cne "Planidroid_v$version.apk") { throw 'El nombre del APK no coincide con el tag.' }
$sdk = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'build-tools')) } | Select-Object -First 1
if (!$sdk) { throw 'No se ha encontrado Android SDK. Configura ANDROID_HOME.' }
$bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Where-Object { (Test-Path (Join-Path $_.FullName 'aapt.exe')) -and (Test-Path (Join-Path $_.FullName 'apksigner.bat')) } | Sort-Object Name -Descending | Select-Object -First 1
if (!$bt) { throw 'Instala Android SDK Build Tools.' }
$aapt = Join-Path $bt.FullName 'aapt.exe'
$signer = Join-Path $bt.FullName 'apksigner.bat'
function Read-Metadata([string]$path) {
    $resolved = (Resolve-Path -LiteralPath $path).Path
    $badging = & $aapt dump badging $resolved
    if ($LASTEXITCODE -ne 0) { throw 'No se puede leer el APK.' }
    $line = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
    if ($line -notmatch "name='([^']+)' versionCode='([0-9]+)' versionName='([^']*)'") { throw 'Metadatos APK no validos.' }
    $package = $Matches[1]; $code = [long]$Matches[2]; $name = $Matches[3]
    $verified = & $signer verify --print-certs $resolved
    if ($LASTEXITCODE -ne 0) { throw 'APK sin firma valida.' }
    $certs = @($verified | ForEach-Object { if ($_ -match '^Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$') { $Matches[1].ToLowerInvariant() } } | Sort-Object -Unique)
    if ($certs.Count -eq 0) { throw 'No se pudo comprobar el certificado.' }
    return @{ Package=$package; Code=$code; Version=$name; Certs=$certs }
}
$new = Read-Metadata $Apk
$old = Read-Metadata $PreviousApk
$gradle = Get-Content -Raw app/build.gradle.kts
if ($gradle -notmatch 'versionName\s*=\s*"([^"]+)"') { throw 'versionName no encontrado en Gradle.' }
$gradleVersion = $Matches[1]
if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') { throw 'versionCode no encontrado en Gradle.' }
$gradleCode = [long]$Matches[1]
if ($new.Package -cne 'es.planidroid.app' -or $new.Package -cne $old.Package) { throw 'Identidad del paquete incorrecta.' }
if ($new.Version -cne $version -or $new.Version -cne $gradleVersion -or $new.Code -ne $gradleCode) { throw "Tag, Gradle y APK no coinciden. APK: $($new.Version) / $($new.Code)." }
if ($new.Code -le $old.Code) { throw "versionCode debe aumentar: $($old.Code) -> $($new.Code)." }
if ([version]$new.Version -le [version]$old.Version) { throw 'versionName debe aumentar.' }
if (Compare-Object $old.Certs $new.Certs) { throw 'La firma no coincide con la version anterior.' }
Write-Host "APK valido para actualizar $($old.Version) ($($old.Code)) a $($new.Version) ($($new.Code))."
