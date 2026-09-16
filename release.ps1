$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$version = (Get-Content -LiteralPath 'VERSION' -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'VERSION must be major.minor.patch' }

Write-Host "Building StreamflixDesktop v$version..."
& .\build.ps1
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$exePath = Join-Path $PSScriptRoot 'dist\StreamflixDesktop\StreamflixDesktop.exe'
if (-not (Test-Path -LiteralPath $exePath)) {
    throw "Executable not found at $exePath"
}

Write-Host "Running self-test..."
$process = Start-Process -FilePath $exePath -ArgumentList '--self-test' -Wait -NoNewWindow -PassThru
if ($process.ExitCode -ne 0) {
    throw "Self-test failed with exit code $($process.ExitCode)"
}
Write-Host "Self-test passed."

$zipName = "StreamflixDesktop-$version-windows.zip"
$zipPath = Join-Path $PSScriptRoot "dist\$zipName"

if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Write-Host "Compressing to $zipName..."
Compress-Archive -Path (Join-Path $PSScriptRoot 'dist\StreamflixDesktop') -DestinationPath $zipPath -Force

Write-Host "Calculating SHA-256..."
$hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
$hashLine = "$hash *$zipName"
Set-Content -Path (Join-Path $PSScriptRoot "dist\$zipName.sha256") -Value $hashLine

Write-Host "Release created successfully:"
Write-Host "  ZIP: dist\$zipName"
Write-Host "  SHA: dist\$zipName.sha256 ($hash)"
