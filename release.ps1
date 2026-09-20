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

# The public Streamflix archive does not redistribute mpv. The app provisions the
# pinned upstream runtime on first launch into %LOCALAPPDATA% after SHA-256 verification.
$bundledMpvDir = Join-Path $PSScriptRoot 'dist\StreamflixDesktop\tools\mpv'
if (Test-Path -LiteralPath $bundledMpvDir) {
    Remove-Item -LiteralPath $bundledMpvDir -Recurse -Force
}
if (Test-Path -LiteralPath (Join-Path $bundledMpvDir 'mpv.exe')) {
    throw 'Bundled mpv remained in the public release image'
}
$mpvNoticeDir = Join-Path $PSScriptRoot 'dist\StreamflixDesktop\third_party\mpv'
foreach ($notice in @('Copyright','LICENSE.GPL','LICENSE.LGPL','SOURCE.txt')) {
    if (-not (Test-Path -LiteralPath (Join-Path $mpvNoticeDir $notice) -PathType Leaf)) {
        throw "Public package is missing mpv provenance/license notice: $notice"
    }
}
Write-Host "Public package prepared without bundled mpv; first launch will provision it upstream."

# Build a normal Windows installer for the primary download. The portable ZIP
# remains published because the in-app updater consumes it directly.
$jpackage = $env:STREAMFLIX_JPACKAGE
if (-not $jpackage) {
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { $jpackage = $cmd.Source }
}
if (-not $jpackage -or -not (Test-Path -LiteralPath $jpackage -PathType Leaf)) {
    throw 'jpackage required to create the Windows installer.'
}

$installerStage = Join-Path $PSScriptRoot 'dist\installer'
if (Test-Path -LiteralPath $installerStage) {
    Remove-Item -LiteralPath $installerStage -Recurse -Force
}
New-Item -ItemType Directory -Path $installerStage -Force | Out-Null

Write-Host "Creating Windows installer..."
& $jpackage @(
    '--type','exe',
    '--name','StreamflixDesktop',
    '--app-image',(Join-Path $PSScriptRoot 'dist\StreamflixDesktop'),
    '--dest',$installerStage,
    '--app-version',$version,
    '--vendor','Streamflix Desktop Community Port',
    '--description','Streamflix Desktop for Windows',
    '--license-file',(Join-Path $PSScriptRoot 'LICENSE'),
    '--win-per-user-install',
    '--win-dir-chooser',
    '--win-menu',
    '--win-menu-group','Streamflix',
    '--win-shortcut'
)
if ($LASTEXITCODE -ne 0) { throw "jpackage installer failed with code $LASTEXITCODE" }

$generatedInstallers = @(Get-ChildItem -LiteralPath $installerStage -File -Filter '*.exe')
if ($generatedInstallers.Count -ne 1) {
    throw "Expected exactly one installer EXE, found $($generatedInstallers.Count)"
}
$versionedInstallerName = "StreamflixDesktop-$version-Setup.exe"
$versionedInstallerPath = Join-Path $PSScriptRoot "dist\$versionedInstallerName"
$stableInstallerName = 'StreamflixDesktop-Setup.exe'
$stableInstallerPath = Join-Path $PSScriptRoot "dist\$stableInstallerName"
Copy-Item -LiteralPath $generatedInstallers[0].FullName -Destination $versionedInstallerPath -Force
Copy-Item -LiteralPath $generatedInstallers[0].FullName -Destination $stableInstallerPath -Force
Remove-Item -LiteralPath $installerStage -Recurse -Force

$installerHash = (Get-FileHash -LiteralPath $versionedInstallerPath -Algorithm SHA256).Hash
Set-Content -Path (Join-Path $PSScriptRoot "dist\$versionedInstallerName.sha256") -Value "$installerHash *$versionedInstallerName"
Set-Content -Path (Join-Path $PSScriptRoot "dist\$stableInstallerName.sha256") -Value "$installerHash *$stableInstallerName"
Write-Host "Windows installer verified: dist\$versionedInstallerName"

$zipName = "StreamflixDesktop-$version-windows.zip"
$zipPath = Join-Path $PSScriptRoot "dist\$zipName"

if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Write-Host "Compressing to $zipName..."
Compress-Archive -Path (Join-Path $PSScriptRoot 'dist\StreamflixDesktop') -DestinationPath $zipPath -Force

# Verify the final archive, not only the staging directory.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archiveCheck = [IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    $mpvBinaryEntries = @($archiveCheck.Entries | Where-Object {
        $_.FullName -match '(^|[\\/])mpv\.exe$' -or $_.FullName -match 'tools[\\/]mpv[\\/]'
    })
    $mpvNoticeEntries = @($archiveCheck.Entries | Where-Object {
        $_.FullName -match 'third_party[\\/]mpv[\\/](Copyright|LICENSE\.GPL|LICENSE\.LGPL|SOURCE\.txt)$'
    })
    if ($mpvBinaryEntries.Count -ne 0) {
        throw 'Public ZIP unexpectedly contains the mpv runtime'
    }
    if ($mpvNoticeEntries.Count -ne 4) {
        throw "Public ZIP contains $($mpvNoticeEntries.Count)/4 required mpv notice files"
    }
} finally {
    $archiveCheck.Dispose()
}
Write-Host "Public ZIP layout verified: mpv binary absent, notices present."

Write-Host "Calculating SHA-256..."
$hash = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
$hashLine = "$hash *$zipName"
Set-Content -Path (Join-Path $PSScriptRoot "dist\$zipName.sha256") -Value $hashLine

# Stable asset names keep the README latest-download URL and in-app updater independent of version.
$stableZipName = 'StreamflixDesktop-windows.zip'
$stableZipPath = Join-Path $PSScriptRoot "dist\$stableZipName"
Copy-Item -LiteralPath $zipPath -Destination $stableZipPath -Force
$stableHashLine = "$hash *$stableZipName"
Set-Content -Path (Join-Path $PSScriptRoot "dist\$stableZipName.sha256") -Value $stableHashLine

Write-Host "Release created successfully:"
Write-Host "  Installer:     dist\$versionedInstallerName"
Write-Host "  Stable setup:  dist\$stableInstallerName"
Write-Host "  Installer SHA: $installerHash"
Write-Host "  Versioned ZIP: dist\$zipName"
Write-Host "  Stable ZIP:    dist\$stableZipName"
Write-Host "  ZIP SHA-256:   $hash"
