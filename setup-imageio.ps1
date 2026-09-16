$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$version = "3.12.0"
$target = Join-Path $PSScriptRoot "lib\imageio"
New-Item $target -ItemType Directory -Force | Out-Null

$artifacts = @(
    @{ path="imageio/imageio-webp"; name="imageio-webp" },
    @{ path="imageio/imageio-core"; name="imageio-core" },
    @{ path="imageio/imageio-metadata"; name="imageio-metadata" },
    @{ path="common/common-lang"; name="common-lang" },
    @{ path="common/common-io"; name="common-io" },
    @{ path="common/common-image"; name="common-image" }
)

foreach ($a in $artifacts) {
    $file = "$($a.name)-$version.jar"
    $dest = Join-Path $target $file
    if (Test-Path $dest) { continue }
    $url = "https://repo1.maven.org/maven2/com/twelvemonkeys/$($a.path)/$version/$file"
    Write-Host "Downloading $file..."
    & curl.exe -L --fail --retry 2 -o $dest $url
    if ($LASTEXITCODE -ne 0) { throw "Failed to download $file" }
}

Write-Host "ImageIO WebP dependencies ready: $target"
