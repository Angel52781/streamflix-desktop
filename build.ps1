param(
    [switch]$JarOnly
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Resolve-JavaBin {
    $candidates = @()
    if ($env:STREAMFLIX_JDK) {
        $candidates += (Join-Path $env:STREAMFLIX_JDK "bin")
        $candidates += $env:STREAMFLIX_JDK
    }
    $candidates += "C:\Program Files\Android\Android Studio\jbr\bin"
    $candidates += "C:\Program Files\Tableau\Tableau 2025.1\bin\jre\bin"

    foreach ($dir in $candidates) {
        if ($dir -and (Test-Path (Join-Path $dir "javac.exe")) -and
            (Test-Path (Join-Path $dir "java.exe")) -and
            (Test-Path (Join-Path $dir "jar.exe"))) {
            return $dir
        }
    }

    $cmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($cmd) { return Split-Path $cmd.Source }
    throw "No compatible JDK found (javac/java/jar)."
}

function Invoke-Checked {
    param([string]$Exe, [object[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Exe failed with code $LASTEXITCODE"
    }
}

$javaBin = Resolve-JavaBin
$javac = Join-Path $javaBin "javac.exe"
$java  = Join-Path $javaBin "java.exe"
$jar   = Join-Path $javaBin "jar.exe"
$runtimeImage = Split-Path $javaBin -Parent

Write-Host "Java toolchain: $javaBin"

Remove-Item build -Recurse -Force -ErrorAction SilentlyContinue
New-Item build\classes -ItemType Directory -Force | Out-Null
New-Item build\test-classes -ItemType Directory -Force | Out-Null

$imageLib = Join-Path $PSScriptRoot "lib\imageio"
if (-not (Test-Path (Join-Path $imageLib "imageio-webp-3.12.0.jar"))) {
    Write-Host "ImageIO WebP support not found; provisioning..."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "setup-imageio.ps1")
    if ($LASTEXITCODE -ne 0) { throw "ImageIO provisioning failed" }
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "setup-html.ps1")

if ($LASTEXITCODE -ne 0) { throw "Jsoup provisioning failed" }

$depJars = @(Get-ChildItem (Join-Path $PSScriptRoot "lib") -Recurse -Filter *.jar | ForEach-Object FullName)
$depCp = ($depJars -join ";")

$sources = @(Get-ChildItem src\main\java -Recurse -Filter *.java | ForEach-Object FullName)
$tests   = @(Get-ChildItem src\test\java -Recurse -Filter *.java | ForEach-Object FullName)

Invoke-Checked $javac (@("--release","17","-encoding","UTF-8","-cp",$depCp,"-d","build\classes") + $sources)
Invoke-Checked $jar @("--create","--file","build\streamflix-desktop.jar","--main-class","dev.streamflix.desktop.App","-C","build\classes",".")

Invoke-Checked $javac (@("--release","17","-encoding","UTF-8","-cp",("build\classes;" + $depCp),"-d","build\test-classes") + $tests)

foreach ($test in @(
    "dev.streamflix.desktop.JsonTest",
    "dev.streamflix.desktop.ProviderFixtureTest",
    "dev.streamflix.desktop.ExtractorFixtureTest"
)) {
    Invoke-Checked $java @("-cp",("build\classes;build\test-classes;" + $depCp),$test)
}

Write-Host "JAR OK: build\streamflix-desktop.jar"
if ($JarOnly) { exit 0 }

$mpvExe = Join-Path $PSScriptRoot "tools\mpv\mpv.exe"
if (-not (Test-Path $mpvExe)) {
    Write-Host "mpv not found; provisioning portable player..."
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "setup-mpv.ps1")
    if ($LASTEXITCODE -ne 0) { throw "mpv provisioning failed" }
}

New-Item build\package -ItemType Directory -Force | Out-Null
Copy-Item build\streamflix-desktop.jar build\package\streamflix-desktop.jar -Force
foreach ($jarFile in $depJars) { Copy-Item $jarFile build\package -Force }

$jpackageCandidates = @(
    (Join-Path $javaBin "jpackage.exe"),
    "C:\Program Files\Tableau\Tableau 2025.1\bin\jre\bin\jpackage.exe"
)
$jpackage = $jpackageCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $jpackage) {
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { $jpackage = $cmd.Source }
}
if (-not $jpackage) { throw "jpackage not found." }

Remove-Item dist -Recurse -Force -ErrorAction SilentlyContinue
Invoke-Checked $jpackage @(
    "--type","app-image",
    "--name","StreamflixDesktop",
    "--input","build\\package",
    "--main-jar","streamflix-desktop.jar",
    "--main-class","dev.streamflix.desktop.App",
    "--dest","dist",
    "--description","Streamflix Desktop",
    "--vendor","Streamflix Desktop Community Port",
    "--app-version","1.1.0",
    "--runtime-image",$runtimeImage
)

$mpvSource = Join-Path $PSScriptRoot "tools\mpv"
$mpvDest = Join-Path $PSScriptRoot "dist\StreamflixDesktop\tools\mpv"
if (Test-Path (Join-Path $mpvSource "mpv.exe")) {
    New-Item $mpvDest -ItemType Directory -Force | Out-Null
    foreach ($name in @("mpv.exe","mpv.com","d3dcompiler_43.dll")) {
        $source = Join-Path $mpvSource $name
        if (Test-Path $source) { Copy-Item $source $mpvDest -Force }
    }
    if (Test-Path (Join-Path $mpvSource "mpv")) {
        Copy-Item (Join-Path $mpvSource "mpv") $mpvDest -Recurse -Force
    }
} else {
    Write-Warning "Portable mpv is unavailable; run .\setup-mpv.ps1 and rebuild."
}

Write-Host ""
Write-Host "APP OK: dist\StreamflixDesktop\StreamflixDesktop.exe"
