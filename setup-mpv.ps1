$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$target = Join-Path $PSScriptRoot "tools\mpv"
$archive = Join-Path $target "mpv.7z"
$partial = "$archive.part"
$url = "https://sourceforge.net/projects/mpv-player-windows/files/64bit/mpv-x86_64-20260830-git-e8673660ab.7z/download"
$expectedSha256 = "464AB69B2248E7B592F0C27A927FFD1F016F7FA2D6D8B46B1A98254C5F2B670A"
$sevenZip = "C:\Program Files\7-Zip\7z.exe"

New-Item $target -ItemType Directory -Force | Out-Null
if (-not (Test-Path $sevenZip)) {
    throw "7-Zip is required to unpack mpv. Expected: $sevenZip"
}

function Test-ArchiveHash {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $false }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -eq $expectedSha256
}

if (-not (Test-ArchiveHash $archive)) {
    Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    Write-Host "Downloading portable mpv..."
    try {
        & curl.exe --fail --location --retry 2 --silent --show-error --output $partial $url
        if ($LASTEXITCODE -ne 0) { throw "mpv download failed" }
        $actualSha256 = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash
        if ($actualSha256 -ne $expectedSha256) {
            throw "mpv SHA-256 mismatch. Expected $expectedSha256, got $actualSha256"
        }
        Move-Item -LiteralPath $partial -Destination $archive -Force
    } finally {
        Remove-Item -LiteralPath $partial -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "Verified cached mpv archive SHA-256."
}

Remove-Item -LiteralPath (Join-Path $target "mpv.exe") -Force -ErrorAction SilentlyContinue
Write-Host "Extracting mpv..."
& $sevenZip x $archive "-o$target" -y | Out-Null
if ($LASTEXITCODE -ne 0) { throw "mpv extraction failed" }

$mpvExe = Join-Path $target "mpv.exe"
if (-not (Test-Path -LiteralPath $mpvExe -PathType Leaf)) {
    throw "mpv.exe was not produced"
}
Write-Host "mpv ready: tools\mpv\mpv.exe"
Write-Host "archive_sha256=$expectedSha256"
