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
    throw "No se encontrÃ³ un JDK compatible (javac/java/jar)."
}

function Invoke-Checked {
    param([string]$Exe, [object[]]$Arguments)
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "FallÃ³ $Exe con cÃ³digo $LASTEXITCODE"
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

$sources = @(Get-ChildItem src\main\java -Recurse -Filter *.java | ForEach-Object FullName)
$tests   = @(Get-ChildItem src\test\java -Recurse -Filter *.java | ForEach-Object FullName)

Invoke-Checked $javac (@("--release","17","-encoding","UTF-8","-d","build\classes") + $sources)
Invoke-Checked $jar @("--create","--file","build\streamflix-desktop.jar","--main-class","dev.streamflix.desktop.App","-C","build\classes",".")

Invoke-Checked $javac (@("--release","17","-encoding","UTF-8","-cp","build\classes","-d","build\test-classes") + $tests)

foreach ($test in @(
    "dev.streamflix.desktop.JsonTest",
    "dev.streamflix.desktop.ProviderFixtureTest",
    "dev.streamflix.desktop.ExtractorFixtureTest"
)) {
    Invoke-Checked $java @("-cp","build\classes;build\test-classes",$test)
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

$jpackageCandidates = @(
    (Join-Path $javaBin "jpackage.exe"),
    "C:\Program Files\Tableau\Tableau 2025.1\bin\jre\bin\jpackage.exe"
)
$jpackage = $jpackageCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $jpackage) {
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { $jpackage = $cmd.Source }
}
if (-not $jpackage) { throw "No se encontrÃ³ jpackage." }

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
    "--app-version","1.0.0",
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
    Write-Warning "mpv portable no estÃ¡ disponible; ejecuta .\setup-mpv.ps1 y vuelve a compilar."
}

Write-Host ""
Write-Host "APP OK: dist\StreamflixDesktop\StreamflixDesktop.exe"
