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
# pinned upstream runtime on the first playback attempt that needs it, placing it in
# %LOCALAPPDATA% after SHA-256 verification.
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
Write-Host "Public package prepared without bundled mpv; the first playback attempt that needs it will provision it upstream."

# Build a per-user EXE for the primary download and a machine-wide MSI for
# managed/admin deployment. Both installers use the same validated app image.
# The portable ZIP remains published because the in-app updater consumes it.
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

Write-Host "Creating per-user EXE installer..."
$brandIcon = Join-Path $PSScriptRoot 'build\branding\StreamflixDesktop.ico'
if (-not (Test-Path -LiteralPath $brandIcon -PathType Leaf)) {
    throw 'Streamflix application icon missing from validated build output.'
}
$commonInstallerArguments = @(
    '--name','StreamflixDesktop',
    '--app-image',(Join-Path $PSScriptRoot 'dist\StreamflixDesktop'),
    '--icon',$brandIcon,
    '--dest',$installerStage,
    '--app-version',$version,
    '--vendor','Streamflix Desktop Community Port',
    '--description','Streamflix Desktop for Windows',
    '--license-file',(Join-Path $PSScriptRoot 'LICENSE'),
    '--win-dir-chooser',
    '--win-menu',
    '--win-menu-group','Streamflix',
    '--win-shortcut'
)
$exeInstallerArguments = @('--type','exe') + $commonInstallerArguments + @('--win-per-user-install')
& $jpackage @exeInstallerArguments
if ($LASTEXITCODE -ne 0) { throw "jpackage EXE installer failed with code $LASTEXITCODE" }

Write-Host "Creating managed-deployment MSI installer..."
$msiInstallerArguments = @('--type','msi') + $commonInstallerArguments
& $jpackage @msiInstallerArguments
if ($LASTEXITCODE -ne 0) { throw "jpackage MSI installer failed with code $LASTEXITCODE" }

function Publish-InstallerAssets {
    param([ValidateSet('exe','msi')][string]$Extension)

    $generatedInstallers = @(Get-ChildItem -LiteralPath $installerStage -File -Filter "*.$Extension")
    if ($generatedInstallers.Count -ne 1) {
        throw "Expected exactly one installer .$Extension, found $($generatedInstallers.Count)"
    }

    $versionedName = "StreamflixDesktop-$version-Setup.$Extension"
    $versionedPath = Join-Path $PSScriptRoot "dist\$versionedName"
    $stableName = "StreamflixDesktop-Setup.$Extension"
    $stablePath = Join-Path $PSScriptRoot "dist\$stableName"
    Copy-Item -LiteralPath $generatedInstallers[0].FullName -Destination $versionedPath -Force
    Copy-Item -LiteralPath $generatedInstallers[0].FullName -Destination $stablePath -Force

    $hash = (Get-FileHash -LiteralPath $versionedPath -Algorithm SHA256).Hash
    Set-Content -Path (Join-Path $PSScriptRoot "dist\$versionedName.sha256") -Value "$hash *$versionedName"
    Set-Content -Path (Join-Path $PSScriptRoot "dist\$stableName.sha256") -Value "$hash *$stableName"

    [PSCustomObject]@{
        VersionedName = $versionedName
        StableName = $stableName
        Hash = $hash
    }
}

$exeAssets = Publish-InstallerAssets -Extension 'exe'
$msiAssets = Publish-InstallerAssets -Extension 'msi'
Remove-Item -LiteralPath $installerStage -Recurse -Force

Write-Host "Windows installers verified:"
Write-Host "  EXE: dist\$($exeAssets.VersionedName)"
Write-Host "  MSI: dist\$($msiAssets.VersionedName)"

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
Write-Host "  Versioned EXE: dist\$($exeAssets.VersionedName)"
Write-Host "  Stable EXE:    dist\$($exeAssets.StableName)"
Write-Host "  EXE SHA-256:   $($exeAssets.Hash)"
Write-Host "  Versioned MSI: dist\$($msiAssets.VersionedName)"
Write-Host "  Stable MSI:    dist\$($msiAssets.StableName)"
Write-Host "  MSI SHA-256:   $($msiAssets.Hash)"
Write-Host "  Versioned ZIP: dist\$zipName"
Write-Host "  Stable ZIP:    dist\$stableZipName"
Write-Host "  ZIP SHA-256:   $hash"
