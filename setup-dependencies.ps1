param([ValidateSet('all', 'html', 'imageio')][string]$Group = 'all')
$ErrorActionPreference = 'Stop'
foreach ($line in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dependencies.lock')) {
    if (-not $line.Trim() -or $line.StartsWith('#')) { continue }
    $relative, $sha256, $url = $line.Split('|')
    if ($Group -ne 'all' -and -not $relative.StartsWith("$Group/")) { continue }
    $target = Join-Path $PSScriptRoot "lib/$relative"
    if (Test-Path -LiteralPath $target) {
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $sha256) {
            throw "Dependency checksum mismatch: $target. Remove this file and run setup again."
        }
        Write-Output $target
        continue
    }
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    $partial = "$target.part"
    try {
        Write-Host "Downloading $relative..."
        & curl.exe --fail --location --retry 2 --silent --show-error --output $partial $url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $relative" }
        if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -ne $sha256) {
            throw "Downloaded dependency checksum mismatch: $relative"
        }
        Move-Item -LiteralPath $partial -Destination $target -Force
    } finally {
        if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
    }
    Write-Output $target
}
Write-Host "Verified dependencies: $Group"
