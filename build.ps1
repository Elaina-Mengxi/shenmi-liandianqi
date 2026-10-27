param(
    [string]$JdkPath = $env:JAVA_HOME,
    [string]$SdkPath = $env:ANDROID_SDK_ROOT,
    [string]$BuildToolsVersion = '35.0.0',
    [string]$BuildToolsPath,
    [string]$PlatformJar
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (!$SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (!$JdkPath -or !(Test-Path -LiteralPath (Join-Path $JdkPath 'bin\javac.exe'))) {
    throw 'Set JAVA_HOME or pass -JdkPath with a JDK 17 installation.'
}
if (!$SdkPath -and (!$BuildToolsPath -or !$PlatformJar)) {
    throw 'Set ANDROID_SDK_ROOT or pass -SdkPath.'
}
$bt = $BuildToolsPath
if (!$bt) { $bt = Join-Path $SdkPath "build-tools\$BuildToolsVersion" }
$platform = $PlatformJar
if (!$platform) { $platform = Join-Path $SdkPath 'platforms\android-35\android.jar' }
if (!(Test-Path -LiteralPath $platform) -or !(Test-Path -LiteralPath (Join-Path $bt 'aapt.exe'))) {
    throw 'Install Android SDK Platform 35 and Build Tools 35.0.0; set ANDROID_SDK_ROOT or pass -SdkPath.'
}
$env:JAVA_HOME = $JdkPath
$env:PATH = "$JdkPath\bin;" + $env:PATH
function Check { if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" } }
$outRoot = Join-Path $PSScriptRoot 'out'
$buildId = [Guid]::NewGuid().ToString('N')
$classes = Join-Path $outRoot "classes-$buildId"
$dex = Join-Path $outRoot "dex-$buildId"
New-Item -ItemType Directory -Force -Path out,$classes,$dex | Out-Null
& "$bt\aapt.exe" package -f -M AndroidManifest.xml -S res -I $platform -F out\base.apk
Check
$sources = @(Get-ChildItem -LiteralPath src -Filter '*.java' -Recurse | ForEach-Object FullName)
& "$JdkPath\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $platform -d $classes $sources
Check
& "$JdkPath\bin\jar.exe" cf out\classes.jar -C $classes .
Check
& "$bt\d8.bat" --lib $platform --min-api 24 --output $dex out\classes.jar
Check
Copy-Item -LiteralPath out\base.apk -Destination out\unsigned.apk -Force
Push-Location -LiteralPath $dex
try {
    & "$bt\aapt.exe" add ..\unsigned.apk classes.dex
    Check
} finally { Pop-Location }
& "$bt\zipalign.exe" -f 4 out\unsigned.apk out\aligned.apk
Check
if (!(Test-Path -LiteralPath out\local.keystore)) {
    & "$JdkPath\bin\keytool.exe" -genkeypair -keystore out\local.keystore -storepass android -keypass android -alias local -dname 'CN=Pause Loop Development' -keyalg RSA -keysize 2048 -validity 10000
    Check
}
$manifest = [xml](Get-Content -LiteralPath AndroidManifest.xml -Raw)
$version = $manifest.manifest.GetAttribute('versionName', 'http://schemas.android.com/apk/res/android')
$apk = "out\PauseLoop-v$version.apk"
& "$bt\apksigner.bat" sign --ks out\local.keystore --ks-pass pass:android --key-pass pass:android --out $apk out\aligned.apk
Check
& "$bt\apksigner.bat" verify --verbose $apk
Check
Write-Output "Built: $apk"
