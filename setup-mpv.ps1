$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$target = Join-Path $PSScriptRoot "tools\mpv"
$archive = Join-Path $target "mpv.7z"
$url = "https://sourceforge.net/projects/mpv-player-windows/files/64bit/mpv-x86_64-20260830-git-e8673660ab.7z/download"
$sevenZip = "C:\Program Files\7-Zip\7z.exe"

New-Item $target -ItemType Directory -Force | Out-Null
if (-not (Test-Path $sevenZip)) {
    throw "7-Zip is required to unpack mpv. Expected: $sevenZip"
}

Write-Host "Downloading portable mpv..."
& curl.exe -L --fail --retry 2 -o $archive $url
if ($LASTEXITCODE -ne 0) { throw "mpv download failed" }

Write-Host "Extracting mpv..."
& $sevenZip x $archive "-o$target" -y | Out-Null
if ($LASTEXITCODE -ne 0) { throw "mpv extraction failed" }
if (-not (Test-Path (Join-Path $target "mpv.exe"))) { throw "mpv.exe was not produced" }
Write-Host "mpv ready: tools\mpv\mpv.exe"
