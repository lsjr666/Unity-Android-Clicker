$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$UnityAndroid = "D:\2022.3.62f2c1\Editor\Data\PlaybackEngines\AndroidPlayer"
$Sdk = Join-Path $UnityAndroid "SDK"
$BuildTools = Join-Path $Sdk "build-tools\34.0.0"
$JavaHome = Join-Path $UnityAndroid "OpenJDK"
$AndroidJar = Join-Path $Sdk "platforms\android-35\android.jar"
$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar = Join-Path $JavaHome "bin\jar.exe"
$Keytool = Join-Path $JavaHome "bin\keytool.exe"
$Aapt2 = Join-Path $BuildTools "aapt2.exe"
$D8 = Join-Path $BuildTools "d8.bat"
$Zipalign = Join-Path $BuildTools "zipalign.exe"
$ApkSigner = Join-Path $BuildTools "apksigner.bat"

$BuildDir = Join-Path $ProjectRoot "build"
$GenDir = Join-Path $BuildDir "gen"
$ObjDir = Join-Path $BuildDir "obj"
$ClassesDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
$OutDir = Join-Path $ProjectRoot "Builds"
$UnsignedApk = Join-Path $BuildDir "clicker-unsigned.apk"
$AlignedApk = Join-Path $BuildDir "clicker-aligned.apk"
$FinalApk = Join-Path $OutDir "Clicker.apk"
$KeyStore = Join-Path $BuildDir "debug.keystore"
$ClassesJar = Join-Path $BuildDir "classes.jar"

function Invoke-Tool {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [string[]] $Arguments
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
    }
}

foreach ($Path in @($BuildDir, $GenDir, $ObjDir, $ClassesDir, $DexDir, $OutDir)) {
    New-Item -ItemType Directory -Force $Path | Out-Null
}
Remove-Item -Recurse -Force "$ObjDir\*" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$ClassesDir\*" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$DexDir\*" -ErrorAction SilentlyContinue

Write-Host "Compiling resources..."
Invoke-Tool $Aapt2 @("compile", "--dir", (Join-Path $ProjectRoot "app\src\main\res"), "-o", $ObjDir)

Write-Host "Linking APK shell..."
$FlatResources = Get-ChildItem -Filter *.flat $ObjDir | ForEach-Object { $_.FullName }
$LinkArgs = @(
    "link",
    "-I", $AndroidJar,
    "--manifest", (Join-Path $ProjectRoot "AndroidManifest.xml"),
    "--java", $GenDir,
    "--min-sdk-version", "24",
    "--target-sdk-version", "35",
    "--version-code", "1",
    "--version-name", "1.0",
    "-o", $UnsignedApk
) + $FlatResources
Invoke-Tool $Aapt2 $LinkArgs

Write-Host "Compiling Java..."
$Sources = @()
$Sources += Get-ChildItem -Recurse -Filter *.java (Join-Path $ProjectRoot "app\src\main\java") | ForEach-Object { $_.FullName }
$Sources += Get-ChildItem -Recurse -Filter *.java $GenDir | ForEach-Object { $_.FullName }
$JavacArgs = @(
    "-encoding", "UTF-8",
    "-source", "1.8",
    "-target", "1.8",
    "-bootclasspath", $AndroidJar,
    "-d", $ClassesDir
) + $Sources
Invoke-Tool $Javac $JavacArgs

Write-Host "Building dex..."
Remove-Item -Force $ClassesJar -ErrorAction SilentlyContinue
Push-Location $ClassesDir
try {
    Invoke-Tool $Jar @("cf", $ClassesJar, ".")
} finally {
    Pop-Location
}
Invoke-Tool $D8 @("--min-api", "24", "--lib", $AndroidJar, "--output", $DexDir, $ClassesJar)

Write-Host "Adding dex to APK..."
Push-Location $DexDir
try {
    Invoke-Tool $Jar @("uf", $UnsignedApk, "classes.dex")
} finally {
    Pop-Location
}

Write-Host "Aligning APK..."
Invoke-Tool $Zipalign @("-f", "-p", "4", $UnsignedApk, $AlignedApk)

if (-not (Test-Path $KeyStore)) {
    Write-Host "Creating debug keystore..."
    Invoke-Tool $Keytool @(
        "-genkeypair",
        "-keystore", $KeyStore,
        "-storepass", "android",
        "-keypass", "android",
        "-alias", "androiddebugkey",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=Clicker,O=Clicker,C=CN"
    )
}

Write-Host "Signing APK..."
Invoke-Tool $ApkSigner @(
    "sign",
    "--ks", $KeyStore,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    "--out", $FinalApk,
    $AlignedApk
)

Write-Host "Verifying signature..."
Invoke-Tool $ApkSigner @("verify", "--verbose", $FinalApk)
Remove-Item -Force "$FinalApk.idsig" -ErrorAction SilentlyContinue
Write-Host "Built $FinalApk"
